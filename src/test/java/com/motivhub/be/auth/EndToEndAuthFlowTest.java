package com.motivhub.be.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.dto.ExchangeRequest;
import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.handler.OAuth2SuccessHandler;
import com.motivhub.be.auth.oauth.CustomOAuth2User;
import com.motivhub.be.auth.oauth.CustomOAuth2UserService;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class EndToEndAuthFlowTest extends AbstractIntegrationTest {

    @Autowired private CustomOAuth2UserService customOAuth2UserService;
    @Autowired private OAuth2SuccessHandler oAuth2SuccessHandler;
    @Autowired private UserRepository userRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void worksEndToEndFromFirstGithubLoginToProtectedApiCall() throws Exception {
        Map<String, Object> githubAttributes = Map.of(
                "id", 987654, "email", "e2e@github.com", "avatar_url", "http://img/e2e.png");

        User user = customOAuth2UserService.resolveUser("github", githubAttributes);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomOAuth2User(user.getId(), githubAttributes), null);

        MockHttpServletResponse redirectResponse = new MockHttpServletResponse();
        oAuth2SuccessHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(), redirectResponse, authentication);

        String redirectUrl = redirectResponse.getRedirectedUrl();
        assertThat(redirectUrl).startsWith("http://localhost:3000/oauth/callback?code=");
        String code = extractCode(redirectUrl);

        String exchangeResponseBody = mockMvc.perform(post("/api/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExchangeRequest(code))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        TokenPair tokens = objectMapper.readValue(exchangeResponseBody, TokenPair.class);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("e2e@github.com"))
                .andExpect(jsonPath("$.nicknameConfigured").value(false));

        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    private String extractCode(String redirectUrl) {
        Matcher matcher = Pattern.compile("code=(.+)$").matcher(redirectUrl);
        if (!matcher.find()) {
            throw new IllegalStateException("리다이렉트 URL에서 code를 찾을 수 없습니다: " + redirectUrl);
        }
        return matcher.group(1);
    }
}
