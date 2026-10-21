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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lists distributors, agencies, and agents from core-common-distributor-mgmt and records
 * an attachment in the distributor audit log. Existing distributor tables are left in place.
 */
public final class DomainDistributorClient implements DistributorPort {

    private static final Set<String> ROLES = Set.of("BROKER", "AGENCY", "COLLABORATOR");

    private final WebClient webClient;
    private final ConfigurationPort configuration;
    private final String distributorBaseUrl;
    private final String channelKey;

    public DomainDistributorClient(WebClient webClient, ConfigurationPort configuration, String distributorBaseUrl, String channelKey) {
        this.webClient = webClient;
        this.configuration = configuration;
        this.distributorBaseUrl = trim(distributorBaseUrl);
        this.channelKey = channelKey;
    }

    @Override
    public Flux<DistributorView> list() {
        return configuration.getRequired(channelKey).flatMapMany(channel -> webClient.post()
                .uri(distributorBaseUrl + "/api/v1/distributors/filter")
                .bodyValue(Map.of())
                .exchangeToMono(JsonResponses::body)
                .flatMapMany(body -> Flux.fromIterable(items(body)))
                .concatMap(item -> toView(item, channel)));
    }

    @Override
    public Mono<DistributorView> link(DistributorLink link) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("distributorId", link.distributorId().toString());
        body.put("action", "UPDATED");
        body.put("entity", "STAFF_RECORD");
        body.put("entityId", link.recordId().toString());
        body.put("auditMetadata", "party=" + link.customerPartyId()
                + ";channel=" + link.channel()
                + ";collaborator=" + link.collaboratorId()
                + ";actor=" + link.employeeId()
                + ";area=" + link.area());
        body.put("userId", link.employeeId().toString());
        body.put("auditTimestamp", LocalDateTime.now().toString());
        return webClient.post()
                .uri(distributorBaseUrl + "/api/v1/distributors/" + link.distributorId() + "/audit-logs")
                .bodyValue(body)
                .exchangeToMono(JsonResponses::body)
                .then(Mono.just(new DistributorView(link.distributorId(), "", "DISTRIBUTOR", link.customerPartyId(),
                        link.channel(), null, link.collaboratorId())));
    }

    private Mono<DistributorView> toView(JsonNode item, String channel) {
        UUID id = UUID.fromString(item.path("id").asText());
        String externalCode = item.path("externalCode").asText("");
        String role = ROLES.contains(externalCode.toUpperCase(Locale.ROOT)) ? externalCode.toUpperCase(Locale.ROOT) : "DISTRIBUTOR";
        UUID partyId = uuidOrNull(item.path("partyId").asText(""));
        return firstId(distributorBaseUrl + "/api/v1/distributors/" + id + "/agencies/filter")
                .zipWith(firstId(distributorBaseUrl + "/api/v1/distributors/" + id + "/agents/filter"))
                .map(pair -> new DistributorView(id, item.path("name").asText(""), role, partyId, channel,
                        pair.getT1().orElse(null), pair.getT2().orElse(null)));
    }

    private Mono<Optional<UUID>> firstId(String uri) {
        return webClient.post()
                .uri(uri)
                .bodyValue(Map.of())
                .exchangeToMono(JsonResponses::body)
                .map(body -> {
                    for (JsonNode node : items(body)) {
                        String id = node.path("id").asText("");
                        if (!id.isBlank()) {
                            return Optional.of(UUID.fromString(id));
                        }
                    }
                    return Optional.<UUID>empty();
                });
    }

    private static Iterable<JsonNode> items(JsonNode body) {
        if (body == null || body.isNull()) {
            return java.util.List.of();
        }
        if (body.isArray()) {
            return body;
        }
        for (String field : new String[]{"content", "items", "data", "results"}) {
            if (body.path(field).isArray()) {
                return body.path(field);
            }
        }
        return java.util.List.of();
    }

    private static UUID uuidOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            throw new TerminalDeniedException("PROPERTY_NOT_SET");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
