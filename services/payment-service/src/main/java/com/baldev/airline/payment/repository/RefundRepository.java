package com.baldev.airline.payment.repository;

import com.baldev.airline.payment.model.Refund;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundKey(String refundKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.providerRefundId = :providerRefundId")
    Optional<Refund> findByProviderRefundIdForUpdate(@Param("providerRefundId") String providerRefundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.refundKey = :refundKey")
    Optional<Refund> findByRefundKeyForUpdate(@Param("refundKey") String refundKey);

    /** Money already refunded or on its way back for a payment (everything except failed refunds). */
    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.paymentId = :paymentId and r.status <> :excluded")
    BigDecimal sumAmountByPaymentIdExcludingStatus(@Param("paymentId") Long paymentId,
                                                   @Param("excluded") Refund.State excluded);

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.paymentId = :paymentId and r.status = :status")
    BigDecimal sumAmountByPaymentIdAndStatus(@Param("paymentId") Long paymentId, @Param("status") Refund.State status);

    /**
     * Atomically claims a refund for submission to the provider, so a Kafka redelivery and the reconciliation
     * job can never submit the same refund concurrently. @return 1 if this caller owns the submission.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Refund r set r.status = :to, r.attempts = r.attempts + 1, r.updatedAt = :now"
            + " where r.id = :id and r.status = :from")
    int transition(@Param("id") Long id, @Param("from") Refund.State from, @Param("to") Refund.State to,
                   @Param("now") Instant now);

    @Query("select r.id from Refund r where r.status in :states and r.updatedAt < :before order by r.updatedAt")
    List<Long> findIdsInStatesUpdatedBefore(@Param("states") Collection<Refund.State> states,
                                            @Param("before") Instant before, Pageable pageable);
}
