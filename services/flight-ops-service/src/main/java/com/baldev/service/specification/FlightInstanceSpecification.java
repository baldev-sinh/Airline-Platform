package com.baldev.service.specification;


import com.baldev.enums.FlightStatus;
import com.baldev.model.FlightInstance;
import com.baldev.payload.request.FlightSearchRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class FlightInstanceSpecification {

    private static final Set<FlightStatus> EXCLUDED_STATUSES=Set.of(
            FlightStatus.CANCELLED,
            FlightStatus.COMPLETED,
            FlightStatus.DIVERTED
    );

    private FlightInstanceSpecification(){}

    public static Specification<FlightInstance> buildSearchSpec(FlightSearchRequest request){
        return (root, query,cb)->{
            List<Predicate> predicates=new ArrayList<>();

//            1. only active instances
            predicates.add(cb.isTrue(root.get("isActive")));

//            2. exclude terminal statuses
            predicates.add(root.get("status").in(EXCLUDED_STATUSES).not());

//            3. flight must still be in the future
            predicates.add(cb.greaterThan(root.get("departureDateTime"), LocalDateTime.now()));

//            4. origin airport
            predicates.add(cb.equal(root.get("departureAirportId"),request.getDepartureAirportId()));

//            5. Destination airport
            predicates.add(cb.equal(root.get("arrivalAirportId"),request.getArrivalAirportId()));

//            optional filters

//            6. Departure Date
            LocalDateTime startOfDate=request.getDepartureDate().atStartOfDay();
            LocalDateTime endOfDay= request.getDepartureDate().atTime(LocalTime.MAX);
            predicates.add(cb.between(root.get("departureDateTime"), startOfDate, endOfDay));

            predicates.add(cb.greaterThanOrEqualTo(root.get("availableSeats"),
                    request.getPassengers()));


//            8. airline id
            if(request.getAirlines()!=null && !request.getAirlines().isEmpty()){
                predicates.add(root.get("airlineId").in(request.getAirlines()));
            }
//            9. Departure Time Range
            if(isFilterableTimeRange(request.getDepartureTimeRange())){
                applyTimeRangePredicate(
                        predicates,root,cb,
                        "departureDateTime",
                        request.getDepartureTimeRange());
            }

//            10. arrival time range
            if (isFilterableTimeRange(request.getArrivalTimeRange())) {
                applyTimeRangePredicate(predicates, root, cb,
                        "arrivalDateTime", request.getArrivalTimeRange());
            }
//            11. maximum flight duration in minute
            if(request.getMaxDuration()!=null){
                Expression<Integer> durationMinutes=cb.function(
                        "TIMESTAMPDIFF",
                        Integer.class,
                        cb.literal("MINUTE"),
                        root.get("departureDateTime"),
                        root.get("arrivalDateTime")
                );
                predicates.add(cb.lessThanOrEqualTo(
                        durationMinutes,
                        request.getMaxDuration()));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));

        };


    }

    private static boolean isFilterableTimeRange(String range){
        return range !=null && !range.isBlank() && !range.equalsIgnoreCase("any");
    }

    private static void applyTimeRangePredicate(
            List<Predicate> predicates,
            Root<FlightInstance> root,
            CriteriaBuilder cb,
            String dateTimeField,
            String timeRange
    ){
        Expression<Integer> hour=cb.function("HOUR",Integer.class,root.get(dateTimeField));

        switch (timeRange.toLowerCase()){
            case "morning" -> predicates.add(cb.between(hour, 6, 11));
            case "afternoon"-> predicates.add(cb.between(hour, 12, 17));
            case "evening" -> predicates.add(cb.between(hour,18, 20));
            case "night" -> predicates.add(cb.or(
                    cb.greaterThanOrEqualTo(hour, 21),
                    cb.lessThanOrEqualTo(hour, 5))
            );
            default -> {}
        }
    }
}
