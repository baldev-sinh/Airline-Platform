package com.baldev.services.impl;

import com.baldev.mapper.PassengerMapper;
import com.baldev.model.Passenger;
import com.baldev.payload.request.PassengerRequest;
import com.baldev.repository.PassengerRepository;
import com.baldev.services.PassengerService;
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
