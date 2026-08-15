package com.example.payment.service;

import com.example.payment.dto.CreatePaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.UserPaymentSummary;
import com.example.payment.dto.UserResponse;
import com.example.payment.exception.ResourceNotFoundException;
import com.example.payment.model.Currency;
import com.example.payment.model.Payment;
import com.example.payment.model.PaymentPurpose;
import com.example.payment.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final GoogleSheetsService googleSheetsService;
    private final UserService userService;

    @Value("${app.default-created-by}")
    private String defaultCreatedBy;

    public PaymentService(GoogleSheetsService googleSheetsService, UserService userService) {
        this.googleSheetsService = googleSheetsService;
        this.userService = userService;
    }

    public List<PaymentResponse> getAllPayments() {
        List<Payment> payments = googleSheetsService.getPayments();
        Map<Long, User> usersById = indexUsers();
        return payments.stream()
                .map(p -> toResponse(p, usersById.get(p.getUserId())))
                .toList();
    }

    public PaymentResponse getPaymentById(String id) {
        Payment payment = googleSheetsService.findPaymentById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with id " + id + " was not found"));
        User user = googleSheetsService.findUserById(payment.getUserId()).orElse(null);
        return toResponse(payment, user);
    }

    public List<PaymentResponse> getPaymentsByUserId(Long userId) {
        // Ensure the user exists so callers get a clean 404 rather than an empty list for typos.
        User user = googleSheetsService.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found"));
        return googleSheetsService.getPaymentsByUserId(userId).stream()
                .map(p -> toResponse(p, user))
                .toList();
    }

    /**
     * Free-text search across payment id, user name, and phone number.
     */
    public List<PaymentResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return getAllPayments();
        }
        String normalized = query.trim().toLowerCase();
        Map<Long, User> usersById = indexUsers();

        return googleSheetsService.getPayments().stream()
                .filter(p -> {
                    User user = usersById.get(p.getUserId());
                    boolean matchesId = p.getId() != null && p.getId().toLowerCase().contains(normalized);
                    boolean matchesName = user != null && user.getName() != null
                            && user.getName().toLowerCase().contains(normalized);
                    boolean matchesPhone = user != null && user.getPhoneNumber() != null
                            && user.getPhoneNumber().toLowerCase().contains(normalized);
                    return matchesId || matchesName || matchesPhone;
                })
                .map(p -> toResponse(p, usersById.get(p.getUserId())))
                .toList();
    }

    public PaymentResponse createPayment(CreatePaymentRequest request) {
        User user = userService.findOrCreateUser(request.getPhoneNumber(), request.getName());

        Payment payment = Payment.builder()
                .userId(user.getId())
                .visitor(Boolean.TRUE.equals(request.getIsVisitor()))
                .paymentType(request.getPaymentType())
                .paymentPurpose(request.getPaymentPurpose())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                // createdBy is set here by the server - never trusted from the client.
                // TODO: once authentication is added, resolve from the SecurityContext.
                .createdBy(defaultCreatedBy)
                .build();

        Payment saved = googleSheetsService.createPayment(payment);
        return toResponse(saved, user);
    }

    public UserPaymentSummary getUserSummary(Long userId) {
        User user = googleSheetsService.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found"));

        List<Payment> payments = googleSheetsService.getPaymentsByUserId(userId);

        BigDecimal totalLoans = sumByPurpose(payments, PaymentPurpose.LOAN);
        BigDecimal totalDonations = sumByPurpose(payments, PaymentPurpose.DONATION);
        BigDecimal totalAmount = totalLoans.add(totalDonations);

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .createdBy(user.getCreatedBy())
                .created(user.getCreated())
                .build();

        List<PaymentResponse> paymentResponses = payments.stream()
                .map(p -> toResponse(p, user))
                .toList();

        return UserPaymentSummary.builder()
                .user(userResponse)
                .totalPayments(payments.size())
                .totalLoans(totalLoans)
                .totalDonations(totalDonations)
                .totalAmount(totalAmount)
                .payments(paymentResponses)
                .build();
    }

    // NOTE: this summary intentionally does not attempt to convert across currencies -
    // totals are simple sums. For multi-currency users, consider breaking totals down
    // per-currency in a future iteration.
    private BigDecimal sumByPurpose(List<Payment> payments, PaymentPurpose purpose) {
        return payments.stream()
                .filter(p -> p.getPaymentPurpose() == purpose)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, User> indexUsers() {
        return googleSheetsService.getUsers().stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private PaymentResponse toResponse(Payment payment, User user) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .userName(user != null ? user.getName() : null)
                .phoneNumber(user != null ? user.getPhoneNumber() : null)
                .isVisitor(payment.isVisitor())
                .paymentType(payment.getPaymentType())
                .paymentPurpose(payment.getPaymentPurpose())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .createdBy(payment.getCreatedBy())
                .created(payment.getCreated())
                .build();
    }
}
