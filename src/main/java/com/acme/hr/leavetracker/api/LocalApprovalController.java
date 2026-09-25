package com.acme.hr.leavetracker.api;

import com.acme.hr.leavetracker.service.IntegrationSignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Local replacement for the Google Apps Script confirmation page. It is deliberately available
 * only in the dev profile and keeps the HMAC secret on the server.
 */
@Controller
@Profile("dev")
@RequestMapping("/dev/approval")
public class LocalApprovalController {
    private static final String APPROVE = "APPROVE";
    private static final String REJECT = "REJECT";

    private final IntegrationSignatureService signatureService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public LocalApprovalController(IntegrationSignatureService signatureService, ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmPage(@RequestParam String action, @RequestParam String requestId,
                                              @RequestParam String token) {
        String normalizedAction = normalizeAction(action);
        if (normalizedAction == null || !isUuid(requestId) || !StringUtils.hasText(token)) {
            return page(400, "Invalid leave action", "This approval link is incomplete or invalid.");
        }

        String verb = APPROVE.equals(normalizedAction) ? "Approve" : "Reject";
        String color = APPROVE.equals(normalizedAction) ? "#16803c" : "#b42318";
        String body = "<p>You are about to <strong>" + verb.toLowerCase(Locale.ROOT) + "</strong> a leave request.</p>"
                + "<form method=\"post\" action=\"/dev/approval/confirm\">"
                + hidden("action", normalizedAction) + hidden("requestId", requestId) + hidden("token", token)
                + "<label for=\"managerComment\">Comment (optional)</label>"
                + "<textarea id=\"managerComment\" name=\"managerComment\" maxlength=\"1000\" rows=\"4\" "
                + "placeholder=\"Add a note for the employee\"></textarea>"
                + "<button type=\"submit\" style=\"background:" + color + "\">Confirm " + verb + "</button>"
                + "</form><p class=\"hint\">This confirmation prevents mail-security scanners from accidentally deciding leave.</p>";
        return page(200, "Confirm leave decision", body);
    }

    @PostMapping(path = "/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmDecision(@RequestParam String action, @RequestParam UUID requestId,
                                                  @RequestParam String token,
                                                  @RequestParam(required = false) String managerComment,
                                                  HttpServletRequest servletRequest) {
        String normalizedAction = normalizeAction(action);
        if (normalizedAction == null || !StringUtils.hasText(token)) {
            return page(400, "Invalid leave action", "The submitted information was incomplete.");
        }
        if (!signatureService.isConfigured()) {
            return page(503, "Local approval unavailable", "The development shared secret is not configured.");
        }

        String cleanComment = managerComment == null ? "" : managerComment.trim();
        GoogleDecision decision = new GoogleDecision(requestId, normalizedAction, token, cleanComment);
        String signature = signatureService.signDecision(requestId.toString(), normalizedAction, token, cleanComment);
        try {
            HttpRequest endpointRequest = HttpRequest.newBuilder(decisionEndpointUri(servletRequest))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Google-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(decision), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(endpointRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String outcome = APPROVE.equals(normalizedAction) ? "approved" : "rejected";
                return page(200, "Decision saved", "The leave request has been <strong>" + outcome + "</strong>."
                        + " The employee notification has been queued.");
            }
            return page(response.statusCode(), "Decision not saved",
                    "The request could not be processed. It may have expired or already been decided.");
        } catch (Exception exception) {
            // Do not log the request body or URL: each contains the one-time approval token.
            return page(502, "Connection problem", "Could not reach the local leave tracker decision endpoint.");
        }
    }

    private URI decisionEndpointUri(HttpServletRequest request) {
        return UriComponentsBuilder.newInstance()
                .scheme(request.getScheme())
                .host(request.getServerName())
                .port(request.getServerPort())
                .path("/api/integrations/google/decisions")
                .build()
                .toUri();
    }

    private String normalizeAction(String action) {
        if (action == null) {
            return null;
        }
        return switch (action.toUpperCase(Locale.ROOT)) {
            case APPROVE -> APPROVE;
            case REJECT -> REJECT;
            default -> null;
        };
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ResponseEntity<String> page(int status, String title, String body) {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1\"><meta name=\"referrer\" content=\"no-referrer\">"
                + "<title>" + escapeHtml(title) + "</title><style>body{font-family:Arial,sans-serif;background:#f5f7f6;"
                + "color:#17212b;margin:0;padding:24px}.card{max-width:580px;margin:48px auto;background:#fff;padding:32px;"
                + "border-radius:12px;box-shadow:0 8px 24px #00000012}h1{margin-top:0}label{display:block;font-weight:bold;"
                + "margin:18px 0 7px}textarea{box-sizing:border-box;width:100%;padding:10px;border:1px solid #bbc7c0;"
                + "border-radius:6px;font:inherit}button{border:0;border-radius:6px;color:white;padding:11px 16px;font-weight:bold;"
                + "font-size:1rem;margin-top:18px;cursor:pointer}.hint{color:#607068;font-size:.88rem;margin-top:20px}</style>"
                + "</head><body><main class=\"card\"><h1>" + escapeHtml(title) + "</h1>" + body
                + "</main></body></html>";
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + escapeHtml(name) + "\" value=\"" + escapeHtml(value) + "\">";
    }

    private String escapeHtml(String value) {
        return org.springframework.web.util.HtmlUtils.htmlEscape(value);
    }
}
