package com.example.payment.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Builds the Google Sheets API client bean.
 * <p>
 * Credentials are NEVER hardcoded. They are loaded from a service account JSON file whose
 * location is provided via the {@code google.sheets.credentials-path} property (which itself
 * defaults to the standard {@code GOOGLE_APPLICATION_CREDENTIALS} environment variable).
 * The path may point to a plain filesystem location or a classpath resource
 * (e.g. {@code classpath:credentials/service-account.json}).
 */
@Slf4j
@Configuration
public class GoogleSheetsConfig {

    private final GoogleSheetsProperties properties;

    public GoogleSheetsConfig(GoogleSheetsProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Sheets sheetsClient() throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(normalizeLocation(properties.getCredentialsPath()));

        if (!resource.exists()) {
            throw new IOException(
                    "Google service account credentials file not found at '" + properties.getCredentialsPath()
                            + "'. Set GOOGLE_APPLICATION_CREDENTIALS (or google.sheets.credentials-path) to point "
                            + "to your service account JSON file. See the README for setup instructions.");
        }

        GoogleCredentials credentials;
        try (InputStream in = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        }

        return new Sheets.Builder(httpTransport, GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(properties.getApplicationName())
                .build();
    }

    private String normalizeLocation(String path) {
        if (path.startsWith("classpath:") || path.startsWith("file:")) {
            return path;
        }
        return "file:" + path;
    }
}
