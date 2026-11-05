package com.baldev.airline.payment.controller;

import com.baldev.airline.common.exception.BadRequestException;
import com.baldev.airline.common.exception.ForbiddenException;
import com.baldev.airline.common.payload.dto.PaymentDTO;
import com.baldev.airline.common.payload.request.PaymentVerifyRequest;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "amount", "status", "paidAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentService paymentService;

    @PostMapping("/verify")
    public PaymentDTO verifyPayment(@Valid @RequestBody PaymentVerifyRequest request, CurrentUser user) {
        return paymentService.verifyPayment(user, request);
    }

    @GetMapping("/{id}")
    public PaymentDTO getPayment(@PathVariable Long id, CurrentUser user) {
        return paymentService.getPayment(user, id);
    }

    /** Back-office listing; the gateway restricts it to system admins and the service re-checks. */
    @GetMapping
    public Page<PaymentDTO> getAllPayments(CurrentUser user,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "createdAt") String sortBy,
                                           @RequestParam(defaultValue = "DESC") String sortDirection) {
        if (!user.isSystemAdmin()) {
            throw new ForbiddenException("Only system administrators can list payments");
        }
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BadRequestException("sortBy must be one of " + SORTABLE_FIELDS);
        }
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return paymentService.getAllPayments(
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(direction, sortBy)));
    }
}
