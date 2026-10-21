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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.core.distributor.sdk.api.DistributorAgenciesApi;
import io.fireflysoftware.core.distributor.sdk.api.DistributorAgentsApi;
import io.fireflysoftware.core.distributor.sdk.api.DistributorApi;
import io.fireflysoftware.core.distributor.sdk.api.DistributorAuditLogApi;
import io.fireflysoftware.core.distributor.sdk.invoker.ApiClient;
import io.fireflysoftware.core.distributor.sdk.model.DistributorAuditLogDTO;
import io.fireflysoftware.core.distributor.sdk.model.FilterRequestDistributorAgencyDTO;
import io.fireflysoftware.core.distributor.sdk.model.FilterRequestDistributorAgentDTO;
import io.fireflysoftware.core.distributor.sdk.model.FilterRequestDistributorDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lists distributors, agencies, and agents from core-common-distributor-mgmt and records
 * an attachment in the distributor audit log.
 */
public final class DomainDistributorClient implements DistributorPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ROLES = Set.of("BROKER", "AGENCY", "COLLABORATOR");

    private final DistributorApi distributors;
    private final DistributorAgenciesApi agencies;
    private final DistributorAgentsApi agents;
    private final DistributorAuditLogApi audit;
    private final ConfigurationPort configuration;
    private final String channelKey;

    public DomainDistributorClient(ConfigurationPort configuration, String distributorBaseUrl, String channelKey) {
        ApiClient client = new ApiClient();
        client.setBasePath(trim(distributorBaseUrl));
        this.distributors = new DistributorApi(client);
        this.agencies = new DistributorAgenciesApi(client);
        this.agents = new DistributorAgentsApi(client);
        this.audit = new DistributorAuditLogApi(client);
        this.configuration = configuration;
        this.channelKey = channelKey;
    }

    @Override
    public Flux<DistributorView> list() {
        return configuration.getRequired(channelKey).flatMapMany(channel -> distributors
                .filterDistributorsWithResponseSpec(new FilterRequestDistributorDTO(), key())
                .bodyToMono(String.class)
                .flatMapMany(raw -> Flux.fromIterable(items(read(raw))))
                .concatMap(item -> toView(item, channel)));
    }

    @Override
    public Mono<DistributorView> link(DistributorLink link) {
        DistributorAuditLogDTO entry = new DistributorAuditLogDTO();
        entry.setDistributorId(link.distributorId());
        entry.setAction(DistributorAuditLogDTO.ActionEnum.UPDATED);
        entry.setEntity("STAFF_RECORD");
        entry.setEntityId(link.recordId().toString());
        entry.setAuditMetadata("party=" + link.customerPartyId()
                + ";channel=" + link.channel()
                + ";collaborator=" + link.collaboratorId()
                + ";actor=" + link.employeeId()
                + ";area=" + link.area());
        entry.setUserId(link.employeeId());
        entry.setAuditTimestamp(LocalDateTime.now().withNano(0));
        return audit.createDistributorAuditLog(link.distributorId(), entry, key())
                .thenReturn(new DistributorView(link.distributorId(), "", "DISTRIBUTOR", link.customerPartyId(),
                        link.channel(), null, link.collaboratorId()));
    }

    private Mono<DistributorView> toView(JsonNode item, String channel) {
        UUID id = UUID.fromString(item.path("id").asText());
        String externalCode = item.path("externalCode").asText("");
        String role = ROLES.contains(externalCode.toUpperCase(Locale.ROOT)) ? externalCode.toUpperCase(Locale.ROOT) : "DISTRIBUTOR";
        UUID partyId = uuidOrNull(item.path("partyId").asText(""));
        return firstId(agencies.filter6(id, new FilterRequestDistributorAgencyDTO(), key()))
                .zipWith(firstId(agents.filter4(id, new FilterRequestDistributorAgentDTO(), key())))
                .map(pair -> new DistributorView(id, item.path("name").asText(""), role, partyId, channel,
                        pair.getT1().orElse(null), pair.getT2().orElse(null)));
    }

    private static Mono<Optional<UUID>> firstId(Mono<io.fireflysoftware.core.distributor.sdk.model.PaginationResponse> page) {
        return page.map(response -> firstId(response.getContent())).defaultIfEmpty(Optional.empty());
    }

    private static Optional<UUID> firstId(List<Object> content) {
        if (content == null) {
            return Optional.empty();
        }
        for (Object item : content) {
            String id = null;
            if (item instanceof Map<?, ?> map && map.get("id") != null) {
                id = map.get("id").toString();
            } else if (item != null) {
                try {
                    Object value = item.getClass().getMethod("getId").invoke(item);
                    id = value == null ? null : value.toString();
                } catch (ReflectiveOperationException ignored) {
                    id = null;
                }
            }
            if (id != null && !id.isBlank()) {
                return Optional.of(UUID.fromString(id));
            }
        }
        return Optional.empty();
    }

    private static Iterable<JsonNode> items(JsonNode body) {
        if (body == null || body.isNull()) {
            return List.of();
        }
        if (body.isArray()) {
            return body;
        }
        for (String field : new String[]{"content", "items", "data", "results"}) {
            if (body.path(field).isArray()) {
                return body.path(field);
            }
        }
        return List.of();
    }

    private static JsonNode read(String raw) {
        try {
            return raw == null || raw.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("DOMAIN_RESPONSE_UNREADABLE");
        }
    }

    private static UUID uuidOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return UUID.fromString(text);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            throw new TerminalDeniedException("PROPERTY_NOT_SET");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
