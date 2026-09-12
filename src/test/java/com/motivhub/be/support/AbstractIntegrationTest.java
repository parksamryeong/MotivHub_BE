package com.motivhub.be.support;

import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    private static final String TEST_BUCKET = "motivhub-test";

    private static final AtomicLong USER_SEQUENCE = new AtomicLong();

    @Autowired
    protected UserRepository userRepository;

    // 싱글턴 컨테이너 패턴: @Container를 붙이지 않아야 JUnit5가 테스트 클래스마다
    // 자동으로 stop시키지 않는다. 여러 클래스가 이 static 필드를 공유해야 하므로,
    // JVM 전체에서 단 한 번만 시작되고 절대 재시작되지 않아야 한다.
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("motivhub")
            .withUsername("test")
            .withPassword("test");

    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(Service.S3);

    static {
        MYSQL.start();
        REDIS.start();
        LOCALSTACK.start();
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
        try {
            LOCALSTACK.execInContainer("awslocal", "s3", "mb", "s3://" + TEST_BUCKET);
        } catch (Exception e) {
            throw new RuntimeException("테스트용 S3 버킷 생성 실패", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("aws.region", () -> "us-east-1");
        registry.add("aws.s3.bucket", () -> TEST_BUCKET);
        registry.add("aws.s3.endpoint-override",
                () -> LOCALSTACK.getEndpointOverride(Service.S3).toString());
    }

    /**
     * 여러 테스트 클래스가 하나의 공유 MySQL Testcontainer를 쓰기 때문에, 사람이 고른 접미사
     * (예: "outsider")만으로 유저를 만들면 다른 테스트 파일이 같은 접미사를 골랐을 때 유니크 제약
     * 충돌이 난다(TestTransaction.flagForCommit()으로 실제 커밋하는 테스트가 있으면 특히). 이
     * 메서드는 JVM 전체에서 단조 증가하는 카운터를 접미사에 덧붙여서 충돌 자체를 구조적으로
     * 불가능하게 만든다.
     */
    protected User createUniqueUser(String label) {
        String suffix = label + "-" + USER_SEQUENCE.incrementAndGet();
        // nickname 컬럼은 VARCHAR(30) + UNIQUE 제약이라 label이 길면 넘칠 수 있다. 유일성을
        // 보장하는 카운터는 항상 접미사 맨 끝에 있으므로, 넘칠 경우 뒤쪽 30자를 잘라내
        // 카운터를 보존한다(앞을 자르면 label이 잘리고, 카운터는 항상 남아 유일성은 유지된다).
        String nickname = "user_" + suffix;
        if (nickname.length() > 30) {
            nickname = nickname.substring(nickname.length() - 30);
        }
        return userRepository.save(User.create(
                SocialProvider.GITHUB, suffix, suffix + "@test.com", nickname, null));
    }
}
