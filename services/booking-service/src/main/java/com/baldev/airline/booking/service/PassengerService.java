package com.baldev.airline.booking.service;

import com.baldev.airline.booking.model.Passenger;
import com.baldev.airline.common.payload.request.PassengerRequest;

public interface PassengerService {

    Passenger createPassenger(PassengerRequest request, Long userId);
}
