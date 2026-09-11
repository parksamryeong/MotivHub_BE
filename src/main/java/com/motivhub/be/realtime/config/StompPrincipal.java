package com.motivhub.be.realtime.config;

import java.security.Principal;

public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(Long userId) {
        this.name = String.valueOf(userId);
    }

    @Override
    public String getName() {
        return name;
    }
}
