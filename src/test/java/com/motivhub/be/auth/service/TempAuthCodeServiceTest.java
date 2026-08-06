package com.motivhub.be.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TempAuthCodeServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TempAuthCodeService tempAuthCodeService;

    @Test
    void consumesIssuedCodeOnlyOnce() {
        TokenPair tokens = new TokenPair("access-1", "refresh-1");

        String code = tempAuthCodeService.issue(tokens);

        assertThat(tempAuthCodeService.consume(code)).contains(tokens);
        assertThat(tempAuthCodeService.consume(code)).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownCode() {
        assertThat(tempAuthCodeService.consume("no-such-code")).isEmpty();
    }
}
