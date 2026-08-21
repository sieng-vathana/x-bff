package com.x.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sharedlib.response.ApiResponse;
import com.x.bff.dto.PosQrCheckoutRequest;
import com.x.bff.dto.PosQrCheckoutResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Base64;

@Service
public class PosQrCheckoutService {
    private static final ParameterizedTypeReference<ApiResponse<JsonNode>> JSON_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final WebClient orderClient;
    private final WebClient paymentClient;
    private final WebClient imageClient;
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
                objectMapper);
    }

    PosQrCheckoutService(
            WebClient orderClient,
            WebClient paymentClient,
            WebClient imageClient,
            ObjectMapper objectMapper) {
        this.orderClient = orderClient;
        this.paymentClient = paymentClient;
        this.imageClient = imageClient;
        this.objectMapper = objectMapper;
    }

    public Mono<PosQrCheckoutResponse> create(PosQrCheckoutRequest request) {
        return create(request, "/qr");
    }

    public Mono<PosQrCheckoutResponse> createSimulated(PosQrCheckoutRequest request) {
        return create(request, "/simulated-qr");
    }

    private Mono<PosQrCheckoutResponse> create(PosQrCheckoutRequest request, String paymentPath) {
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
        orderRequest.put("idempotencyKey", request.idempotencyKey());
        orderRequest.set("items", objectMapper.valueToTree(request.items()));

        return orderClient.post()
                .uri("/pos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(orderRequest)
                .retrieve()
                .bodyToMono(JSON_RESPONSE)
                .map(this::requireData)
                .flatMap(order -> createPayment(request, order, paymentPath)
                        .flatMap(payment -> embedQrImage(payment)
                                .map(enrichedPayment -> new PosQrCheckoutResponse(order, enrichedPayment))));
    }

    private Mono<JsonNode> createPayment(PosQrCheckoutRequest request, JsonNode order, String paymentPath) {
        long orderId = requiredLong(order, "id");
        String currencyCode = requiredText(order, "currencyCode");
        JsonNode grandTotal = order.get("grandTotal");
        if (grandTotal == null || !grandTotal.isNumber()) {
            return Mono.error(new IllegalStateException("Order service did not return grandTotal"));
        }

        ObjectNode paymentRequest = objectMapper.createObjectNode();
        paymentRequest.put("orderId", orderId);
        paymentRequest.put("businessId", request.businessId());
        paymentRequest.put("storeId", request.storeId());
        paymentRequest.put("cashierId", request.cashierId());
        paymentRequest.set("amount", grandTotal);
        paymentRequest.put("currencyCode", currencyCode);
        paymentRequest.put("idempotencyKey", request.paymentIdempotencyKey());
        if (StringUtils.hasText(request.paymentNote())) {
            paymentRequest.put("note", request.paymentNote());
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
