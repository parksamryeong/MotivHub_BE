package com.motivhub.be.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RandomNicknameGeneratorTest {

    private final NicknameValidator validator = new NicknameValidator();
    private final RandomNicknameGenerator generator = new RandomNicknameGenerator();

    @Test
    void generatedNicknameStartsWithUserPrefixAndIsValidFormat() {
        String nickname = generator.generate();

        assertThat(nickname).startsWith("user");
        assertThat(validator.isValidFormat(nickname)).isTrue();
    }

    @Test
    void generatesDifferentNicknameEachTime() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
