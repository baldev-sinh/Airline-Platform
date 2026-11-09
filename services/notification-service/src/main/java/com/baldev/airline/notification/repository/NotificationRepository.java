package com.baldev.airline.notification.repository;

import com.baldev.airline.notification.model.Notification;
import com.baldev.airline.notification.model.Notification.Channel;
import com.baldev.airline.notification.model.Notification.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByEventKeyAndChannel(String eventKey, Channel channel);

    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(Long userId, Channel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(Long userId, Channel channel);

    Optional<Notification> findByIdAndUserIdAndChannel(Long id, Long userId, Channel channel);

    @Query("select n.id from Notification n where n.channel = :channel and n.status = :status"
            + " and n.nextAttemptAt <= :now order by n.nextAttemptAt")
    List<Long> findDue(@Param("channel") Channel channel, @Param("status") Status status, @Param("now") Instant now,
                       Pageable pageable);

    /**
     * Claims a due e-mail for one delivery attempt (pushes its next attempt into the future), so two instances of
     * the retry job never send the same e-mail at the same time. @return 1 if this caller owns the attempt.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.nextAttemptAt = :lease, n.attempts = n.attempts + 1"
            + " where n.id = :id and n.status = 'PENDING' and n.nextAttemptAt <= :now")
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("lease") Instant lease);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.channel = :channel and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("channel") Channel channel, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
