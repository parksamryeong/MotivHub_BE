package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskExpirationSchedulerTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskExpirationScheduler taskExpirationScheduler;

    @Test
    void overdueIncompleteTaskBecomesExpired() {
        User owner = userRepository.save(User.create(
                SocialProvider.GITHUB, "expire-owner", "expire@test.com", "user_expire", null));
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "만료 워크스페이스");
        TaskResponse overdueTask = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("지난 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));
        TaskResponse futureTask = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("미래 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        taskExpirationScheduler.expireOverdueTasks();

        assertThat(taskService.getDetail(owner.getId(), overdueTask.id()).status()).isEqualTo(TaskStatus.EXPIRED);
        assertThat(taskService.getDetail(owner.getId(), futureTask.id()).status()).isEqualTo(TaskStatus.WAITING);
    }

    @Test
    void completedOverdueTaskStaysUntouched() {
        User owner = userRepository.save(User.create(
                SocialProvider.GITHUB, "expire-owner2", "expire2@test.com", "user_expire2", null));
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "만료 워크스페이스2");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("완료된 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));
        taskService.changeStatus(owner.getId(), task.id(), TaskStatus.DONE);

        taskExpirationScheduler.expireOverdueTasks();

        assertThat(taskService.getDetail(owner.getId(), task.id()).status()).isEqualTo(TaskStatus.DONE);
    }
}
