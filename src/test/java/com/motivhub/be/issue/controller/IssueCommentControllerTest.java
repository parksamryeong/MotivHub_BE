package com.motivhub.be.issue.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.issue.dto.IssueCommentCreateRequest;
import com.motivhub.be.issue.dto.IssueCreateRequest;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class IssueCommentControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "issue-comment-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void createsAndListsComment() throws Exception {
        User author = newUser("ic1-author");
        User commenter = newUser("ic1-commenter");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 댓글 API 워크스페이스1");
        String createResponse = mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "제목", "설명", null))))
                .andReturn().getResponse().getContentAsString();
        Long issueId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(post("/api/issues/{issueId}/comments", issueId)
                        .header("Authorization", "Bearer " + tokenFor(commenter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueCommentCreateRequest("도움됐어요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("도움됐어요"))
                .andExpect(jsonPath("$.author.nickname").value(commenter.getNickname()));

        mockMvc.perform(get("/api/issues/{issueId}/comments", issueId)
                        .header("Authorization", "Bearer " + tokenFor(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("도움됐어요"));
    }

    @Test
    void commentingOnUnknownIssueReturns404() throws Exception {
        User user = newUser("ic2-user");

        mockMvc.perform(post("/api/issues/{issueId}/comments", 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IssueCommentCreateRequest("내용"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    }

    @Test
    void listingCommentsOnUnknownIssueReturns404() throws Exception {
        User user = newUser("ic3-user");

        mockMvc.perform(get("/api/issues/{issueId}/comments", 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    }
}
