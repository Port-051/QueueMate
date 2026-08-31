package com.queuemate.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** contracts/openapi.yaml의 auth 스키마와 1:1로 대응한다. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank @Size(min = 2, max = 16) String nickname
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
        public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
        }
    }
}
