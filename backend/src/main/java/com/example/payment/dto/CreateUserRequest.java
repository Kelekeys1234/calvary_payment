package com.example.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Payload for creating a new user.
 */
@Data
public class CreateUserRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^\\+?[0-9]{7,15}$",
            message = "Phone number must contain only digits (optionally prefixed with +) and be 7-15 digits long"
    )
    private String phoneNumber;
}
