package com.motivhub.be.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,72}$",
                message = "비밀번호는 영문과 숫자를 포함한 8~72자의 영문/숫자/특수문자여야 합니다.")
        String newPassword) {
}
