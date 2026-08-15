package com.example.payment.service;

import com.example.payment.dto.CreateUserRequest;
import com.example.payment.dto.UserResponse;
import com.example.payment.exception.DuplicateUserException;
import com.example.payment.exception.ResourceNotFoundException;
import com.example.payment.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final GoogleSheetsService googleSheetsService;

    @Value("${app.default-created-by}")
    private String defaultCreatedBy;

    public UserService(GoogleSheetsService googleSheetsService) {
        this.googleSheetsService = googleSheetsService;
    }

    public List<UserResponse> getAllUsers() {
        return googleSheetsService.getUsers().stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse getUserById(Long id) {
        User user = googleSheetsService.findUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " was not found"));
        return toResponse(user);
    }

    public UserResponse searchByPhone(String phoneNumber) {
        User user = googleSheetsService.findUserByPhone(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user found with phone number " + phoneNumber));
        return toResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        googleSheetsService.findUserByPhone(request.getPhoneNumber()).ifPresent(existing -> {
            throw new DuplicateUserException(
                    "User with phone number " + request.getPhoneNumber() + " already exists");
        });

        // TODO: once authentication is added, resolve createdBy from the SecurityContext
        // instead of this hardcoded default.
        User created = googleSheetsService.createUser(
                request.getName(), request.getPhoneNumber(), defaultCreatedBy);
        return toResponse(created);
    }

    /**
     * Finds an existing user by phone, or creates one on the fly.
     * Used by PaymentService when recording a payment for a brand-new user.
     */
    User findOrCreateUser(String phoneNumber, String name) {
        return googleSheetsService.findUserByPhone(phoneNumber)
                .orElseGet(() -> {
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException(
                                "No user exists with phone number " + phoneNumber
                                        + ". Provide 'name' to create a new user.");
                    }
                    return googleSheetsService.createUser(name, phoneNumber, defaultCreatedBy);
                });
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .createdBy(user.getCreatedBy())
                .created(user.getCreated())
                .build();
    }
}
