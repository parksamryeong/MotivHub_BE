package com.motivhub.be.issue.dto;

import jakarta.validation.constraints.Size;

public record IssueUpdateRequest(
        @Size(min = 1, max = 100) String title,
        @Size(min = 1, max = 2000) String problemDescription,
        @Size(max = 2000) String solution) {
}
