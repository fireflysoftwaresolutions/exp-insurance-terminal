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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.domain.distributor.branding.sdk.api.AgencyApi;
import io.fireflysoftware.domain.distributor.branding.sdk.api.AgentApi;
import io.fireflysoftware.domain.distributor.branding.sdk.api.DistributorQueryApi;
import io.fireflysoftware.domain.distributor.branding.sdk.model.DistributorDTO;
import io.fireflysoftware.domain.distributor.branding.sdk.model.PaginationResponse;
import io.fireflysoftware.domain.distributor.branding.sdk.model.RegisterDistributorAuditLogCommand;
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
 * Lists distributors, agencies, and agents through domain-distributor-branding
 * and records an attachment in that domain's audit log.
 * The view is read back from the domain after the audit row is stored.
 */
public final class DomainDistributorClient implements DistributorPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ROLES = Set.of("BROKER", "AGENCY", "COLLABORATOR");

    private final DistributorQueryApi distributors;
    private final AgencyApi agencies;
    private final AgentApi agents;
    private final ConfigurationPort configuration;
    private final String channelKey;

    public DomainDistributorClient(ConfigurationPort configuration,
                                   DistributorQueryApi distributors,
                                   AgencyApi agencies,
                                   AgentApi agents,
                                   String channelKey) {
        this.configuration = configuration;
        this.distributors = distributors;
        this.agencies = agencies;
        this.agents = agents;
        this.channelKey = channelKey;
    }

    @Override
    public Flux<DistributorView> list() {
        return configuration.getRequired(channelKey).flatMapMany(channel -> distributors
                .filterDistributors(key())
                .flatMapMany(page -> Flux.fromIterable(content(page)))
                .concatMap(item -> toView(distributor(item), channel)));
    }

    @Override
    public Mono<DistributorView> link(DistributorLink link) {
        RegisterDistributorAuditLogCommand entry = new RegisterDistributorAuditLogCommand();
        entry.setDistributorId(link.distributorId());
        entry.setAction(RegisterDistributorAuditLogCommand.ActionEnum.UPDATED);
        entry.setEntity("STAFF_RECORD");
        entry.setEntityId(link.recordId().toString());
        entry.setAuditMetadata("party=" + link.customerPartyId()
                + ";channel=" + link.channel()
                + ";collaborator=" + link.collaboratorId()
                + ";actor=" + link.employeeId()
                + ";area=" + link.area());
        entry.setUserId(link.employeeId());
        entry.setAuditTimestamp(LocalDateTime.now().withNano(0));
        return distributors.recordAuditLog(link.distributorId(), entry, key())
                .flatMap(saved -> {
                    Object id = saved == null ? null : saved.get("id");
                    if (id == null || id.toString().isBlank()) {
                        return Mono.error(new IllegalStateException("Distributor audit log was not recorded"));
                    }
                    return configuration.getRequired(channelKey)
                            .flatMap(channel -> distributors.getDistributorProfile(link.distributorId(), key())
                                    .flatMap(profile -> toView(profile, channel)));
                });
    }

    private Mono<DistributorView> toView(DistributorDTO item, String channel) {
        UUID id = item.getId();
        if (id == null) {
            return Mono.error(new IllegalStateException("Distributor id was not recorded"));
        }
        String externalCode = item.getExternalCode() == null ? "" : item.getExternalCode();
        String role = ROLES.contains(externalCode.toUpperCase(Locale.ROOT)) ? externalCode.toUpperCase(Locale.ROOT) : "DISTRIBUTOR";
        String name = item.getName() == null ? "" : item.getName();
        return firstId(agencies.listAgencies(id, key()))
                .zipWith(firstId(agents.listAgents(id, key())))
                .map(pair -> new DistributorView(id, name, role, null, channel,
                        pair.getT1().orElse(null), pair.getT2().orElse(null)));
    }

    private static Mono<Optional<UUID>> firstId(Mono<PaginationResponse> page) {
        return page.map(DomainDistributorClient::firstId).defaultIfEmpty(Optional.empty());
    }

    private static Optional<UUID> firstId(PaginationResponse page) {
        for (Object item : content(page)) {
            UUID id = idOf(item);
            if (id != null) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    private static List<Object> content(PaginationResponse page) {
        if (page == null || page.getContent() == null) {
            return List.of();
        }
        return page.getContent();
    }

    private static DistributorDTO distributor(Object item) {
        if (item instanceof DistributorDTO dto) {
            return dto;
        }
        return MAPPER.convertValue(item, DistributorDTO.class);
    }

    private static UUID idOf(Object item) {
        Object raw = null;
        if (item instanceof Map<?, ?> map) {
            raw = map.get("id");
        } else if (item != null) {
            try {
                raw = item.getClass().getMethod("getId").invoke(item);
            } catch (ReflectiveOperationException ignored) {
                raw = null;
            }
        }
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        return UUID.fromString(raw.toString());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
