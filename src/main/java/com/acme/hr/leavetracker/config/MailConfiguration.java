package com.acme.hr.leavetracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/** Keeps local development usable when SMTP has not been configured yet. Delivery errors are logged by NotificationService. */
@Configuration
public class MailConfiguration {
    @Bean
    HttpClient googleSheetSyncHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
}
