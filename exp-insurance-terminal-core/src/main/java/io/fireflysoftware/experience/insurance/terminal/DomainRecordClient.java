/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.experience.insurance.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls the domain HTTP APIs. Core SDKs are not on this classpath.
 * Replace {@link SandboxRecordBook} with this class by setting {@code firefly.terminal.adapter=domain}.
 */
public final class DomainRecordClient implements StaffRecordPort {

    private final WebClient webClient;
    private final ConfigurationPort configuration;
    private final Map<String, String> domainBaseUrls;

    public DomainRecordClient(WebClient webClient, ConfigurationPort configuration, Map<String, String> domainBaseUrls) {
        this.webClient = webClient;
        this.configuration = configuration;
        this.domainBaseUrls = Map.copyOf(domainBaseUrls);
    }

    @Override
    public Flux<StaffRecord> list(StaffArea area) {
        return configuration.getRequired(area.recordIndexKey())
                .flatMapMany(csv -> Flux.fromArray(csv.split(",")))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .concatMap(token -> get(area, UUID.fromString(token)));
    }

    @Override
    public Mono<StaffRecord> get(StaffArea area, UUID recordId) {
        return exchange("GET", base(area) + area.itemPath(recordId), null)
                .map(body -> toRecord(area, recordId, body, null));
    }

