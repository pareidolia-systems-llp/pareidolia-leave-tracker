package com.acme.hr.leavetracker.service;

import com.acme.hr.leavetracker.config.AppProperties;
import com.acme.hr.leavetracker.domain.LeaveRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Local-only manager links. This bean is unavailable unless the dev profile is active. */
@Service
@Profile("dev")
public class LocalApprovalLinkBuilder implements ApprovalLinkBuilder {
    private final AppProperties properties;

    public LocalApprovalLinkBuilder(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<ApprovalLinks> build(LeaveRequest request, String rawApprovalToken) {
        if (!StringUtils.hasText(properties.baseUrl())) {
            return Optional.empty();
        }
        return Optional.of(new ApprovalLinks(
                decisionUrl("APPROVE", request, rawApprovalToken),
                decisionUrl("REJECT", request, rawApprovalToken)));
    }

    private String decisionUrl(String action, LeaveRequest request, String rawApprovalToken) {
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/dev/approval")
                .queryParam("action", action)
                .queryParam("requestId", request.getId())
                .queryParam("token", rawApprovalToken)
                .build().encode(StandardCharsets.UTF_8).toUriString();
    }
}
