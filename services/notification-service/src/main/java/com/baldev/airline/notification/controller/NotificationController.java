package com.baldev.airline.notification.controller;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.notification.model.Notification;
import com.baldev.airline.notification.model.Notification.Channel;
import com.baldev.airline.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** The signed-in customer's in-app inbox. Every query is scoped to the caller; other users' entries are 404. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repository;
    private final Clock clock;

    /** Inbox entry as returned to the customer (no e-mail address, no delivery internals). */
    public record NotificationResponse(Long id, String type, String subject, String summary, Instant createdAt,
                                       Instant readAt) {
        static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getSubject(), n.getSummary(), n.getCreatedAt(),
                    n.getReadAt());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(CurrentUser user, @PageableDefault(size = 20) Pageable pageable) {
        return repository.findByUserIdAndChannelOrderByCreatedAtDesc(user.id(), Channel.IN_APP, pageable)
                .map(NotificationResponse::of);
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(CurrentUser user) {
        return Map.of("unread", repository.countByUserIdAndChannelAndReadAtIsNull(user.id(), Channel.IN_APP));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public NotificationResponse markRead(CurrentUser user, @PathVariable Long id) {
        Notification notification = repository.findByIdAndUserIdAndChannel(id, user.id(), Channel.IN_APP)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
        return NotificationResponse.of(notification);
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Integer>> markAllRead(CurrentUser user) {
        return ResponseEntity.ok(Map.of("updated", repository.markAllRead(user.id(), Channel.IN_APP, clock.instant())));
    }
}
