package com.baldev.airline.flightops.mapper;

import com.baldev.airline.common.payload.response.FlightScheduleResponse;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.flightops.model.FlightSchedule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.AbstractList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlightScheduleMapperTest {

    /**
     * Behaves like Hibernate's lazy collection: readable while the "session" is open, and throws afterwards,
     * the way the real one does when the response is written as JSON after the transaction has ended.
     */
    private static final class SessionBoundList extends AbstractList<DayOfWeek> {
        private final List<DayOfWeek> days;
        private boolean sessionOpen = true;

        SessionBoundList(List<DayOfWeek> days) {
            this.days = days;
        }

        void closeSession() {
            sessionOpen = false;
        }

        @Override
        public DayOfWeek get(int index) {
            check();
            return days.get(index);
        }

        @Override
        public int size() {
            check();
            return days.size();
        }

        private void check() {
            if (!sessionOpen) throw new IllegalStateException("could not initialize proxy - no Session");
        }
    }

    @Test
    void operatingDaysStayReadableAfterTheSessionCloses() {
        SessionBoundList lazyDays = new SessionBoundList(List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        FlightSchedule schedule = FlightSchedule.builder()
                .id(1L)
                .flight(Flight.builder().id(9L).flightNumber("BD101").build())
                .departureTime(LocalTime.of(6, 30)).arrivalTime(LocalTime.of(8, 40))
                .startDate(LocalDate.of(2030, 1, 1)).endDate(LocalDate.of(2030, 1, 31))
                .operatingDays(lazyDays)
                .isActive(true)
                .build();

        FlightScheduleResponse response = FlightScheduleMapper.toResponse(schedule, null, null); // inside the transaction
        lazyDays.closeSession();                                                                 // transaction ends

        // Writing the response as JSON happens now; it must not touch the entity's collection any more.
        assertThat(response.getOperatingDays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(response.getFlightNumber()).isEqualTo("BD101");
    }

    @Test
    void scheduleWithoutOperatingDaysMapsToNull() {
        FlightSchedule schedule = FlightSchedule.builder().id(2L).build();
        assertThat(FlightScheduleMapper.toResponse(schedule, null, null).getOperatingDays()).isNull();
    }
}
