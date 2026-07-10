package io.raindrops.storage;

import io.raindrops.core.Drop;
import io.raindrops.core.DropSerializer;
import io.raindrops.core.ShamirSSS;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StorageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final SecureRandom RNG = new SecureRandom();

    @Test
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.nodeId", notNullValue()))
                .andExpect(jsonPath("$.dropsStored", notNullValue()));
    }

    @Test
    void storeAndRetrieveDrop() throws Exception {
        byte[] masterKey = new byte[32];
        RNG.nextBytes(masterKey);

        java.time.Instant now = java.time.Instant.now();
        BigInteger secret = ShamirSSS.bytesToSecret("Hello Rain Drops!".getBytes());
        List<BigInteger[]> shares = ShamirSSS.split(secret, 3, 2);

        Drop drop = io.raindrops.core.DropFactory.create(
            shares.get(0)[0].intValueExact(),
            shares.get(0)[1],
            masterKey, 365);

        String dropJson = DropSerializer.toJson(drop);
        String storeResp = mockMvc.perform(post("/drops")
                .contentType(MediaType.TEXT_PLAIN)
                .content(dropJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dropId", notNullValue()))
                .andExpect(jsonPath("$.nodeId", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String storeBody = storeResp;
        // Extract dropId from JSON response {"dropId":"...","nodeId":"..."}
        String dropId = storeBody.replaceAll(".*\"dropId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/drops/" + dropId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.x", notNullValue()))
                .andExpect(jsonPath("$.y", notNullValue()));
    }

    @Test
    void dropNotFound() throws Exception {
        mockMvc.perform(get("/drops/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void peersEndpoint() throws Exception {
        mockMvc.perform(get("/peers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId", notNullValue()))
                .andExpect(jsonPath("$.peers", notNullValue()));
    }
}