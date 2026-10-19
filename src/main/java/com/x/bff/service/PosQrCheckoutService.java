package com.x.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.PosQrCheckoutRequest;
import com.x.bff.dto.PosQrCheckoutResponse;
import com.x.bff.dto.PosQrResumeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Base64;

@Service
public class PosQrCheckoutService {
    private static final ParameterizedTypeReference<ApiResponse<JsonNode>> JSON_RESPONSE =
            new ParameterizedTypeReference<>() {};

    /** KHQR bill numbers are limited to 25 characters by the NBC specification. */
    private static final int KHQR_BILL_NUMBER_LIMIT = 25;

    private final WebClient orderClient;
    private final WebClient paymentClient;
    private final WebClient imageClient;
    private final WebClient businessClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public PosQrCheckoutService(
            ServiceClientFactory clientFactory,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this(
                clientFactory.forService("order", "/api/v1/orders"),
                clientFactory.forService("payment", "/api/v1/payments"),
                webClientBuilder.clone().build(),
                clientFactory.forService("business", "/api/v1/businesses"),
                objectMapper);
    }

    PosQrCheckoutService(
            WebClient orderClient,
            WebClient paymentClient,
            WebClient imageClient,
            ObjectMapper objectMapper) {
        this(orderClient, paymentClient, imageClient, null, objectMapper);
    }

    PosQrCheckoutService(
            WebClient orderClient,
            WebClient paymentClient,
            WebClient imageClient,
            WebClient businessClient,
            ObjectMapper objectMapper) {
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
        this.imageClient = imageClient;
        this.businessClient = businessClient;
        this.objectMapper = objectMapper;
    }

    public Mono<PosQrCheckoutResponse> create(PosQrCheckoutRequest request) {
        return create(request, "/qr", null);
    }

    public Mono<PosQrCheckoutResponse> createSimulated(PosQrCheckoutRequest request) {
        return create(request, "/simulated-qr", null);
    }

    /**
     * KHQR paid into the business's own Bakong account. The account is read from the business
     * record on the server, so the browser cannot choose where the money goes.
     */
    public Mono<PosQrCheckoutResponse> createBakong(PosQrCheckoutRequest request) {
        return bakongPayee(request.businessId())
                .flatMap(payee -> create(request, "/bakong-qr", payee));
    }

    /**
     * A fresh KHQR for a POS order that is still waiting for payment, for example after the cashier
     * closed the QR before the customer paid. The order must belong to the business and store given.
     */
    public Mono<PosQrCheckoutResponse> resume(Long orderId, PosQrResumeRequest request, boolean bakong) {
        Mono<ObjectNode> payee = bakong ? bakongPayee(request.businessId()) : Mono.just(objectMapper.createObjectNode());
        return payee.flatMap(extra -> orderClient.get()
                .uri("/{id}", orderId)
                .retrieve()
                .bodyToMono(JSON_RESPONSE)
                .map(this::requireData)
                .flatMap(order -> {
                    if (order.path("businessId").asLong(-1) != request.businessId()
                            || order.path("storeId").asLong(-1) != request.storeId()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found in this store"));
                    }
                    if (!"PENDING_PAYMENT".equals(order.path("orderStatus").asText())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Order " + order.path("orderNo").asText("") + " is no longer waiting for payment"));
                    }
                    return createPayment(request.businessId(), request.storeId(), request.cashierId(),
                            request.paymentIdempotencyKey(), request.paymentNote(), order,
                            bakong ? "/bakong-qr" : "/simulated-qr", bakong ? extra : null)
                            .flatMap(this::embedQrImage)
                            .map(payment -> new PosQrCheckoutResponse(order, payment));
                }));
    }

    private Mono<ObjectNode> bakongPayee(Long businessId) {
        if (businessClient == null) {
            return Mono.error(new IllegalStateException("Business service client is not configured"));
        }
        return businessClient.get()
                .uri("/{id}", businessId)
                .retrieve()
                .bodyToMono(JSON_RESPONSE)
                .map(this::requireData)
                .map(business -> {
                    String account = business.path("bakongAccountId").asText("");
                    if (!StringUtils.hasText(account)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Add your Bakong account ID in POS settings before taking KHQR payments.");
                    }
                    ObjectNode payee = objectMapper.createObjectNode();
                    payee.put("bakongAccountId", account.trim());
                    payee.put("merchantName", requiredText(business, "name"));
                    return payee;
                });
    }

