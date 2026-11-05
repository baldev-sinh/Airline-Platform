package com.baldev.services;

import com.baldev.model.Passenger;
import com.baldev.payload.request.PassengerRequest;

public interface PassengerService {

    Passenger createPassenger(PassengerRequest request, Long userId);
}
