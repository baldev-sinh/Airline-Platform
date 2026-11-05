package com.baldev.services;

import com.baldev.enums.BookingStatus;
import com.baldev.payload.request.BookingRequest;
import com.baldev.payload.response.BookingResponse;
import com.baldev.payload.response.PaymentInitiateResponse;

import java.util.List;

public interface BookingService {

    PaymentInitiateResponse createBooking(BookingRequest request, Long userId);

    BookingResponse updateBooking(Long id, BookingRequest request);

    BookingResponse getBookingById(Long id) throws Exception;

    List<BookingResponse> getAllBookingsByAirline(Long userId,
                                                  String searchQuery,
                                                  BookingStatus status,
                                                  Long flightInstanceId,
                                                  String sortDirection
    );

    List<BookingResponse> getBookingsByUser(Long userId);

    BookingResponse cancelBooking(Long id) throws Exception;

    void deleteBooking(Long id) throws Exception;
}
