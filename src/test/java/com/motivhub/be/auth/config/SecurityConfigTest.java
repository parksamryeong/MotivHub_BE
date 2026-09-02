package com.motivhub.be.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns401WhenCallingProtectedApiWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exchangeEndpointPassesSecurityFilterWithoutAuth() throws Exception {
        // /api/auth/exchange는 POST 전용 컨트롤러가 있어 GET은 405가 나지만,
        // 401(인증필요)이 아니라는 점으로 permitAll 설정이 적용됐는지 검증한다.
        mockMvc.perform(get("/api/auth/exchange"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void apiDocsAreAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
