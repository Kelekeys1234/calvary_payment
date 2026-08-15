package com.example.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "google.sheets")
public class GoogleSheetsProperties {
    private String spreadsheetId;
    private String usersSheet = "Users";
    private String paymentsSheet = "Payments";
    private String credentialsPath = "credentials/service-account.json";
    private String applicationName = "payment-app";
}
