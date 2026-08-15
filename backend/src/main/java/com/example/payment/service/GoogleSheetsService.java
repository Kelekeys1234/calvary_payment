package com.example.payment.service;

import com.example.payment.config.GoogleSheetsProperties;
import com.example.payment.exception.GoogleSheetsIntegrationException;
import com.example.payment.model.Currency;
import com.example.payment.model.Payment;
import com.example.payment.model.PaymentPurpose;
import com.example.payment.model.PaymentType;
import com.example.payment.model.User;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single point of contact with the Google Sheets API.
 * <p>
 * No other class in the application talks to the Sheets API directly - controllers and other
 * services always go through here. This keeps the rest of the codebase persistence-agnostic and
 * makes it straightforward to swap Google Sheets for a real database later.
 * <p>
 * <b>Concurrency note:</b> Google Sheets is not a transactional data store. ID generation and
 * appends are guarded by an in-process lock, which is sufficient for a single application
 * instance. If this service is ever horizontally scaled across multiple instances/nodes, ID
 * generation must be moved to something that supports atomic increments (e.g. a small database
 * sequence, Redis INCR, or Google Sheets' own row-locking via Apps Script) to avoid collisions.
 */
@Slf4j
@Service
public class GoogleSheetsService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PAYMENT_ID_PREFIX = "PAY-";
    private static final int PAYMENT_ID_PAD_LENGTH = 6;

    // Guards ID generation + append operations against concurrent requests within this JVM.
    private final Object usersLock = new Object();
    private final Object paymentsLock = new Object();

    private final Sheets sheets;
    private final GoogleSheetsProperties properties;

    public GoogleSheetsService(Sheets sheets, GoogleSheetsProperties properties) {
        this.sheets = sheets;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    public List<User> getUsers() {
        List<List<Object>> rows = readRows(properties.getUsersSheet(), "A2:E");
        List<User> users = new ArrayList<>();
        for (List<Object> row : rows) {
            User user = rowToUser(row);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    public Optional<User> findUserById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return getUsers().stream()
                .filter(u -> id.equals(u.getId()))
                .findFirst();
    }

    public Optional<User> findUserByPhone(String phoneNumber) {
        if (phoneNumber == null) {
            return Optional.empty();
        }
        String normalized = normalizePhone(phoneNumber);
        return getUsers().stream()
                .filter(u -> normalized.equals(normalizePhone(u.getPhoneNumber())))
                .findFirst();
    }

    /**
     * Creates a new user. Caller is responsible for having already checked for duplicates
     * (see UserService) - this method still re-checks under lock to guard against a race
     * between two concurrent requests for the same phone number.
     */
    public User createUser(String name, String phoneNumber, String createdBy) {
        synchronized (usersLock) {
            // Re-check for duplicates while holding the lock.
            Optional<User> existing = findUserByPhone(phoneNumber);
            if (existing.isPresent()) {
                return existing.get();
            }

            long nextId = getUsers().stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L) + 1;

            LocalDateTime now = LocalDateTime.now();
            User user = User.builder()
                    .id(nextId)
                    .name(name)
                    .phoneNumber(phoneNumber)
                    .createdBy(createdBy)
                    .created(now)
                    .build();

            List<Object> row = List.of(
                    user.getId(),
                    nullSafe(user.getName()),
                    nullSafe(user.getPhoneNumber()),
                    nullSafe(user.getCreatedBy()),
                    user.getCreated().format(TIMESTAMP_FORMAT)
            );
            appendRow(properties.getUsersSheet(), row);
            return user;
        }
    }

    // ------------------------------------------------------------------
    // Payments
    // ------------------------------------------------------------------

    public List<Payment> getPayments() {
        List<List<Object>> rows = readRows(properties.getPaymentsSheet(), "A2:I");
        List<Payment> payments = new ArrayList<>();
        for (List<Object> row : rows) {
            Payment payment = rowToPayment(row);
            if (payment != null) {
                payments.add(payment);
            }
        }
        return payments;
    }

    public Optional<Payment> findPaymentById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return getPayments().stream()
                .filter(p -> id.equalsIgnoreCase(p.getId()))
                .findFirst();
    }

    public List<Payment> getPaymentsByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return getPayments().stream()
                .filter(p -> userId.equals(p.getUserId()))
                .toList();
    }

    /**
     * Persists a new payment. The caller supplies everything except id/created, which are
     * generated here to guarantee they are never client-controlled.
     */
    public Payment createPayment(Payment payment) {
        synchronized (paymentsLock) {
            String nextId = generateNextPaymentId();
            LocalDateTime now = LocalDateTime.now();

            Payment toSave = payment.toBuilder()
                    .id(nextId)
                    .created(now)
                    .build();

            List<Object> row = List.of(
                    toSave.getId(),
                    toSave.getUserId(),
                    toSave.isVisitor() ? "YES" : "NO",
                    toSave.getPaymentType().name(),
                    toSave.getPaymentPurpose().name(),
                    toSave.getAmount().toPlainString(),
                    toSave.getCurrency().name(),
                    nullSafe(toSave.getCreatedBy()),
                    toSave.getCreated().format(TIMESTAMP_FORMAT)
            );
            appendRow(properties.getPaymentsSheet(), row);
            return toSave;
        }
    }

    private String generateNextPaymentId() {
        long max = getPayments().stream()
                .map(Payment::getId)
                .filter(Objects::nonNull)
                .map(this::extractPaymentNumber)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        long next = max + 1;
        return PAYMENT_ID_PREFIX + String.format("%0" + PAYMENT_ID_PAD_LENGTH + "d", next);
    }

    private Long extractPaymentNumber(String paymentId) {
        try {
            String numeric = paymentId.replace(PAYMENT_ID_PREFIX, "").trim();
            return Long.parseLong(numeric);
        } catch (NumberFormatException e) {
            log.warn("Could not parse numeric portion of payment id '{}'", paymentId);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Low-level sheet access
    // ------------------------------------------------------------------

    private List<List<Object>> readRows(String sheetName, String a1RangeSuffix) {
        String range = sheetName + "!" + a1RangeSuffix;
        try {
            ValueRange response = sheets.spreadsheets().values()
                    .get(properties.getSpreadsheetId(), range)
                    .execute();
            List<List<Object>> values = response.getValues();
            return values != null ? values : Collections.emptyList();
        } catch (IOException e) {
            throw new GoogleSheetsIntegrationException(
                    "Failed to read data from sheet '" + sheetName + "'", e);
        }
    }

    private void appendRow(String sheetName, List<Object> row) {
        String range = sheetName + "!A1";
        try {
            ValueRange body = new ValueRange().setValues(List.of(row));
            AppendValuesResponse response = sheets.spreadsheets().values()
                    .append(properties.getSpreadsheetId(), range, body)
                    .setValueInputOption("USER_ENTERED")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();
            log.debug("Appended row to '{}': {}", sheetName, response.getUpdates());
        } catch (IOException e) {
            throw new GoogleSheetsIntegrationException(
                    "Failed to write data to sheet '" + sheetName + "'", e);
        }
    }

    // ------------------------------------------------------------------
    // Row <-> Model mapping
    // ------------------------------------------------------------------

    private User rowToUser(List<Object> row) {
        if (row.isEmpty() || cell(row, 0).isBlank()) {
            return null;
        }
        try {
            return User.builder()
                    .id(Long.parseLong(cell(row, 0)))
                    .name(cell(row, 1))
                    .phoneNumber(cell(row, 2))
                    .createdBy(cell(row, 3))
                    .created(parseTimestamp(cell(row, 4)))
                    .build();
        } catch (Exception e) {
            log.warn("Skipping malformed Users row: {} ({})", row, e.getMessage());
            return null;
        }
    }

    private Payment rowToPayment(List<Object> row) {
        if (row.isEmpty() || cell(row, 0).isBlank()) {
            return null;
        }
        try {
            return Payment.builder()
                    .id(cell(row, 0))
                    .userId(Long.parseLong(cell(row, 1)))
                    .visitor("YES".equalsIgnoreCase(cell(row, 2)))
                    .paymentType(PaymentType.valueOf(cell(row, 3)))
                    .paymentPurpose(PaymentPurpose.valueOf(cell(row, 4)))
                    .amount(new BigDecimal(cell(row, 5)))
                    .currency(Currency.valueOf(cell(row, 6)))
                    .createdBy(cell(row, 7))
                    .created(parseTimestamp(cell(row, 8)))
                    .build();
        } catch (Exception e) {
            log.warn("Skipping malformed Payments row: {} ({})", row, e.getMessage());
            return null;
        }
    }

    private String cell(List<Object> row, int index) {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }
        return String.valueOf(row.get(index)).trim();
    }

    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, TIMESTAMP_FORMAT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("[\\s\\-()]", "");
    }
}
