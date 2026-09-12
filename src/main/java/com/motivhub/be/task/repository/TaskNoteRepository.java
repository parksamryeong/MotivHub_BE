package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskNote;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskNoteRepository extends JpaRepository<TaskNote, Long> {

    @Query("SELECT n FROM TaskNote n JOIN FETCH n.updatedBy WHERE n.task.id = :taskId")
    Optional<TaskNote> findByTaskId(@Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);
}
