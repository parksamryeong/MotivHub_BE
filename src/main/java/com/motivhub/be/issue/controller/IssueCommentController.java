package com.motivhub.be.issue.controller;

import com.motivhub.be.issue.dto.IssueCommentCreateRequest;
import com.motivhub.be.issue.dto.IssueCommentResponse;
import com.motivhub.be.issue.service.IssueCommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IssueCommentController {

    private final IssueCommentService issueCommentService;

    public IssueCommentController(IssueCommentService issueCommentService) {
        this.issueCommentService = issueCommentService;
    }

    @PostMapping("/api/issues/{issueId}/comments")
    public ResponseEntity<IssueCommentResponse> create(
            @AuthenticationPrincipal Long userId, @PathVariable Long issueId,
            @Valid @RequestBody IssueCommentCreateRequest request) {
        return ResponseEntity.ok(issueCommentService.create(userId, issueId, request.content()));
    }

    @GetMapping("/api/issues/{issueId}/comments")
    public ResponseEntity<List<IssueCommentResponse>> list(@PathVariable Long issueId) {
        return ResponseEntity.ok(issueCommentService.list(issueId));
    }
}
