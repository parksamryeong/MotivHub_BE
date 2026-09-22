package com.motivhub.be.notification.service;

import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.event.AssigneeAddedEvent;
import com.motivhub.be.task.event.ChecklistCompletedEvent;
import com.motivhub.be.task.event.DueDateApproachingEvent;
import com.motivhub.be.task.event.TaskCommentCreatedEvent;
import com.motivhub.be.task.event.TaskOverdueEvent;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.repository.TaskWatcherRepository;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![가-힣a-zA-Z0-9])@([가-힣a-zA-Z0-9]{2,15})");
    private static final List<String> TRAILING_PARTICLES = List.of(
            "님께", "님은", "님이", "님을", "한테", "에게", "님",
            "께", "이", "가", "은", "는", "을", "를", "아", "야", "씨");

    private final NotificationService notificationService;
    private final TaskService taskService;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskWatcherRepository taskWatcherRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public NotificationEventListener(NotificationService notificationService, TaskService taskService,
                                      TaskAssigneeRepository taskAssigneeRepository,
                                      TaskWatcherRepository taskWatcherRepository,
                                      WorkspaceMemberRepository workspaceMemberRepository,
                                      UserRepository userRepository) {
        this.notificationService = notificationService;
        this.taskService = taskService;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskWatcherRepository = taskWatcherRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssigneeAdded(AssigneeAddedEvent event) {
        handleSafely("onAssigneeAdded", () -> {
            Task task = taskService.getTask(event.taskId());
            String message = "'" + task.getName() + "'의 담당자로 지정되었습니다.";
            notifySafely(event.newAssigneeUserId(), NotificationType.ASSIGNEE_ADDED,
                    NotificationTargetType.TASK, task.getId(), message);

            Set<Long> watcherRecipientIds = watcherIds(task.getId());
            watcherRecipientIds.remove(event.newAssigneeUserId());
            if (!watcherRecipientIds.isEmpty()) {
                User newAssignee = userRepository.findById(event.newAssigneeUserId())
                        .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
                String watcherMessage = "'" + newAssignee.getNickname() + "'님이 '" + task.getName() + "'의 담당자로 지정되었습니다.";
                for (Long watcherId : watcherRecipientIds) {
                    notifySafely(watcherId, NotificationType.ASSIGNEE_ADDED,
                            NotificationTargetType.TASK, task.getId(), watcherMessage);
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCommentCreated(TaskCommentCreatedEvent event) {
        handleSafely("onTaskCommentCreated", () -> {
            Task task = taskService.getTask(event.taskId());
            Set<Long> recipientIds = assigneeIds(task.getId());
            recipientIds.addAll(watcherIds(task.getId()));
            recipientIds.add(task.getCreatedBy().getId());
            recipientIds.remove(event.authorId());
            String message = "'" + event.authorNickname() + "'님이 '" + task.getName() + "'에 댓글을 남겼습니다.";
            for (Long recipientId : recipientIds) {
                notifySafely(recipientId, NotificationType.TASK_COMMENT_ADDED,
                        NotificationTargetType.TASK, task.getId(), message);
            }

            String mentionMessage = "'" + event.authorNickname() + "'님이 '" + task.getName() + "' 댓글에서 회원님을 언급했습니다.";
            for (Long mentionedUserId : mentionedWorkspaceMemberIds(event.content(), task, event.authorId())) {
                notifySafely(mentionedUserId, NotificationType.MENTIONED,
                        NotificationTargetType.TASK, task.getId(), mentionMessage);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChecklistCompleted(ChecklistCompletedEvent event) {
        handleSafely("onChecklistCompleted", () -> {
            Task task = taskService.getTask(event.taskId());
            Set<Long> recipientIds = assigneeIds(task.getId());
            recipientIds.addAll(watcherIds(task.getId()));
            String message = "'" + task.getName() + "'의 체크리스트를 모두 완료했습니다.";
            for (Long recipientId : recipientIds) {
                notifySafely(recipientId, NotificationType.CHECKLIST_COMPLETED,
                        NotificationTargetType.TASK, task.getId(), message);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDueDateApproaching(DueDateApproachingEvent event) {
        handleSafely("onDueDateApproaching", () -> {
            Task task = taskService.getTask(event.taskId());
            Set<Long> recipientIds = assigneeIds(task.getId());
            recipientIds.addAll(watcherIds(task.getId()));
            workspaceMemberRepository.findByWorkspaceIdAndRole(task.getWorkspace().getId(), WorkspaceRole.OWNER)
                    .ifPresent(owner -> recipientIds.add(owner.getUser().getId()));
            String message = "'" + task.getName() + "' 마감일이 이틀 남았습니다.";
            for (Long recipientId : recipientIds) {
                if (!notificationService.alreadyNotifiedToday(recipientId, NotificationType.DUE_DATE_APPROACHING, task.getId())) {
                    notifySafely(recipientId, NotificationType.DUE_DATE_APPROACHING,
                            NotificationTargetType.TASK, task.getId(), message);
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskOverdue(TaskOverdueEvent event) {
        handleSafely("onTaskOverdue", () -> {
            Task task = taskService.getTask(event.taskId());
            Set<Long> recipientIds = assigneeIds(task.getId());
            recipientIds.addAll(watcherIds(task.getId()));
            workspaceMemberRepository.findByWorkspaceIdAndRole(task.getWorkspace().getId(), WorkspaceRole.OWNER)
                    .ifPresent(owner -> recipientIds.add(owner.getUser().getId()));
            String message = "'" + task.getName() + "' 마감일이 지나 자동으로 만료 처리되었습니다.";
            // 이 태스크는 TaskExpirationScheduler에서 WAITING/IN_PROGRESS -> EXPIRED로 전환될 때만 이 이벤트를
            // 받는데, 그 스케줄러의 조회 조건(status IN (WAITING, IN_PROGRESS))상 한 태스크가 이 경로를 두 번
            // 탈 수 없다 - 그래서 onDueDateApproaching과 달리 alreadyNotifiedToday 중복 방지가 필요 없다.
            for (Long recipientId : recipientIds) {
                notifySafely(recipientId, NotificationType.TASK_OVERDUE,
                        NotificationTargetType.TASK, task.getId(), message);
            }
        });
    }

    private void handleSafely(String handlerName, Runnable body) {
        try {
            body.run();
        } catch (Exception e) {
            log.warn("알림 이벤트 처리 실패 - handler={}", handlerName, e);
        }
    }

    private void notifySafely(Long recipientId, NotificationType type, NotificationTargetType targetType,
                               Long targetId, String message) {
        try {
            notificationService.notify(recipientId, type, targetType, targetId, message);
        } catch (Exception e) {
            log.warn("알림 생성 실패 - recipientId={}, type={}, targetId={}", recipientId, type, targetId, e);
        }
    }

    private Set<Long> assigneeIds(Long taskId) {
        Set<Long> ids = new HashSet<>();
        taskAssigneeRepository.findByTaskId(taskId).forEach(assignee -> ids.add(assignee.getUser().getId()));
        return ids;
    }

    private Set<Long> watcherIds(Long taskId) {
        Set<Long> ids = new HashSet<>();
        taskWatcherRepository.findByTaskId(taskId).forEach(watcher -> ids.add(watcher.getUser().getId()));
        return ids;
    }

    // 댓글 내용에서 @닉네임을 추출해, 그 워크스페이스의 멤버이면서 작성자 본인이 아닌 유저 ID만 남긴다.
    // 존재하지 않는 닉네임/비멤버/자기 자신 멘션은 이 필터를 거치면서 조용히 제외된다.
    private Set<Long> mentionedWorkspaceMemberIds(String content, Task task, Long authorId) {
        Set<String> capturedTokens = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            capturedTokens.add(matcher.group(1));
        }
        if (capturedTokens.isEmpty()) {
            return Set.of();
        }

        Set<String> candidateNicknames = new HashSet<>();
        for (String token : capturedTokens) {
            candidateNicknames.add(token);
            for (String particle : TRAILING_PARTICLES) {
                if (token.length() > particle.length() && token.endsWith(particle)) {
                    candidateNicknames.add(token.substring(0, token.length() - particle.length()));
                }
            }
        }

        Map<String, User> userByNickname = new HashMap<>();
        for (User user : userRepository.findByNicknameIn(new ArrayList<>(candidateNicknames))) {
            userByNickname.put(user.getNickname(), user);
        }

        Set<Long> memberUserIds = new HashSet<>();
        workspaceMemberRepository.findByWorkspaceId(task.getWorkspace().getId())
                .forEach(member -> memberUserIds.add(member.getUser().getId()));

        Set<Long> result = new HashSet<>();
        for (String token : capturedTokens) {
            User resolved = resolveLongestMatch(token, userByNickname);
            if (resolved != null && memberUserIds.contains(resolved.getId()) && !resolved.getId().equals(authorId)) {
                result.add(resolved.getId());
            }
        }
        return result;
    }

    // "철수님"처럼 캡처된 문자열 뒤에 존칭/조사가 붙어있으면 떼어내고 실제 존재하는 닉네임을 찾는다.
    // 캡처된 문자열 자체가 실제 닉네임이면 그걸 우선한다(존칭을 잘못 떼어낸 더 짧은 후보로 넘어가지 않도록).
    // 닉네임 조회는 DB collation(utf8mb4_0900_ai_ci, 대소문자 구분 없음)을 그대로 따른다 - @JCHUL이
    // jchul을 찾는 건 의도된 동작이다.
    private User resolveLongestMatch(String captured, Map<String, User> userByNickname) {
        User exact = userByNickname.get(captured);
        if (exact != null) {
            return exact;
        }
        User bestMatch = null;
        for (String particle : TRAILING_PARTICLES) {
            if (captured.length() > particle.length() && captured.endsWith(particle)) {
                String candidate = captured.substring(0, captured.length() - particle.length());
                User user = userByNickname.get(candidate);
                if (user != null && (bestMatch == null || candidate.length() > bestMatch.getNickname().length())) {
                    bestMatch = user;
                }
            }
        }
        return bestMatch;
    }
}
