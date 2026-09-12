package com.motivhub.be.realtime.dto;

import com.motivhub.be.user.dto.UserSummary;
import java.util.List;

public record TaskPresenceMessage(Long taskId, List<UserSummary> viewers) {
}
