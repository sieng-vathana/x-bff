package com.x.bff.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.bff.dto.StoreResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoreResponseContractTest {

    @Test
    void decodesThePagedStoreServiceResponse() throws Exception {
        String responseBody = """
                {"status":1,"code":200,"data":{"content":[{"id":12,"businessId":12,"name":"Shopify","code":"MAIN","images":[{"id":1,"imageUrl":"/api/v1/files/1/content","isPrimary":true,"sortOrder":0}]}],"page":0,"size":100,"totalElements":1,"totalPages":1,"hasNext":false}}
                """;

        ApiResponse<PageResponse<StoreResponse>> response = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .readValue(responseBody, new TypeReference<>() {});

        assertEquals(12L, response.getData().content().get(0).id());
        assertEquals(1L, response.getData().content().get(0).images().get(0).id());
        assertEquals(100, response.getData().size());
    }
}
