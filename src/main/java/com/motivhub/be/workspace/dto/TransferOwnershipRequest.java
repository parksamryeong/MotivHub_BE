package com.motivhub.be.workspace.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(@NotNull Long newOwnerUserId) {
}
