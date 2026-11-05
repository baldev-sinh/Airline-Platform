package com.baldev.airline.booking.service.impl;

import com.baldev.airline.common.enums.TicketStatus;
import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.booking.model.Passenger;
import com.baldev.airline.booking.model.Ticket;
import com.baldev.airline.booking.repository.TicketRepository;
import com.baldev.airline.booking.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    @Override
    public List<Ticket> generateTicketsForBooking(Booking booking) {
        List<Ticket> tickets = new ArrayList<Ticket>();

        for (Passenger passenger : booking.getPassengers()) {
            String ticketNumber = generateUniqueTicketNumber();

            Ticket ticket = Ticket.builder()
                    .ticketNumber(ticketNumber)
                    .status(TicketStatus.BOOKED)
                    .issuedAt(LocalDateTime.now())
                    .booking(booking)
                    .passenger(passenger)
                    .build();

            Ticket savedTicket = ticketRepository.save(ticket);
            tickets.add(savedTicket);
        }
        return tickets;
    }

    private String generateUniqueTicketNumber() {
        String ticketNumber;

        do {
            String datePart = LocalDateTime.now().toString().substring(0, 10);
            String randomPart = UUID.randomUUID().toString().substring(0, 8);
            ticketNumber = String.format("TKT-%s-%s", datePart, randomPart);
        } while (ticketRepository.existsByTicketNumber(ticketNumber));

        return ticketNumber;
    }
}
