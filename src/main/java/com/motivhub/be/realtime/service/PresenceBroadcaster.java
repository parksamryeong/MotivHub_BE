package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.dto.TaskPresenceMessage;
import com.motivhub.be.user.dto.UserSummary;
import com.motivhub.be.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PresenceBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PresenceBroadcaster.class);

    private final PresenceService presenceService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Duration debounceWindow;

    // 이 두 맵/셋은 애플리케이션 인스턴스 로컬 상태다 - 단일 EC2 인스턴스 배포를 전제로 한다.
    // 인스턴스를 여러 대로 늘리면(수평 확장) 같은 taskId의 이벤트가 서로 다른 인스턴스로
    // 분산될 수 있어 디바운스가 인스턴스별로 따로 동작하게 된다 - 그때는 이 상태를 Redis로
    // 옮겨야 한다.
    private final ConcurrentHashMap<Long, Instant> lastBroadcastAt = new ConcurrentHashMap<>();
    private final Set<Long> pendingTaskIds = ConcurrentHashMap.newKeySet();

    public PresenceBroadcaster(PresenceService presenceService, UserRepository userRepository,
                                SimpMessagingTemplate messagingTemplate,
                                @Value("${app.realtime.presence.debounce-window-ms}") long debounceWindowMs) {
        this.presenceService = presenceService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.debounceWindow = Duration.ofMillis(debounceWindowMs);
    }

    /**
     * 프레즌스 변경(입장/퇴장)이 생겼을 때 호출한다. 마지막 전송 후 디바운스 창이 지났으면
     * 즉시 전송하고(leading edge), 아니면 pending으로만 표시해서 {@link #flushPending()}이
     * 나중에 한 번만 마무리 전송하게 한다.
     */
    public void requestBroadcast(Long taskId) {
        Instant now = Instant.now();
        Instant last = lastBroadcastAt.get(taskId);
        if (last == null || Duration.between(last, now).compareTo(debounceWindow) >= 0) {
            lastBroadcastAt.put(taskId, now);
            pendingTaskIds.remove(taskId);
            broadcast(taskId);
        } else {
            pendingTaskIds.add(taskId);
        }
    }

    @Scheduled(fixedDelayString = "${app.realtime.presence.poll-interval-ms}")
    public void flushPending() {
        if (pendingTaskIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Long taskId : Set.copyOf(pendingTaskIds)) {
            Instant last = lastBroadcastAt.get(taskId);
            if (last != null && Duration.between(last, now).compareTo(debounceWindow) < 0) {
                continue;
            }
            pendingTaskIds.remove(taskId);
            lastBroadcastAt.put(taskId, now);
            try {
                broadcast(taskId);
            } catch (Exception e) {
                log.warn("프레즌스 마무리 브로드캐스트 실패 - taskId={}", taskId, e);
            }
        }
    }

    private void broadcast(Long taskId) {
        List<Long> viewerIds = presenceService.currentViewerIds(taskId);
        List<UserSummary> viewers = userRepository.findAllById(viewerIds).stream()
                .map(UserSummary::from)
                .toList();
        messagingTemplate.convertAndSend("/topic/tasks/" + taskId + "/presence",
                new TaskPresenceMessage(taskId, viewers));
    }
}
