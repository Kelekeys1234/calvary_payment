package com.example.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a row in the "Users" Google Sheet.
 * Column order: ID | NAME | PHONE_NUMBER | CREATED_BY | CREATED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String name;
    private String phoneNumber;
    private String createdBy;
    private LocalDateTime created;
}
