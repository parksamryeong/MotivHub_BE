package com.motivhub.be.task.dto;

import jakarta.validation.constraints.Size;

public record TaskChecklistItemUpdateRequest(@Size(min = 1, max = 200) String content, Boolean isDone) {
}
