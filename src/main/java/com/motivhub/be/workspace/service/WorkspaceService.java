package com.motivhub.be.workspace.service;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceDetailResponse;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.exception.NotWorkspaceOwnerException;
import com.motivhub.be.workspace.exception.WorkspaceLeaveRequiresTransferException;
import com.motivhub.be.workspace.exception.WorkspaceMemberNotFoundException;
import com.motivhub.be.workspace.exception.WorkspaceNotFoundException;
import com.motivhub.be.workspace.event.WorkspaceMemberRemovedEvent;
import com.motivhub.be.workspace.dto.WorkspaceTaskCounts;
import com.motivhub.be.workspace.repository.WorkspaceMemberCount;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.repository.WorkspaceRepository;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.task.repository.TaskStatusCount;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                             WorkspaceMemberRepository workspaceMemberRepository,
                             UserRepository userRepository,
                             TaskRepository taskRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public WorkspaceResponse create(Long userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        Workspace workspace = workspaceRepository.save(Workspace.create(name));
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.OWNER));
        return WorkspaceResponse.of(workspace, WorkspaceRole.OWNER);
    }

    public List<WorkspaceResponse> listMine(Long userId) {
        List<WorkspaceMember> members = workspaceMemberRepository.findByUserIdFetchWorkspace(userId);
        List<Long> workspaceIds = members.stream().map(member -> member.getWorkspace().getId()).toList();
        Map<Long, WorkspaceTaskCounts> countsByWorkspaceId = taskCountsByWorkspaceId(workspaceIds);
        Map<Long, Long> memberCountByWorkspaceId = memberCountByWorkspaceId(workspaceIds);
        return members.stream()
                .map(member -> WorkspaceResponse.of(member.getWorkspace(), member.getRole(),
                        countsByWorkspaceId.getOrDefault(member.getWorkspace().getId(), WorkspaceTaskCounts.empty()),
                        memberCountByWorkspaceId.getOrDefault(member.getWorkspace().getId(), 0L)))
                .toList();
    }

    // 워크스페이스마다 개별 쿼리를 날리지 않고, 한 번의 집계 쿼리로 전부 가져온 뒤 메모리에서 조합한다(N+1 방지).
    private Map<Long, Long> memberCountByWorkspaceId(List<Long> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (WorkspaceMemberCount row : workspaceMemberRepository.countByWorkspaceIdsGroupByWorkspace(workspaceIds)) {
            result.put(row.workspaceId(), row.count());
        }
        return result;
    }

    // 워크스페이스마다 개별 쿼리를 날리지 않고, 한 번의 집계 쿼리로 전부 가져온 뒤 메모리에서 조합한다(N+1 방지).
    private Map<Long, WorkspaceTaskCounts> taskCountsByWorkspaceId(List<Long> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, long[]> countsByStatusIndex = new HashMap<>();
        for (TaskStatusCount row : taskRepository.countByWorkspaceIdsGroupByStatus(workspaceIds)) {
            long[] counts = countsByStatusIndex.computeIfAbsent(row.workspaceId(), id -> new long[4]);
            counts[statusIndex(row.status())] = row.count();
        }
        Map<Long, WorkspaceTaskCounts> result = new HashMap<>();
        countsByStatusIndex.forEach((workspaceId, counts) ->
                result.put(workspaceId, new WorkspaceTaskCounts(counts[0], counts[1], counts[2], counts[3])));
        return result;
    }

    private int statusIndex(TaskStatus status) {
        return switch (status) {
            case WAITING -> 0;
            case IN_PROGRESS -> 1;
            case DONE -> 2;
            case EXPIRED -> 3;
        };
    }

    public WorkspaceDetailResponse getDetail(Long userId, Long workspaceId) {
        WorkspaceMember member = getMembership(workspaceId, userId);
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        WorkspaceTaskCounts taskCounts = taskCountsByWorkspaceId(List.of(workspaceId))
                .getOrDefault(workspaceId, WorkspaceTaskCounts.empty());
        return WorkspaceDetailResponse.of(member.getWorkspace(), member.getRole(), members, taskCounts);
    }

    public Workspace getWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new WorkspaceNotFoundException("워크스페이스를 찾을 수 없습니다."));
    }

    public WorkspaceMember getMembership(Long workspaceId, Long userId) {
        getWorkspace(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new NotWorkspaceMemberException("워크스페이스 멤버가 아닙니다."));
    }

    @Transactional
    public WorkspaceResponse updateName(Long userId, Long workspaceId, String name) {
        requireOwner(workspaceId, userId);
        Workspace workspace = getWorkspace(workspaceId);
        workspace.rename(name);
        return WorkspaceResponse.of(workspace, WorkspaceRole.OWNER);
    }

    @Transactional
    public void delete(Long userId, Long workspaceId) {
        requireOwner(workspaceId, userId);
        getWorkspace(workspaceId).delete();
    }

    @Transactional
    public void leave(Long userId, Long workspaceId) {
        WorkspaceMember member = getMembership(workspaceId, userId);
        if (member.isOwner()) {
            long memberCount = workspaceMemberRepository.countByWorkspaceId(workspaceId);
            if (memberCount > 1) {
                throw new WorkspaceLeaveRequiresTransferException(
                        "다른 멤버가 있는 워크스페이스는 오너십을 이전한 후에만 나갈 수 있습니다.");
            }
            member.getWorkspace().delete();
            eventPublisher.publishEvent(new WorkspaceMemberRemovedEvent(workspaceId, userId));
            return;
        }
        workspaceMemberRepository.delete(member);
        eventPublisher.publishEvent(new WorkspaceMemberRemovedEvent(workspaceId, userId));
    }

    @Transactional
    public void kick(Long ownerUserId, Long workspaceId, Long targetUserId) {
        requireOwner(workspaceId, ownerUserId);
        WorkspaceMember target = getMembership(workspaceId, targetUserId);
        workspaceMemberRepository.delete(target);
        eventPublisher.publishEvent(new WorkspaceMemberRemovedEvent(workspaceId, targetUserId));
    }

    @Transactional
    public void transferOwnership(Long ownerUserId, Long workspaceId, Long newOwnerUserId) {
        WorkspaceMember currentOwner = getMembership(workspaceId, ownerUserId);
        if (!currentOwner.isOwner()) {
            throw new NotWorkspaceOwnerException("워크스페이스 OWNER만 가능한 작업입니다.");
        }
        WorkspaceMember newOwner = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, newOwnerUserId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException("대상이 워크스페이스 멤버가 아닙니다."));
        currentOwner.changeRole(WorkspaceRole.MEMBER);
        newOwner.changeRole(WorkspaceRole.OWNER);
    }

    public void requireOwner(Long workspaceId, Long userId) {
        WorkspaceMember member = getMembership(workspaceId, userId);
        if (!member.isOwner()) {
            throw new NotWorkspaceOwnerException("워크스페이스 OWNER만 가능한 작업입니다.");
        }
    }
}