    @Override
    public Mono<StaffRecord> update(RecordCommand command) {
        StaffArea area = command.area();
        String base = base(area);
        return switch (area.updateMode()) {
            case PUT_DOCUMENT -> getNode(area, command.recordId()).flatMap(current -> {
                Map<String, Object> body = document(current, command);
                return exchange("PUT", base + area.itemPath(command.recordId()), body)
                        .map(written -> toRecord(area, command.recordId(), written, command));
            });
            case ADD_HOLDER -> configuration.getRequired(area.holderRoleKey()).flatMap(roleId -> {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("accountId", command.recordId().toString());
                body.put("partyId", command.customerPartyId().toString());
                body.put("roleInContractId", roleId);
                return exchange("POST", base + "/api/v1/accounts/" + command.recordId() + "/holders", body)
                        .then(get(area, command.recordId()))
                        .map(record -> record.withUpdate(command));
            });
            case CARD_SECURITY -> configuration.getRequired(area.cardSecurityKey()).flatMap(settingId -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("securitySettingId", settingId);
                body.put("enabled", true);
                return exchange("PUT", base + "/api/v1/backoffice/cards/" + command.recordId() + "/security", body)
                        .then(get(area, command.recordId()))
                        .map(record -> record.withUpdate(command));
            });
            case EMPLOYMENT -> {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("applicationId", command.recordId().toString());
                body.put("employer", command.note() == null ? "" : command.note());
                yield exchange("PATCH", base + area.itemPath(command.recordId()) + "/employment-data", body)
                        .then(Mono.fromSupplier(() -> new StaffRecord(command.recordId(), area.name(),
                                command.customerPartyId(), command.actorId(), "OPEN", command.note(),
                                command.distributorId(), command.note())));
            }
        };
    }

    @Override
    public Mono<StaffRecord> close(RecordCommand command) {
        StaffArea area = command.area();
        String base = base(area);
        String reason = command.reason() == null ? "" : command.reason();
        Mono<JsonNode> call = switch (area.closeMode()) {
            case JSON_SUFFIX -> {
                Map<String, String> body = Map.of("statusReason", reason, "status", command.status() == null ? "" : command.status());
                yield exchange("POST", base + area.itemPath(command.recordId()) + "/" + area.closeSuffix(), body);
            }
            case ACCOUNT_QUERY -> exchange("POST", base + "/api/v1/accounts/" + command.recordId()
                    + "/close?reason=" + encode(reason), Map.of());
            case CARD_CANCEL -> exchange("POST", base + "/api/v1/cards/" + command.recordId()
                    + "/cancel?reason=" + encode(reason) + "&cancelledBy=" + encode(command.actorId().toString()), Map.of());
            case APPLICATION_WITHDRAW -> exchange("POST", base + "/api/v1/loan-applications/" + command.recordId() + "/withdraw", Map.of());
        };
        return call.then(Mono.fromSupplier(() -> new StaffRecord(command.recordId(), area.name(),
                command.customerPartyId(), command.actorId(), command.status(), reason, command.distributorId(), command.note())));
    }

    @Override
    public Mono<Void> removeDraft(RecordCommand command, String requiredStatus) {
        if (!command.area().draftRemoval()) {
            return Mono.error(new TerminalDeniedException("REMOVAL_NOT_APPLICABLE"));
        }
        return getNode(command.area(), command.recordId()).flatMap(current -> {
            String status = firstText(current, "status", "agreementStatus", "policyStatus");
            if (status.isBlank()) {
                status = "DRAFT";
            }
            if (!requiredStatus.equals(status)) {
                return Mono.error(new TerminalDeniedException("DRAFT_REQUIRED"));
            }
            return exchange("DELETE", base(command.area()) + command.area().itemPath(command.recordId()), null)
                    .then();
        });
    }

    @Override
    public Mono<StaffRecord> decide(RecordCommand command) {
        if (command.area().decideMode() == DecideMode.NONE) {
            return Mono.error(new TerminalDeniedException("DECISION_NOT_APPLICABLE"));
        }
        String suffix = switch (command.area().decideMode()) {
            case APPROVE_OR_REJECT -> "APPROVE".equalsIgnoreCase(command.decision()) ? "approve" : "reject";
            case DENY -> "deny";
            case DECLINE -> "decline";
            case NONE -> "";
        };
        String status = SandboxRecordBook.decisionStatus(command);
        Map<String, String> body = Map.of("statusReason", command.reason() == null ? "" : command.reason());
        return exchange("POST", base(command.area()) + command.area().itemPath(command.recordId()) + "/" + suffix, body)
                .then(Mono.fromSupplier(() -> new StaffRecord(command.recordId(), command.area().name(),
                        command.customerPartyId(), command.actorId(), status, command.reason(),
                        command.distributorId(), command.note())));
    }

    private Mono<JsonNode> getNode(StaffArea area, UUID recordId) {
        return exchange("GET", base(area) + area.itemPath(recordId), null);
    }

    private Mono<JsonNode> exchange(String method, String uri, Object body) {
        WebClient.RequestBodyUriSpec spec = webClient.method(org.springframework.http.HttpMethod.valueOf(method));
        WebClient.RequestHeadersSpec<?> headers = body == null ? spec.uri(uri) : spec.uri(uri).bodyValue(body);
        return headers.exchangeToMono(response -> {
            if (response.statusCode().isError()) {
                return response.createException().flatMap(Mono::error);
            }
            return JsonResponses.body(response);
        });
    }

    private String base(StaffArea area) {
        String url = domainBaseUrls.get(area.domainKey());
        if (url == null || url.isBlank()) {
            throw new TerminalDeniedException("PROPERTY_NOT_SET");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Map<String, Object> document(JsonNode current, RecordCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        current.fieldNames().forEachRemaining(name -> body.put(name, current.path(name).isContainerNode()
                ? current.path(name).toString()
                : current.path(name).asText()));
        body.put("actorId", command.actorId().toString());
        body.put("customerPartyId", command.customerPartyId().toString());
        if (body.containsKey("lastModifiedBy")) {
            body.put("lastModifiedBy", command.actorId().toString());
        }
        if (body.containsKey("updatedBy")) {
            body.put("updatedBy", command.actorId().toString());
        }
        for (String partyField : new String[]{"partyId", "customerId", "borrowerPartyId", "applicantPartyId"}) {
            if (body.containsKey(partyField)) {
                body.put(partyField, command.customerPartyId().toString());
            }
        }
        if (command.note() != null && body.containsKey("notes")) {
            body.put("notes", command.note());
        }
        if (command.distributorId() != null) {
            body.put("distributorId", command.distributorId().toString());
        }
        return body;
    }

    private static StaffRecord toRecord(StaffArea area, UUID recordId, JsonNode body, RecordCommand command) {
        UUID customer = command != null ? command.customerPartyId() : uuidOrNull(firstText(body, "customerPartyId", "partyId", "customerId"));
        UUID actor = command != null ? command.actorId() : uuidOrNull(firstText(body, "actorId", "lastModifiedBy", "updatedBy"));
        String status = firstText(body, "status", "agreementStatus", "policyStatus", "accountStatus");
        String reference = firstText(body, "reference", "agreementNumber", "policyNumber", "accountNumber", "notes");
        UUID distributor = command != null && command.distributorId() != null
                ? command.distributorId()
                : uuidOrNull(firstText(body, "distributorId"));
        String note = command != null && command.note() != null ? command.note() : firstText(body, "notes", "note");
        return new StaffRecord(recordId, area.name(), customer, actor, status, reference, distributor, note);
    }

    private static UUID uuidOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private static String firstText(JsonNode item, String... names) {
        if (item == null) {
            return "";
        }
        for (String name : names) {
            String value = item.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
