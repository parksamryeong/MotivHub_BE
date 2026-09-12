package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskNote;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskNoteRepository extends JpaRepository<TaskNote, Long> {

    Optional<TaskNote> findByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
