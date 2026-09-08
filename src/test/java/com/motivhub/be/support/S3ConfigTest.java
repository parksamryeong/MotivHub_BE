package com.motivhub.be.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3ConfigTest extends AbstractIntegrationTest {

    @Autowired private S3Client s3Client;
    @Autowired private S3Presigner s3Presigner;
    @Value("${aws.s3.bucket}") private String bucket;

    @Test
    void s3ClientCanPutAndGetObjectAgainstLocalStack() {
        String key = "smoke-test/" + System.currentTimeMillis() + ".txt";

        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromString("hello", StandardCharsets.UTF_8));

        String content = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .asUtf8String();

        assertThat(content).isEqualTo("hello");
    }

    @Test
    void s3PresignerProducesPathStyleUrlUsableForRealHttpPut() throws Exception {
        String key = "smoke-test/presigned-" + System.currentTimeMillis() + ".txt";

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key).build())
                        .build());

        URI presignedUrl = presignedRequest.url().toURI();

        // 경로 스타일(path-style)이어야 한다: http://localhost:4566/{bucket}/{key}
        // 버추얼 호스트 스타일(virtual-hosted-style)이면 http://{bucket}.localhost:4566/{key}가
        // 되는데, {bucket}.localhost 형태의 DNS 해석은 OS/리졸버에 따라 보장되지 않는다
        // (특히 Windows). Task 2~4의 프리사인드 URL 실제 HTTP PUT/GET 테스트가 이 config에
        // 기대는 전제이므로, 여기서 직접 검증한다.
        assertThat(presignedUrl.getHost()).doesNotContain(bucket);
        assertThat(presignedUrl.getPath()).startsWith("/" + bucket + "/");

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(presignedUrl)
                        .PUT(BodyPublishers.ofString("presigned-hello", StandardCharsets.UTF_8))
                        .build(),
                BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        String content = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .asUtf8String();
        assertThat(content).isEqualTo("presigned-hello");
    }
}
