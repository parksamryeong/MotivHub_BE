package com.motivhub.be.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknameValidatorTest {

    private final NicknameValidator validator = new NicknameValidator();

    @Test
    void validForKoreanAlphanumericCombo() {
        assertThat(validator.isValidFormat("가a1개발자")).isTrue();
    }

    @Test
    void invalidWhenShorterThanTwoChars() {
        assertThat(validator.isValidFormat("a")).isFalse();
    }

    @Test
    void invalidWhenLongerThanFifteenChars() {
        assertThat(validator.isValidFormat("a".repeat(16))).isFalse();
    }

    @Test
    void invalidWhenContainingSpecialCharacters() {
        assertThat(validator.isValidFormat("닉네임!")).isFalse();
    }

    @Test
    void invalidWhenNullOrBlank() {
        assertThat(validator.isValidFormat(null)).isFalse();
        assertThat(validator.isValidFormat("   ")).isFalse();
    }

    @Test
    void validWhenExactlyTwoChars() {
        assertThat(validator.isValidFormat("ab")).isTrue();
    }

    @Test
    void validWhenExactlyFifteenChars() {
        assertThat(validator.isValidFormat("a".repeat(15))).isTrue();
    }
}
