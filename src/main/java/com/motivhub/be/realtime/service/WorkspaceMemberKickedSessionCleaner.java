package com.motivhub.be.realtime.service;

import com.motivhub.be.workspace.event.WorkspaceMemberKickedEvent;
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
public class WorkspaceMemberKickedSessionCleaner {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceMemberKickedSessionCleaner.class);
    private static final byte[] EMPTY_PAYLOAD = new byte[0];

    private final SimpUserRegistry simpUserRegistry;
    private final MessageChannel clientInboundChannel;

    public WorkspaceMemberKickedSessionCleaner(SimpUserRegistry simpUserRegistry,
                                                @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel) {
        this.simpUserRegistry = simpUserRegistry;
        this.clientInboundChannel = clientInboundChannel;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberKicked(WorkspaceMemberKickedEvent event) {
        try {
            String destination = "/topic/workspaces/" + event.workspaceId() + "/tasks";
            SimpUser user = simpUserRegistry.getUser(String.valueOf(event.userId()));
            if (user == null) {
                return;
            }
            for (SimpSession session : user.getSessions()) {
                for (SimpSubscription subscription : session.getSubscriptions()) {
                    if (destination.equals(subscription.getDestination())) {
                        unsubscribe(session.getId(), subscription.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("추방된 멤버의 보드 구독 해제 실패 - workspaceId={}, userId={}",
                    event.workspaceId(), event.userId(), e);
        }
    }

    private void unsubscribe(String sessionId, String subscriptionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(EMPTY_PAYLOAD, accessor.getMessageHeaders());
        clientInboundChannel.send(message);
    }
}
