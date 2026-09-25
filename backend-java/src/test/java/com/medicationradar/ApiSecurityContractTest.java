package com.medicationradar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/database_that_must_not_be_created",
        "spring.datasource.username=invalid_user",
        "spring.datasource.password=invalid_password",
        "app.security.jwt-secret=12345678901234567890123456789012"
})
@AutoConfigureMockMvc
class ApiSecurityContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointRemainsPublicAndCompatible() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.model").isString())
                .andExpect(jsonPath("$.keyConfigured").isBoolean());
    }

    @Test
    void protectedEndpointUsesLegacyErrorShape() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("请先登录"));
    }
}
