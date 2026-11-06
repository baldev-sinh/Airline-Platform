package com.baldev.airline.seat.service.impl;

import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.payload.request.SeatHoldRequest;
import com.baldev.airline.seat.model.FlightInstanceCabin;
import com.baldev.airline.seat.model.SeatInstance;
import com.baldev.airline.seat.repository.FlightInstanceCabinRepository;
import com.baldev.airline.seat.repository.SeatInstanceRepository;
import com.baldev.airline.seat.service.SeatInventoryService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the seat-locking guarantees against a real MySQL (row locks cannot be tested with mocks or H2).
 * Each test fires real concurrent transactions through the Spring service proxy and checks the database afterwards.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "eureka.client.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false",
                "airline.seat.hold-expiry-check-interval=PT1H",
                "DB_POOL_MAX_SIZE=30",
                // Short lock wait so the timeout test runs quickly; production uses 3 s.
                "DB_LOCK_WAIT_TIMEOUT_SECONDS=1"
        })
class SeatLockingConcurrencyTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    /** Every test works on its own flight instance, so tests never see each other's rows. */
    private static final AtomicLong FLIGHT_INSTANCE_IDS = new AtomicLong(10_000);

    @Autowired
    private SeatInventoryService seatInventoryService;
    @Autowired
    private SeatInstanceRepository seatInstanceRepository;
    @Autowired
    private FlightInstanceCabinRepository flightInstanceCabinRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void manyCustomersRacingForOneSeat_exactlyOneGetsIt() throws Exception {
        long flightInstanceId = FLIGHT_INSTANCE_IDS.incrementAndGet();
        Long seatId = newSeat(flightInstanceId, null).getId();
        int customers = 20;

        List<Outcome> outcomes = runConcurrently(customers, i -> {
            long bookingId = 1_000L + i;
            seatInventoryService.holdSeats(holdRequest(bookingId, flightInstanceId, seatId));
            return bookingId;
        });

        List<Outcome> winners = outcomes.stream().filter(o -> o.bookingId() != null).toList();
        assertThat(winners).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() != null)
                .hasSize(customers - 1)
                .allSatisfy(o -> assertThat(o.error())
                        .isInstanceOf(ConflictException.class)
                        .hasMessageContaining("no longer available"));

        SeatInstance seat = seatInstanceRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatAvailabilityStatus.HELD);
        assertThat(seat.getHeldByBookingId()).isEqualTo(winners.getFirst().bookingId());
    }

    @RepeatedTest(10)
    void overlappingMultiSeatHoldsInOppositeOrder_neverDeadlockAndStayAllOrNothing() throws Exception {
        long flightInstanceId = FLIGHT_INSTANCE_IDS.incrementAndGet();
        Long seatA = newSeat(flightInstanceId, null).getId();
        Long seatB = newSeat(flightInstanceId, null).getId();

        // Customer 1 asks for [A, B], customer 2 for [B, A]. Without ordered locking this is the classic deadlock.
        List<Outcome> outcomes = runConcurrently(2, i -> {
            long bookingId = 2_000L + i;
            List<Long> seats = i == 0 ? List.of(seatA, seatB) : List.of(seatB, seatA);
            seatInventoryService.holdSeats(SeatHoldRequest.builder()
                    .bookingId(bookingId).flightInstanceId(flightInstanceId).seatInstanceIds(seats).build());
            return bookingId;
        });

        List<Outcome> winners = outcomes.stream().filter(o -> o.bookingId() != null).toList();
        assertThat(winners).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() != null)
                .singleElement()
                .satisfies(o -> assertThat(o.error()).isInstanceOf(ConflictException.class));

        // Both seats belong to the winner: the loser held nothing, not even the seat it reached first.
        assertThat(seatInstanceRepository.findAllById(List.of(seatA, seatB)))
                .allSatisfy(s -> assertThat(s.getHeldByBookingId()).isEqualTo(winners.getFirst().bookingId()));
    }

    @Test
    void concurrentConfirmationsAndCancellations_keepTheCabinCounterExact() throws Exception {
        long flightInstanceId = FLIGHT_INSTANCE_IDS.incrementAndGet();
        FlightInstanceCabin cabin = flightInstanceCabinRepository.save(FlightInstanceCabin.builder()
                .flightInstanceId(flightInstanceId).totalSeats(30).bookedSeats(0).build());
        int bookings = 30;
        long firstBooking = 3_000L;
        for (int i = 0; i < bookings; i++) {
            SeatInstance seat = newSeat(flightInstanceId, cabin);
            seat.hold(firstBooking + i, Instant.now().plus(Duration.ofMinutes(20)));
            seatInstanceRepository.save(seat);
        }

        // 30 payments complete at the same moment: every confirmation must be counted.
        runConcurrently(bookings, i -> {
            seatInventoryService.confirmSeats(firstBooking + i);
            return firstBooking + i;
        });
        assertThat(bookedSeats(cabin)).isEqualTo(bookings);

        // 10 of them are cancelled at the same moment: every release must be counted.
        runConcurrently(10, i -> {
            seatInventoryService.releaseSeats(firstBooking + i, true);
            return firstBooking + i;
        });
        assertThat(bookedSeats(cabin)).isEqualTo(bookings - 10);
    }

    @Test
    void waitingForALockedSeatGivesUpAfterTheLockTimeout() throws Exception {
        long flightInstanceId = FLIGHT_INSTANCE_IDS.incrementAndGet();
        Long seatId = newSeat(flightInstanceId, null).getId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        // Another transaction takes the row lock and keeps it longer than the 1 s lock wait timeout.
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                seatInstanceRepository.findAllByIdForUpdate(List.of(seatId));
                locked.countDown();
                await(finish);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            assertThatThrownBy(() -> seatInventoryService.holdSeats(holdRequest(4_000L, flightInstanceId, seatId)))
                    // Mapped to 409 LOCK_CONFLICT by PersistenceExceptionHandler.
                    .isInstanceOf(PessimisticLockingFailureException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));

            finish.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
        // The failed attempt changed nothing.
        assertThat(seatInstanceRepository.findById(seatId).orElseThrow().isAvailable()).isTrue();
    }

    // ---------------------------------------------------------------------------------------------------------

    private SeatInstance newSeat(long flightInstanceId, FlightInstanceCabin cabin) {
        return seatInstanceRepository.save(SeatInstance.builder()
                .flightId(1L)
                .flightInstanceId(flightInstanceId)
                .flightInstanceCabin(cabin)
                .status(SeatAvailabilityStatus.AVAILABLE)
                .build());
    }

    private int bookedSeats(FlightInstanceCabin cabin) {
        return flightInstanceCabinRepository.findById(cabin.getId()).orElseThrow().getBookedSeats();
    }

    private static SeatHoldRequest holdRequest(long bookingId, long flightInstanceId, Long seatId) {
        return SeatHoldRequest.builder()
                .bookingId(bookingId).flightInstanceId(flightInstanceId).seatInstanceIds(List.of(seatId)).build();
    }

    /** What one concurrent caller ended with: the booking id it returned, or the exception it threw. */
    private record Outcome(Long bookingId, Throwable error) {
    }

    @FunctionalInterface
    private interface Task {
        long run(int index) throws Exception;
    }

    /**
     * Starts {@code count} threads that wait on one gate and are released together, so their transactions really
     * overlap, then collects every result.
     */
    private static List<Outcome> runConcurrently(int count, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            LongStream.range(0, count).forEach(i -> futures.add(pool.submit((Callable<Outcome>) () -> {
                ready.countDown();
                await(go);
                try {
                    return new Outcome(task.run((int) i), null);
                } catch (Exception e) {
                    return new Outcome(null, e);
                }
            })));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test threads");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
