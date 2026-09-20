package com.motivhub.be.user.repository;

import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByProviderAndProviderId(SocialProvider provider, String providerId);
    boolean existsByNickname(String nickname);
    List<User> findByNicknameIn(List<String> nicknames);
}
