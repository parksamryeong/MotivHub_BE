package com.motivhub.be.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.global.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

/**
 * Slice test (no Docker/DB/Redis required) proving that the explicit {@code ObjectMapper} bean
 * defined in {@code com.motivhub.be.global.config.JacksonConfig} is discoverable by Spring and
 * can serialize {@link ErrorResponse} (which has a {@code LocalDateTime timestamp} field) now
 * that jackson-datatype-jsr310 is on the classpath (see build.gradle). Before both fixes, this
 * would either fail to find an ObjectMapper bean at all (no Spring Boot autoconfiguration
 * provides a classic com.fasterxml.jackson.databind.ObjectMapper bean in this Boot 4.1 setup) or
 * throw com.fasterxml.jackson.databind.exc.InvalidDefinitionException on LocalDateTime.
 */
@JsonTest
@Import(JacksonConfig.class)
class ErrorResponseJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesErrorResponseWithLocalDateTimeWithoutThrowing() {
        ErrorResponse response = ErrorResponse.of("TOKEN_EXPIRED", "토큰이 만료되었습니다.");

        assertThatCode(() -> objectMapper.writeValueAsString(response)).doesNotThrowAnyException();
    }

    @Test
    void serializedJsonContainsIsoFormattedTimestampAndRoundTrips() throws Exception {
        ErrorResponse response = ErrorResponse.of("UNAUTHORIZED", "인증이 필요합니다.");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"code\":\"UNAUTHORIZED\"");
        // ":\"" (colon immediately followed by an opening quote) is only true for a string
        // value; it would be false if WRITE_DATES_AS_TIMESTAMPS produced an array like
        // "timestamp":[2026,8,7,...] instead of an ISO-8601 string.
        assertThat(json).contains("\"timestamp\":\"");

        ErrorResponse roundTripped = objectMapper.readValue(json, ErrorResponse.class);
        assertThat(roundTripped.code()).isEqualTo("UNAUTHORIZED");
        assertThat(roundTripped.message()).isEqualTo("인증이 필요합니다.");
        assertThat(roundTripped.timestamp()).isEqualTo(response.timestamp());
    }
}
