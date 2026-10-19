package com.x.bff.controller;

import com.sun.net.httpserver.HttpServer;
import com.x.bff.service.ServiceClientFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileControllerTest {

    private HttpServer storage;

    @AfterEach
    void stop() {
        if (storage != null) {
            storage.stop(0);
        }
    }

    @Test
    void imageBytesFromStorageReachTheBrowser() throws Exception {
        byte[] image = new byte[150_000];
        Arrays.fill(image, (byte) 7);
        storage = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        storage.createContext("/api/v1/files/4/content", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, image.length);
            exchange.getResponseBody().write(image);
            exchange.close();
        });
        storage.start();
        ServiceClientFactory clients = mock(ServiceClientFactory.class);
        when(clients.forService("storage", "/api/v1/files"))
                .thenReturn(WebClient.create("http://127.0.0.1:" + storage.getAddress().getPort() + "/api/v1/files"));

        ResponseEntity<reactor.core.publisher.Flux<org.springframework.core.io.buffer.DataBuffer>> response =
                new FileController(clients).content(4L).block();

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getContentType()).hasToString("image/jpeg");
        byte[] body = DataBufferUtils.join(response.getBody())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .block();
        assertThat(body).hasSize(image.length).isEqualTo(image);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("max-age=86400, private");
    }
}
