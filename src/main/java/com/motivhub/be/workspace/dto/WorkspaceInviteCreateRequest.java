package com.motivhub.be.workspace.dto;

import jakarta.validation.constraints.Email;

public record WorkspaceInviteCreateRequest(@Email String email) {
}
