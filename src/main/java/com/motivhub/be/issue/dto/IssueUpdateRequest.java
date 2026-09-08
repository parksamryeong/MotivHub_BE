package com.motivhub.be.issue.dto;

import jakarta.validation.constraints.Size;

public record IssueUpdateRequest(
        @Size(max = 100) String title, @Size(max = 2000) String problemDescription,
        @Size(max = 2000) String solution) {
}
