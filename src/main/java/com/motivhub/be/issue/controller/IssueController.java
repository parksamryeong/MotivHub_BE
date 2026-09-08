package com.motivhub.be.issue.controller;

import com.motivhub.be.issue.dto.IssueCreateRequest;
import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.issue.dto.IssueUpdateRequest;
import com.motivhub.be.issue.service.IssueService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping("/api/issues")
    public ResponseEntity<IssueResponse> create(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody IssueCreateRequest request) {
        return ResponseEntity.ok(issueService.create(
                userId, request.workspaceId(), request.title(), request.problemDescription(), request.solution()));
    }

    @GetMapping("/api/issues")
    public ResponseEntity<List<IssueResponse>> list() {
        return ResponseEntity.ok(issueService.list());
    }

    @GetMapping("/api/issues/{id}")
    public ResponseEntity<IssueResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.getDetail(id));
    }

    @PatchMapping("/api/issues/{id}")
    public ResponseEntity<IssueResponse> update(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody IssueUpdateRequest request) {
        return ResponseEntity.ok(issueService.update(
                userId, id, request.title(), request.problemDescription(), request.solution()));
    }

    @DeleteMapping("/api/issues/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        issueService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