    private Mono<PosQrCheckoutResponse> create(
            PosQrCheckoutRequest request, String paymentPath, ObjectNode extraPaymentFields) {
        ObjectNode orderRequest = objectMapper.createObjectNode();
        orderRequest.put("businessId", request.businessId());
        orderRequest.put("storeId", request.storeId());
        orderRequest.put("customerId", request.customerId());
        orderRequest.put("cashierId", request.cashierId());
        orderRequest.put("currencyCode", request.currencyCode());
        if (request.taxRate() != null) {
            orderRequest.put("taxRate", request.taxRate());
        }
        if (request.roundingIncrement() != null) {
            orderRequest.put("roundingIncrement", request.roundingIncrement());
        }
        orderRequest.put("allowNegativeStock", Boolean.TRUE.equals(request.allowNegativeStock()));
        if (request.loyaltyPointsToRedeem() != null && request.loyaltyPointsToRedeem() > 0) {
            orderRequest.put("loyaltyPointsToRedeem", request.loyaltyPointsToRedeem());
        }
        orderRequest.put("idempotencyKey", request.idempotencyKey());
        orderRequest.set("items", objectMapper.valueToTree(request.items()));

        return orderClient.post()
                .uri("/pos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(orderRequest)
                .retrieve()
                .bodyToMono(JSON_RESPONSE)
                .map(this::requireData)
                .flatMap(order -> createPayment(request.businessId(), request.storeId(), request.cashierId(),
                        request.paymentIdempotencyKey(), request.paymentNote(), order, paymentPath, extraPaymentFields)
                        .flatMap(payment -> embedQrImage(payment)
                                .map(enrichedPayment -> new PosQrCheckoutResponse(order, enrichedPayment))));
    }

    private Mono<JsonNode> createPayment(
            Long businessId, Long storeId, Long cashierId, String paymentIdempotencyKey, String paymentNote,
            JsonNode order, String paymentPath, ObjectNode extraPaymentFields) {
        long orderId = requiredLong(order, "id");
        String currencyCode = requiredText(order, "currencyCode");
        JsonNode grandTotal = order.get("grandTotal");
        if (grandTotal == null || !grandTotal.isNumber()) {
            return Mono.error(new IllegalStateException("Order service did not return grandTotal"));
        }

        ObjectNode paymentRequest = objectMapper.createObjectNode();
        paymentRequest.put("orderId", orderId);
        paymentRequest.put("businessId", businessId);
        paymentRequest.put("storeId", storeId);
        paymentRequest.put("cashierId", cashierId);
        paymentRequest.set("amount", grandTotal);
        paymentRequest.put("currencyCode", currencyCode);
        paymentRequest.put("idempotencyKey", paymentIdempotencyKey);
        if (StringUtils.hasText(paymentNote)) {
            paymentRequest.put("note", paymentNote);
        }
        if (extraPaymentFields != null) {
            paymentRequest.setAll(extraPaymentFields);
            String orderNo = order.path("orderNo").asText("");
            if (StringUtils.hasText(orderNo)) {
                paymentRequest.put("billNumber", orderNo.length() <= KHQR_BILL_NUMBER_LIMIT
                        ? orderNo
                        : orderNo.substring(0, KHQR_BILL_NUMBER_LIMIT));
            }
        }

        return paymentClient.post()
                .uri(paymentPath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paymentRequest)
                .retrieve()
                .bodyToMono(JSON_RESPONSE)
                .map(this::requireData);
    }

    private Mono<JsonNode> embedQrImage(JsonNode payment) {
        String qrImageUrl = requiredText(payment, "qrImageUrl");
        if (qrImageUrl.startsWith("data:image/")) {
            ObjectNode enriched = payment.deepCopy();
            enriched.put("qrImageDataUrl", qrImageUrl);
            return Mono.just(enriched);
        }
        URI uri = URI.create(qrImageUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"khqr.cc".equalsIgnoreCase(uri.getHost())
                || uri.getPath() == null
                || !uri.getPath().startsWith("/api/khqr/")) {
            return Mono.error(new IllegalStateException("KHQRPay returned an unsupported QR image URL"));
        }

        return imageClient.get()
                .uri(uri)
                .accept(MediaType.IMAGE_PNG)
                .retrieve()
                .toEntity(byte[].class)
                .map(response -> {
                    byte[] image = response.getBody();
                    MediaType contentType = response.getHeaders().getContentType();
                    if (image == null || image.length == 0 || contentType == null
                            || !"image".equalsIgnoreCase(contentType.getType())) {
                        throw new IllegalStateException("KHQRPay did not return a valid QR image");
                    }
                    ObjectNode enriched = payment.deepCopy();
                    enriched.put(
                            "qrImageDataUrl",
                            "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image));
                    return enriched;
                });
    }

    private JsonNode requireData(ApiResponse<JsonNode> response) {
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Downstream service returned no data");
        }
        return response.getData();
    }

    private long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalStateException("Order service did not return " + field);
        }
        return value.asLong();
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !StringUtils.hasText(value.asText())) {
            throw new IllegalStateException("Downstream service did not return " + field);
        }
        return value.asText();
    }
}
