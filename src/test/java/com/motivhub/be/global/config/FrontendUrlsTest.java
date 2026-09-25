package com.motivhub.be.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrontendUrlsTest {

    @Test
    void allOriginsSplitsCommaSeparatedValues() {
        assertThat(FrontendUrls.allOrigins("https://motivhub.cloud,https://motiv-hub-fe.vercel.app"))
                .containsExactly("https://motivhub.cloud", "https://motiv-hub-fe.vercel.app");
    }

    @Test
    void allOriginsTrimsWhitespaceAndIgnoresEmptySegments() {
        assertThat(FrontendUrls.allOrigins(" https://motivhub.cloud , , https://motiv-hub-fe.vercel.app "))
                .containsExactly("https://motivhub.cloud", "https://motiv-hub-fe.vercel.app");
    }

    @Test
    void allOriginsReturnsSingleEntryForSingleOrigin() {
        assertThat(FrontendUrls.allOrigins("http://localhost:3000")).containsExactly("http://localhost:3000");
    }

    @Test
    void primaryReturnsFirstOriginWhenMultipleArePresent() {
        assertThat(FrontendUrls.primary("https://motivhub.cloud,https://motiv-hub-fe.vercel.app"))
                .isEqualTo("https://motivhub.cloud");
    }

    @Test
    void primaryReturnsTheOnlyOriginWhenSingle() {
        assertThat(FrontendUrls.primary("http://localhost:3000")).isEqualTo("http://localhost:3000");
    }
}
