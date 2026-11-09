package com.baldev.airline.ancillary.service;

import com.baldev.airline.ancillary.payload.request.PriceQuoteRequest;
import java.math.BigDecimal;
import com.baldev.airline.ancillary.payload.request.FlightMealRequest;
import com.baldev.airline.ancillary.payload.response.FlightMealResponse;
import java.util.List;

public interface FlightMealService {

    FlightMealResponse createFlightMeal(FlightMealRequest request);

    FlightMealResponse getFlightMealById(Long id);

    List<FlightMealResponse> getByFlightId(Long flightId);

    List<FlightMealResponse> getAllByIds(List<Long> ids);

    FlightMealResponse updateFlightMeal(Long id, FlightMealRequest request);

    void deleteFlightMeal(Long id);

    FlightMealResponse updateFlightMealAvailability(Long id, Boolean availability);

    BigDecimal calculateMealPrice(PriceQuoteRequest request);
}
