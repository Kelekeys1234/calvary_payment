package com.example.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a row in the "Payments" Google Sheet.
 * Column order:
 * ID | USER_ID | IS_VISITOR | PAYMENT_TYPE | PAYMENT_PURPOSE | AMOUNT | CURRENCY | CREATED_BY | CREATED
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    private String id; // e.g. PAY-000001
    private Long userId;
    private boolean visitor;
    private PaymentType paymentType;
    private PaymentPurpose paymentPurpose;
    private BigDecimal amount;
    private Currency currency;
    private String createdBy;
    private LocalDateTime created;
}
