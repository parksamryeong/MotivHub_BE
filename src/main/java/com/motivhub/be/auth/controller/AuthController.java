package com.motivhub.be.auth.controller;

import com.motivhub.be.auth.dto.ExchangeRequest;
import com.motivhub.be.auth.dto.LoginRequest;
import com.motivhub.be.auth.dto.RefreshRequest;
import com.motivhub.be.auth.dto.SignupCompleteRequest;
import com.motivhub.be.auth.dto.SignupRequestVerificationRequest;
import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.service.AuthService;
import com.motivhub.be.auth.service.SignupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;

    public AuthController(AuthService authService, SignupService signupService) {
        this.authService = authService;
        this.signupService = signupService;
    }

    @PostMapping("/exchange")
    public ResponseEntity<TokenPair> exchange(@Valid @RequestBody ExchangeRequest request) {
        return ResponseEntity.ok(authService.exchange(request.code()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RefreshRequest request) {
        authService.logout(userId, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup/request-verification")
    public ResponseEntity<Void> requestVerification(@Valid @RequestBody SignupRequestVerificationRequest request) {
        signupService.requestVerification(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/signup/complete")
    public ResponseEntity<TokenPair> completeSignup(@Valid @RequestBody SignupCompleteRequest request) {
        return ResponseEntity.ok(
                signupService.completeSignup(request.email(), request.code(), request.password(), request.nickname()));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPair> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }
}
