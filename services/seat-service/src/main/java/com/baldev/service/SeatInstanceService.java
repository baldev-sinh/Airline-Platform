package com.baldev.service;


import com.baldev.enums.SeatAvailabilityStatus;
import com.baldev.payload.response.SeatInstanceResponse;

import java.util.List;

public interface SeatInstanceService {

    Double calculateSeatPrice(List<Long> seatInstanceIds);

    SeatInstanceResponse updateSeatInstanceStatus(Long seatInstanceId,
                                                  SeatAvailabilityStatus status);
}
