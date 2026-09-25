package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.config.AppProperties;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Production approval links continue to target the configured Google Apps Script web app. */
@Service
@Profile("!dev")
public class GoogleAppsScriptApprovalLinkBuilder implements ApprovalLinkBuilder {
    private final AppProperties properties;

    public GoogleAppsScriptApprovalLinkBuilder(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<ApprovalLinks> build(LeaveRequest request, String rawApprovalToken) {
        String webAppUrl = properties.googleScript().webAppUrl();
        if (!StringUtils.hasText(webAppUrl)) {
            return Optional.empty();
        }
        return Optional.of(new ApprovalLinks(
                decisionUrl(webAppUrl, "approve", request, rawApprovalToken),
                decisionUrl(webAppUrl, "reject", request, rawApprovalToken)));
    }

    private String decisionUrl(String webAppUrl, String action, LeaveRequest request, String rawApprovalToken) {
        return UriComponentsBuilder.fromUriString(webAppUrl)
                .queryParam("action", action)
                .queryParam("requestId", request.getId())
                .queryParam("token", rawApprovalToken)
                .build().encode(StandardCharsets.UTF_8).toUriString();
    }
}
