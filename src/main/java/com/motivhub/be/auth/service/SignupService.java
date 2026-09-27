package com.motivhub.be.auth.service;

import com.motivhub.be.auth.domain.EmailVerificationToken;
import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.exception.EmailAlreadyRegisteredException;
import com.motivhub.be.auth.exception.InvalidVerificationTokenException;
import com.motivhub.be.auth.exception.TooManyVerificationAttemptsException;
import com.motivhub.be.auth.exception.TooManyVerificationRequestsException;
import com.motivhub.be.auth.exception.VerificationCodeMismatchException;
import com.motivhub.be.auth.exception.VerificationTokenExpiredException;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.auth.repository.EmailVerificationTokenRepository;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.InvalidNicknameException;
import com.motivhub.be.user.exception.NicknameDuplicateException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.user.service.NicknameValidator;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SignupService {

    private static final long EXPIRE_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final EmailVerificationMailService emailVerificationMailService;
    private final NicknameValidator nicknameValidator;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final VerificationAttemptLimiter verificationAttemptLimiter;

    public SignupService(EmailVerificationTokenRepository emailVerificationTokenRepository,
                          UserRepository userRepository,
                          EmailVerificationMailService emailVerificationMailService,
                          NicknameValidator nicknameValidator,
                          PasswordEncoder passwordEncoder,
                          JwtProvider jwtProvider,
                          RefreshTokenService refreshTokenService,
                          VerificationAttemptLimiter verificationAttemptLimiter) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.userRepository = userRepository;
        this.emailVerificationMailService = emailVerificationMailService;
        this.nicknameValidator = nicknameValidator;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.verificationAttemptLimiter = verificationAttemptLimiter;
    }

    @Transactional
    public void requestVerification(String rawEmail) {
        String email = normalize(rawEmail);
        if (userRepository.findByProviderAndProviderId(SocialProvider.EMAIL, email).isPresent()) {
            throw new EmailAlreadyRegisteredException("이미 가입된 이메일입니다.");
        }
        Optional<EmailVerificationToken> existingToken = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email);
        // 이미 발급된 코드가 있다면 그 토큰에 저장된 정규 표기를 쿨다운뿐 아니라 새로 만드는
        // 토큰 row와 발송 대상 이메일까지 그대로 이어간다 - 쿨다운에만 쓰고 새 row는 이번 요청의
        // 원본 문자열로 저장해버리면, completeSignup이 verificationToken.getEmail()로 시도
        // 횟수를 세더라도 재발급 때마다 "정규 표기" 자체가 공격자가 이번에 보낸 변형(대소문자/
        // 악센트/전각문자)으로 계속 바뀌어서 매번 새 Redis 키를 가리키게 되고, 결국 재발급마다
        // 시도 횟수 제한이 리셋되는 문제가 그대로 재현된다. 처음 한 번 정해진 정규 표기가 그
        // 이후 모든 재발급에서 고정돼야 한다 - 첫 요청(기존 토큰이 없는 경우)은 이어갈 대상
        // 자체가 없으므로 이번 요청의 정규화된 값을 그대로 쓴다.
        String canonicalEmail = existingToken.map(EmailVerificationToken::getEmail).orElse(email);
        // 재발급 자체를 반복하는 공격(시도 횟수 우회)을 늦추기 위한 쿨다운 - 이메일 인증 시도 횟수
        // 제한(VerificationAttemptLimiter)과는 별개로, "코드 재발급"이라는 행위 자체에 거는 제한이다.
        if (!verificationAttemptLimiter.tryStartCooldown(canonicalEmail)) {
            throw new TooManyVerificationRequestsException("잠시 후 다시 시도해주세요.");
        }
        // 재발급이면 이전 미소비 코드를 즉시 무효화한다 - 항상 최신 코드 하나만 유효해야 한다.
        existingToken.ifPresent(EmailVerificationToken::consume);

        String code = generateCode();
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create(code, canonicalEmail, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));
        emailVerificationMailService.sendVerification(canonicalEmail, code);
    }

    @Transactional
    public TokenPair completeSignup(String rawEmail, String code, String password, String nickname) {
        String email = normalize(rawEmail);
        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow(() -> new InvalidVerificationTokenException("인증코드를 먼저 요청해주세요."));
        // DB 조회에 쓴 email 대신 verificationToken.getEmail()(토큰에 저장된 정규 표기)로 시도
        // 횟수를 센다 - MySQL 콜레이션은 대소문자뿐 아니라 악센트/전각문자도 같은 값으로 취급하므로
        // (예: víctim@x.com == victim@x.com), 클라이언트가 보낸 문자열을 그대로 키로 쓰면 같은
        // 계정에 대해 사실상 무한히 많은 "다른 키"를 만들어 시도 횟수 제한을 우회할 수 있다.
        // DB가 이미 판단해준 정규 표기를 그대로 쓰면 이 우회가 원천적으로 불가능해진다.
        String canonicalEmail = verificationToken.getEmail();
        // 시도 횟수는 이메일 기준으로 Redis에서 원자적으로 예약한 뒤 그 결과로 판단한다 -
        // "확인 후 증가"는 동시 요청 사이에서 경쟁이 발생하므로, 먼저 증가시키고 결과가
        // 한도를 넘으면 코드 비교 자체를 하지 않는다.
        long attemptNumber = verificationAttemptLimiter.recordAttempt(canonicalEmail);
        if (verificationAttemptLimiter.exceedsLimit(attemptNumber)) {
            throw new TooManyVerificationAttemptsException("인증 시도 횟수를 초과했습니다. 인증코드를 다시 요청해주세요.");
        }
        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationTokenExpiredException("인증코드가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!verificationToken.matchesCode(code)) {
            throw new VerificationCodeMismatchException("인증코드가 일치하지 않습니다.");
        }
        if (!nicknameValidator.isValidFormat(nickname)) {
            throw new InvalidNicknameException("닉네임 형식이 올바르지 않습니다.");
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new NicknameDuplicateException("이미 사용중인 닉네임입니다.");
        }

        User user = userRepository.save(
                User.createEmailAccount(canonicalEmail, nickname, passwordEncoder.encode(password)));
        verificationToken.consume();
        verificationAttemptLimiter.reset(canonicalEmail);

        String deviceId = UUID.randomUUID().toString();
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), deviceId);
        refreshTokenService.save(user.getId(), deviceId, refreshToken);
        return new TokenPair(accessToken, refreshToken);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
