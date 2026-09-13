package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.config.RealtimeDestinations;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.workspace.event.WorkspaceMemberRemovedEvent;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkspaceMemberRemovedSessionCleaner {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceMemberRemovedSessionCleaner.class);
    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    private final SimpUserRegistry simpUserRegistry;
    private final MessageChannel clientInboundChannel;
    private final TaskRepository taskRepository;
    private final TaskEditChannelRegistry editChannelRegistry;

    public WorkspaceMemberRemovedSessionCleaner(SimpUserRegistry simpUserRegistry,
                                                 @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel,
                                                 TaskRepository taskRepository,
                                                 TaskEditChannelRegistry editChannelRegistry) {
        this.simpUserRegistry = simpUserRegistry;
        this.clientInboundChannel = clientInboundChannel;
        this.taskRepository = taskRepository;
        this.editChannelRegistry = editChannelRegistry;
    }

    // 참고: 여기서 조회하는 SimpUserRegistry는 이 클래스가 보내는 합성 UNSUBSCRIBE 메시지를 인지하지
    // 못한다 - SessionUnsubscribeEvent는 실제 STOMP 프레임이 StompSubProtocolHandler를 거칠 때만
    // 발행되는데, 이 메시지는 clientInboundChannel로 직접 들어가 그 경로를 우회한다. 브로커의 구독
    // 레지스트리(실제 전달 여부를 결정하는 쪽)에서는 확실히 제거되므로 기능상 문제는 없지만,
    // SimpUserRegistry 자체는 세션이 끊길 때까지 이 구독을 계속 보여줄 수 있다 - "현재 살아있는 구독
    // 목록"의 근거로 SimpUserRegistry를 쓰는 다른 코드가 생기면 이 괴리를 고려해야 한다.
    //
    // 추방(kick)뿐 아니라 자진 탈퇴(leave)에서도 이 이벤트가 발행된다 - 탈퇴를 실행한 바로 그 세션은
    // 프론트가 leave() 성공 응답을 받는 즉시 스스로 구독을 정리할 수 있지만, 같은 유저의 다른 탭/기기는
    // 그 사실을 알 방법이 없다(추방과 정확히 같은 문제). getSessions()가 유저의 모든 활성 세션을
    // 반환하므로 이 리스너 하나로 두 시나리오 다 커버된다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRemoved(WorkspaceMemberRemovedEvent event) {
        try {
            String boardDestination = RealtimeDestinations.workspaceBoard(event.workspaceId());
            Set<String> targetDestinations = revocableDestinations(event.workspaceId(), boardDestination);
            SimpUser user = simpUserRegistry.getUser(String.valueOf(event.userId()));
            if (user == null) {
                return;
            }
            for (SimpSession session : user.getSessions()) {
                for (SimpSubscription subscription : session.getSubscriptions()) {
                    String destination = subscription.getDestination();
                    if (!targetDestinations.contains(destination)) {
                        continue;
                    }
                    // 구독마다 개별 처리 - 한 세션(탭/기기)에서 실패해도 나머지 세션의 구독 해제는
                    // 계속 시도해야 한다.
                    try {
                        unsubscribe(session.getId(), subscription.getId());
                        // 편집 토픽은 구독 해제만으로는 부족하다 - 그 구독이 곧 SEND 인가 근거이므로
                        // 인메모리 레지스트리에서도 회수해야 제외된 멤버가 계속 편집을 릴레이·버퍼링하는
                        // 것(남아있는 정상 편집자가 그 내용을 모르고 저장하게 되는 것)을 막을 수 있다.
                        // STOMP 하트비트 때문에 소켓은 무한히 살아있을 수 있어서 DISCONNECT만 믿을 수 없다.
                        if (!boardDestination.equals(destination)) {
                            editChannelRegistry.revoke(session.getId(), destination);
                        }
                        log.info(
                                "제외된 멤버의 구독 강제 해제 - workspaceId={}, userId={}, sessionId={}, subscriptionId={}, destination={}",
                                event.workspaceId(), event.userId(), session.getId(), subscription.getId(),
                                destination);
                    } catch (Exception e) {
                        log.error(
                                "제외된 멤버의 구독 해제 실패 - workspaceId={}, userId={}, sessionId={}, subscriptionId={}, destination={}",
                                event.workspaceId(), event.userId(), session.getId(), subscription.getId(),
                                destination, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("제외된 멤버의 보드 구독 정리 처리 실패 - workspaceId={}, userId={}",
                    event.workspaceId(), event.userId(), e);
        }
    }

    // 제외된 멤버에게서 회수해야 하는 목적지 전체 - 워크스페이스 보드 + 그 워크스페이스에 속한 모든
    // 태스크의 두 편집 브로드캐스트 토픽. 편집 토픽은 태스크 단위라 워크스페이스 하나에 목적지가
    // (태스크 수 × 2)개 생기므로, 구독 하나마다 문자열 비교를 반복하지 않도록 Set으로 만들어 둔다.
    private Set<String> revocableDestinations(Long workspaceId, String boardDestination) {
        Set<String> destinations = new LinkedHashSet<>();
        destinations.add(boardDestination);
        for (Task task : taskRepository.findByWorkspaceId(workspaceId)) {
            destinations.add(RealtimeDestinations.taskEditBroadcast(task.getId(), TaskEditableField.DESCRIPTION));
            destinations.add(RealtimeDestinations.taskEditBroadcast(task.getId(), TaskEditableField.NOTE));
        }
        return destinations;
    }

    private void unsubscribe(String sessionId, String subscriptionId) {
        // clientInboundChannel은 ExecutorSubscribableChannel이라 send()는 브로커 실행기에 작업을
        // 제출한 뒤 곧바로 반환한다(비동기) - 실제 구독 해제는 다른 스레드에서 수행되므로, 이 메서드가
        // 던지는 예외는 "제출 자체"의 실패만 잡을 뿐 브로커 처리 결과까지 보장하지 않는다.
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        Message<byte[]> message = MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders());
        clientInboundChannel.send(message);
    }
}
