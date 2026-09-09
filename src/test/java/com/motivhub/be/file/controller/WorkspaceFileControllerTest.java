package com.motivhub.be.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.file.dto.FilePresignRequest;
import com.motivhub.be.file.dto.WorkspaceFileCategoryUpdateRequest;
import com.motivhub.be.file.dto.WorkspaceFileConfirmRequest;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WorkspaceFileControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "file-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void presignReturnsUploadUrlAndFileKey() throws Exception {
        User owner = newUser("f1-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 API 워크스페이스1");

        String response = mockMvc.perform(post("/api/workspaces/{workspaceId}/files/presign", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FilePresignRequest("report.pdf", "application/pdf", 1_000L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String fileKey = objectMapper.readTree(response).get("fileKey").asText();
        assertThat(fileKey).startsWith("workspaces/" + workspace.id() + "/files/");
    }

    @Test
    void confirmAndListReturnUploadedFile() throws Exception {
        User owner = newUser("f2-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 API 워크스페이스2");
        String presignResponse = mockMvc.perform(post("/api/workspaces/{workspaceId}/files/presign", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FilePresignRequest("notes.txt", "text/plain", 5L))))
                .andReturn().getResponse().getContentAsString();
        JsonNode presignJson = objectMapper.readTree(presignResponse);
        String uploadUrl = presignJson.get("uploadUrl").asText();
        String fileKey = presignJson.get("fileKey").asText();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofString("hello"))
                .build();
        client.send(putRequest, HttpResponse.BodyHandlers.discarding());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/files", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkspaceFileConfirmRequest(fileKey, "notes.txt", 5L, "text/plain", "문서"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("notes.txt"))
                .andExpect(jsonPath("$.category").value("문서"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/files", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("notes.txt"))
                .andExpect(jsonPath("$[0].category").value("문서"))
                .andExpect(jsonPath("$[0].uploadedBy.nickname").value(owner.getNickname()));
    }

    @Test
    void memberUpdatesFileCategory() throws Exception {
        User owner = newUser("f6-owner");
        User member = newUser("f6-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "카테고리 API 워크스페이스");
        joinAsMember(workspace.id(), member);
        String presignResponse = mockMvc.perform(post("/api/workspaces/{workspaceId}/files/presign", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FilePresignRequest("cat.txt", "text/plain", 5L))))
                .andReturn().getResponse().getContentAsString();
        JsonNode presignJson = objectMapper.readTree(presignResponse);
        String uploadUrl = presignJson.get("uploadUrl").asText();
        String fileKey = presignJson.get("fileKey").asText();
        HttpClient client = HttpClient.newHttpClient();
        client.send(HttpRequest.newBuilder().uri(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofString("hello")).build(), HttpResponse.BodyHandlers.discarding());
        String confirmResponse = mockMvc.perform(post("/api/workspaces/{workspaceId}/files", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkspaceFileConfirmRequest(fileKey, "cat.txt", 5L, "text/plain", null))))
                .andReturn().getResponse().getContentAsString();
        Long fileId = objectMapper.readTree(confirmResponse).get("id").asLong();

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/files/{fileId}", workspace.id(), fileId)
                        .header("Authorization", "Bearer " + tokenFor(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceFileCategoryUpdateRequest("영수증"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("영수증"));
    }

    @Test
    void updatingCategoryOfUnknownFileReturns404() throws Exception {
        User owner = newUser("f7-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "카테고리 오류 API 워크스페이스");

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/files/{fileId}", workspace.id(), 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceFileCategoryUpdateRequest("분류"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_FILE_NOT_FOUND"));
    }

    @Test
    void downloadsAndDeletesFile() throws Exception {
        User owner = newUser("f3-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 API 워크스페이스3");
        String presignResponse = mockMvc.perform(post("/api/workspaces/{workspaceId}/files/presign", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FilePresignRequest("dl.txt", "text/plain", 5L))))
                .andReturn().getResponse().getContentAsString();
        JsonNode presignJson = objectMapper.readTree(presignResponse);
        String uploadUrl = presignJson.get("uploadUrl").asText();
        String fileKey = presignJson.get("fileKey").asText();
        HttpClient client = HttpClient.newHttpClient();
        client.send(HttpRequest.newBuilder().uri(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofString("hello")).build(), HttpResponse.BodyHandlers.discarding());
        String confirmResponse = mockMvc.perform(post("/api/workspaces/{workspaceId}/files", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WorkspaceFileConfirmRequest(fileKey, "dl.txt", 5L, "text/plain", null))))
                .andReturn().getResponse().getContentAsString();
        Long fileId = objectMapper.readTree(confirmResponse).get("id").asLong();

        mockMvc.perform(get("/api/workspaces/{workspaceId}/files/{fileId}/download", workspace.id(), fileId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());

        mockMvc.perform(delete("/api/workspaces/{workspaceId}/files/{fileId}", workspace.id(), fileId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonMemberCannotPresignReturns403WithCorrectErrorCode() throws Exception {
        User owner = newUser("f4-owner");
        User outsider = newUser("f4-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 권한 API 워크스페이스");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/files/presign", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FilePresignRequest("secret.pdf", "application/pdf", 1_000L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
    }

    @Test
    void presigningBlockedExtensionReturns400WithCorrectErrorCode() throws Exception {
        User owner = newUser("f5-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 확장자 API 워크스페이스");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/files/presign", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FilePresignRequest("virus.exe", "application/octet-stream", 1_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOCKED_FILE_EXTENSION"));
    }
}
