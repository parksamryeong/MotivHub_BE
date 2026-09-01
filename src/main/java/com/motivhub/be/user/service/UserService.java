package com.motivhub.be.user.service;

import com.motivhub.be.auth.service.RefreshTokenService;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.domain.UserStatus;
import com.motivhub.be.user.dto.MyPageResponse;
import com.motivhub.be.user.dto.UserProfileResponse;
import com.motivhub.be.user.exception.InvalidNicknameException;
import com.motivhub.be.user.exception.NicknameDuplicateException;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final NicknameValidator nicknameValidator;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, NicknameValidator nicknameValidator,
                        RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.nicknameValidator = nicknameValidator;
        this.refreshTokenService = refreshTokenService;
    }

    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(getUser(userId));
    }

    public MyPageResponse getMyPage(Long userId) {
        return MyPageResponse.from(getUser(userId));
    }

    public boolean isNicknameAvailable(String nickname) {
        return nicknameValidator.isValidFormat(nickname) && !userRepository.existsByNickname(nickname);
    }

    @Transactional
    public UserProfileResponse updateNickname(Long userId, String nickname) {
        if (!nicknameValidator.isValidFormat(nickname)) {
            throw new InvalidNicknameException("닉네임 형식이 올바르지 않습니다.");
        }
        User user = getUser(userId);
        if (!nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
            throw new NicknameDuplicateException("이미 사용중인 닉네임입니다.");
        }
        user.updateNickname(nickname);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        user.withdraw();
        refreshTokenService.deleteAll(userId);
    }

    private User getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new UserNotFoundException("유저를 찾을 수 없습니다.");
        }
        return user;
    }
}
