package com.motivhub.be.support;

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
}
