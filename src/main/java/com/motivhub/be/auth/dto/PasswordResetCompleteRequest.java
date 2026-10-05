package com.motivhub.be.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetCompleteRequest(
        @Email @NotBlank String email,
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "재설정 코드는 6자리 숫자여야 합니다.")
        String code,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,72}$",
                message = "비밀번호는 영문과 숫자를 포함한 8~72자의 영문/숫자/특수문자여야 합니다.")
        String newPassword) {
}
