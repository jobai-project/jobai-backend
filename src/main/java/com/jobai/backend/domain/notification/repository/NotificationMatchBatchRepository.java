package com.jobai.backend.domain.notification.repository;

import com.jobai.backend.domain.notification.entity.NotificationMatchBatch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationMatchBatchRepository extends JpaRepository<NotificationMatchBatch, Long> {

    @EntityGraph(attributePaths = {"member", "items"})
    Optional<NotificationMatchBatch> findWithItemsById(Long id);

    List<NotificationMatchBatch> findByMemberId(Long memberId);
}
