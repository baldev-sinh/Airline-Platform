package com.baldev.airline.booking.service.impl;

import com.baldev.airline.booking.mapper.PassengerMapper;
import com.baldev.airline.booking.model.Passenger;
import com.baldev.airline.common.payload.request.PassengerRequest;
import com.baldev.airline.booking.repository.PassengerRepository;
import com.baldev.airline.booking.service.PassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PassengerServiceImpl implements PassengerService {


    private final PassengerRepository passengerRepository;

    @Override
    public Passenger createPassenger(PassengerRequest request, Long userId) {

        Passenger passenger = PassengerMapper.toEntity(request);
        passenger.setPrimaryUserId(userId);
        Passenger saved = passengerRepository.save(passenger);
        return saved;
    }


}
