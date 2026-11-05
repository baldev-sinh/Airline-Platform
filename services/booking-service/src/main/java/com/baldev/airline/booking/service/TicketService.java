package com.baldev.airline.booking.service;

import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.booking.model.Ticket;

import java.util.List;

public interface TicketService {

    List<Ticket> generateTicketsForBooking(Booking booking);
}
