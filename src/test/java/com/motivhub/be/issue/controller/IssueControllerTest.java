package com.motivhub.be.issue.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.issue.dto.IssueCreateRequest;
import com.motivhub.be.issue.dto.IssueUpdateRequest;
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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class IssueControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "issue-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void createsAndListsIssue() throws Exception {
        User author = newUser("c1-author");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 API 워크스페이스1");

        mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "제목", "문제 설명", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("이슈 API 워크스페이스1"))
                .andExpect(jsonPath("$.solution").doesNotExist());

        mockMvc.perform(get("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("제목"));
    }

    @Test
    void issueVisibleToUserOutsideWorkspace() throws Exception {
        User author = newUser("c2-author");
        User outsider = newUser("c2-outsider");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 API 워크스페이스2");
        String createResponse = mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "공개 이슈", "설명", null))))
                .andReturn().getResponse().getContentAsString();
        Long issueId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/issues/{id}", issueId)
                        .header("Authorization", "Bearer " + tokenFor(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("공개 이슈"));
    }

    @Test
    void nonMemberCannotCreateIssueReturns403() throws Exception {
        User owner = newUser("c3-owner");
        User outsider = newUser("c3-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이슈 API 워크스페이스3");

        mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "제목", "설명", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
    }

    @Test
    void gettingUnknownIssueReturns404() throws Exception {
        User user = newUser("c4-user");

        mockMvc.perform(get("/api/issues/{id}", 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    }

    @Test
    void nonAuthorCannotUpdateIssueReturns403() throws Exception {
        User author = newUser("c5-author");
        User bystander = newUser("c5-bystander");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 수정 API 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "제목", "설명", null))))
                .andReturn().getResponse().getContentAsString();
        Long issueId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/issues/{id}", issueId)
                        .header("Authorization", "Bearer " + tokenFor(bystander))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueUpdateRequest("해킹", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ISSUE_FORBIDDEN"));
    }

    @Test
    void updatingWithBlankTitleReturns400() throws Exception {
        User author = newUser("c7-author");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 수정 검증 API 워크스페이스1");
        String createResponse = mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "제목", "설명", null))))
                .andReturn().getResponse().getContentAsString();
        Long issueId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/issues/{id}", issueId)
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueUpdateRequest("", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingWithBlankProblemDescriptionReturns400() throws Exception {
        User author = newUser("c8-author");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 수정 검증 API 워크스페이스2");
        String createResponse = mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "제목", "설명", null))))
                .andReturn().getResponse().getContentAsString();
        Long issueId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/issues/{id}", issueId)
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueUpdateRequest(null, "", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorDeletesOwnIssue() throws Exception {
        User author = newUser("c6-author");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 삭제 API 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "제목", "설명", null))))
                .andReturn().getResponse().getContentAsString();
        Long issueId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/issues/{id}", issueId)
                        .header("Authorization", "Bearer " + tokenFor(author)))
                .andExpect(status().isNoContent());
    }

    @Test
    void searchByQueryParamFiltersToMatchingTitle() throws Exception {
        // 이 테스트는 검색 결과를 보려고 트랜잭션을 강제 커밋한다(아래 TestTransaction 참고). 로컬
        // newUser()는 고정 suffix("c7-author")를 쓰는데, 롤백되는 다른 테스트(예:
        // updatingWithBlankTitleReturns400)도 같은 suffix를 쓴다 - 평소엔 롤백되니 안전하지만,
        // 이 테스트만 실제로 커밋해버리면 그 유저가 공유 Testcontainers MySQL에 영구히 남아 나중에
        // 같은 suffix로 유저를 만들려는 테스트가 유니크 제약 위반으로 깨진다. createUniqueUser()
        // (AbstractIntegrationTest, 전역 카운터로 접미사를 유일하게 만듦)로 이 충돌을 구조적으로
        // 막는다.
        User author = createUniqueUser("c7-author");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 검색 API 워크스페이스");
        mockMvc.perform(post("/api/issues")
                        .header("Authorization", "Bearer " + tokenFor(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueCreateRequest(workspace.id(), "검색용 특이한 제목", "설명", null))))
                .andExpect(status().isOk());
        // InnoDB FULLTEXT 인덱스는 같은 트랜잭션 안에서도 커밋 전까지는 검색 결과에 반영되지 않는다
        // (일반 B-Tree 인덱스와 다른 특성). 이 테스트는 @Transactional로 롤백되므로, 검색 대상 이슈를
        // 만든 뒤 강제로 커밋하고 새 트랜잭션을 시작해야 GET 검색이 그 데이터를 볼 수 있다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        try {
            mockMvc.perform(get("/api/issues").queryParam("q", "특이한")
                            .header("Authorization", "Bearer " + tokenFor(author)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].title").value("검색용 특이한 제목"));
        } finally {
            // 공유 Testcontainers MySQL에 커밋된 데이터가 영구히 남아 workspace 필터링 없이 전체 이슈
            // 목록 개수를 비교하는 다른 테스트(예: createsAndListsIssue)를 오염시키지 않도록, 워크스페이스를
            // 소프트 삭제해서 이후 모든 조회에서 제외시키고 그 삭제도 다시 강제 커밋한다.
            workspaceService.delete(author.getId(), workspace.id());
            TestTransaction.flagForCommit();
            TestTransaction.end();
            TestTransaction.start();
        }
    }
}
