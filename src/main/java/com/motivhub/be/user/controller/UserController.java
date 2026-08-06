package com.motivhub.be.user.controller;

import com.motivhub.be.user.dto.MyPageResponse;
import com.motivhub.be.user.dto.NicknameCheckResponse;
import com.motivhub.be.user.dto.NicknameUpdateRequest;
import com.motivhub.be.user.dto.UserProfileResponse;
import com.motivhub.be.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @GetMapping("/me/mypage")
    public ResponseEntity<MyPageResponse> myPage(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getMyPage(userId));
    }

    @GetMapping("/nickname-check")
    public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(new NicknameCheckResponse(userService.isNicknameAvailable(nickname)));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<UserProfileResponse> updateNickname(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody NicknameUpdateRequest request) {
        return ResponseEntity.ok(userService.updateNickname(userId, request.nickname()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
