package com.baldev.airline.payment.repository;

import com.baldev.airline.common.enums.PaymentStatus;
import com.baldev.airline.payment.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingIdIn(Collection<Long> bookingIds);

    Optional<Payment> findFirstByBookingIdAndStatusOrderByIdDesc(Long bookingId, PaymentStatus status);

    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    /**
     * Serializes concurrent settlement of the same payment (client verify racing the gateway webhook) so the
     * completion event is published exactly once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    /** Payments in the given status that have a provider link and were created in the window, oldest first. */
    @Query("select p.id from Payment p where p.status = :status and p.providerOrderId is not null"
            + " and p.createdAt between :from and :to order by p.createdAt")
    List<Long> findIdsWithLinkCreatedBetween(@Param("status") PaymentStatus status, @Param("from") Instant from,
                                             @Param("to") Instant to, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerOrderId = :providerOrderId")
    Optional<Payment> findByProviderOrderIdForUpdate(@Param("providerOrderId") String providerOrderId);
}
