package com.example.payment.exception;

/**
 * Wraps any failure that occurs while communicating with the Google Sheets API.
 * The original exception is preserved as the cause for logging purposes, but its details
 * are never surfaced directly to API clients.
 */
public class GoogleSheetsIntegrationException extends RuntimeException {
    public GoogleSheetsIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
