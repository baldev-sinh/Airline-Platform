package com.baldev.services;

import com.baldev.model.Booking;
import com.baldev.model.Ticket;

import java.util.List;

public interface TicketService {

    List<Ticket> generateTicketsForBooking(Booking booking);
}
