package com.acme.hr.leavetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        String adminApiKey,
        long approvalTokenValidityHours,
        Mail mail,
        GoogleScript googleScript
) {
    public record Mail(String from) { }

    public record GoogleScript(String webAppUrl, String sharedSecret) { }
}
