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

    // 톰캣이 SockJS 스트리밍 연결 중 예외가 나면 에러 페이지를 그리려고 /error로 재디스패치한다.
    // 이 경로가 permitAll이 아니면 인증 없는 재디스패치 자체가 거부되면서(AuthorizationDeniedException)
    // 일시적 끊김이 복구 불가능한 연결 종료로 바뀐다 - /error가 401/403으로 막히지 않는지만 확인한다
    // (실제 에러 응답 바디/상태 코드는 BasicErrorController의 몫이라 여기서는 검증하지 않는다).
    @Test
    void errorEndpointIsNotBlockedByAuthenticationWithoutToken() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError(
                                "/error가 인증 필터에 막혔습니다 (status=" + status + ") - PUBLIC_ENDPOINTS 확인 필요");
                    }
                });
    }
}
