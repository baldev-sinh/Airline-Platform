package com.baldev.airline.seat.service;


import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.common.payload.response.SeatInstanceResponse;

import java.util.List;

public interface SeatInstanceService {

    Double calculateSeatPrice(List<Long> seatInstanceIds);

    SeatInstanceResponse updateSeatInstanceStatus(Long seatInstanceId,
                                                  SeatAvailabilityStatus status);
}
