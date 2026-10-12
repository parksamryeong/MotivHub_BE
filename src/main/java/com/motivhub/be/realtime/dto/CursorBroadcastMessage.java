package com.motivhub.be.realtime.dto;

public record CursorBroadcastMessage(
        Long userId, String nickname, String profileImageUrl, Double x, Double y, boolean visible) {

    public static CursorBroadcastMessage of(Long userId, String nickname, String profileImageUrl,
                                             CursorUpdateMessage update) {
        return new CursorBroadcastMessage(userId, nickname, profileImageUrl, update.x(), update.y(),
                update.visible());
    }
}
