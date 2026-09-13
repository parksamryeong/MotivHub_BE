package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskEditAutosaveSchedulerTest extends AbstractIntegrationTest {

    @Autowired private TaskEditBufferService bufferService;
    @Autowired private TaskEditAutosaveScheduler scheduler;

    // application-test.yaml에서 idle-seconds=1, max-wait-seconds=2, retry-seconds=1로 오버라이드했으므로
    // 실제로 몇 초씩 기다리며 검증할 수 있다. 실제 STOMP 브로드캐스트 도달 여부는 Task 8의 종단 테스트가
    // 다루므로, 여기서는 버퍼 메타데이터(lastRequestedAt)만으로 폴러/즉시요청 로직을 검증한다.

    @Test
    void bufferPastIdleThresholdGetsRequestedAndMarksLastRequestedAt() throws Exception {
        bufferService.appendUpdate(9101L, TaskEditableField.DESCRIPTION, "u1");

        // idle-seconds=1을 넘기고, 폴러 주기(1초)가 최소 한 번 돌 시간을 기다린다.
        // 1500ms(브리프 원안)는 이 환경에서 재현 가능한 경합을 안전하게 흡수하지 못해 늘렸다:
        // 컨텍스트 기동 직후 @Scheduled(fixedDelay=1000) 폴러의 "첫 번째 -> 두 번째" 틱 간격이
        // 일회성으로 평소(~1015ms)보다 훨씬 길게(~2.1~2.2s) 관측됨 - WebSocket 브로커가 등록하는
        // 공유 TaskScheduler(스레드명 접두사 "MessageBroker-")의 기동 직후 스레드 생성/워밍업 비용으로
        // 추정. 이 테스트가 클래스 내 두 번째로 실행되는 테스트라 그 구간과 맞물려 3/3 재현 실패했다
        // (근본 원인은 스케줄러 로직 결함이 아니라 컨텍스트 기동 직후의 일회성 스케줄링 지연이므로,
        // 로직을 흉내내거나 대기시간을 짧게 조작하는 대신 실제 대기시간의 여유를 넓혔다).
        // 2500ms면 위 최악의 지연도 흡수하고, 그 시점엔 max-wait-seconds=2도 이미 지나 있어 이중으로 안전하다.
        Thread.sleep(2500);

        var metadata = bufferService.metadata(9101L, TaskEditableField.DESCRIPTION).orElseThrow();
        assertThat(metadata.lastRequestedAt()).isNotNull();
    }

    @Test
    void requestSaveIfDueSkipsWhenRecentlyRequested() {
        bufferService.appendUpdate(9102L, TaskEditableField.DESCRIPTION, "u1");
        bufferService.markRequested(9102L, TaskEditableField.DESCRIPTION);
        var firstRequestedAt = bufferService.metadata(9102L, TaskEditableField.DESCRIPTION)
                .orElseThrow().lastRequestedAt();

        scheduler.requestSaveIfDue(9102L, TaskEditableField.DESCRIPTION);

        var secondRequestedAt = bufferService.metadata(9102L, TaskEditableField.DESCRIPTION)
                .orElseThrow().lastRequestedAt();
        assertThat(secondRequestedAt).isEqualTo(firstRequestedAt);
    }

    @Test
    void requestSaveIfDueDoesNothingForEmptyBuffer() {
        // 버퍼가 아예 없으면(metadata 없음) 예외 없이 조용히 넘어가야 한다.
        scheduler.requestSaveIfDue(9103L, TaskEditableField.DESCRIPTION);

        assertThat(bufferService.metadata(9103L, TaskEditableField.DESCRIPTION)).isEmpty();
    }
}
