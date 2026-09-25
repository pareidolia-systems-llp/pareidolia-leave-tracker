package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.api.LeaveRequestView;
import com.acme.hr.leavetracker.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class GoogleSheetSyncService {
    private static final Logger log = LoggerFactory.getLogger(GoogleSheetSyncService.class);
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final HttpClient httpClient;

    public GoogleSheetSyncService(AppProperties properties, ObjectMapper objectMapper, Environment environment,
                                  HttpClient googleSheetSyncHttpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = googleSheetSyncHttpClient;
    }

    public void upsert(LeaveRequestView request) {
        if (environment.matchesProfiles("dev")) {
            log.debug("Google Sheet sync is disabled for the dev profile");
            return;
        }
        if (!isConfigured()) {
            log.warn("Google Sheet sync skipped because app.google-script.web-app-url or shared-secret is not configured");
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "source", "springboot",
                    "sharedSecret", properties.googleScript().sharedSecret(),
                    "event", "UPSERT_LEAVE_REQUEST",
                    "request", request));
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.googleScript().webAppUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Google Sheet sync returned HTTP {} for leave request {}", response.statusCode(), request.id());
            }
        } catch (Exception exception) {
            // The database remains authoritative. Operators can retry a sync after fixing the integration.
            log.error("Google Sheet sync failed for leave request {}", request.id(), exception);
        }
    }

    private boolean isConfigured() {
        return properties.googleScript() != null
                && StringUtils.hasText(properties.googleScript().webAppUrl())
                && StringUtils.hasText(properties.googleScript().sharedSecret());
    }
}
