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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Staff terminal. Identity and access are decided by domain-core-security-center.
 * Customer-scoped commands call impersonation, which posts IMPERSONATE, before the domain port.
 */
public final class StaffTerminal {

    private final IdentityPort identity;
    private final AccessPort access;
    private final ImpersonationPort impersonation;
    private final ConfigurationPort configuration;
    private final DistributorPort distributors;
    private final StaffRecordPort records;
    private final AuditPort audit;
    private final String anchorType;
    private final String anchorResourceId;
    private final String propertyPrefix;

    public StaffTerminal(IdentityPort identity, AccessPort access, ImpersonationPort impersonation,
                         ConfigurationPort configuration, DistributorPort distributors, StaffRecordPort records,
                         AuditPort audit, String anchorType, String anchorResourceId, String propertyPrefix) {
        this.identity = identity;
        this.access = access;
        this.impersonation = impersonation;
        this.configuration = configuration;
        this.distributors = distributors;
        this.records = records;
        this.audit = audit;
        this.anchorType = anchorType;
        this.anchorResourceId = anchorResourceId;
        this.propertyPrefix = propertyPrefix;
    }

    public Mono<StaffSession> signIn(String username) {
        return identity.authenticate(username)
                .switchIfEmpty(Mono.error(new TerminalDeniedException("ACTOR_UNKNOWN")))
                .flatMap(subject -> access.check(new AccessCheck(subject.actorId(), "ADMINISTER", anchorType, anchorResourceId))
                        .flatMap(outcome -> outcome.allowed()
                                ? Mono.just(new StaffSession(subject.actorId(), subject.provider()))
                                : Mono.error(new TerminalDeniedException(outcome.reasonCode()))));
    }

    public Flux<StaffRecord> list(UUID actorId, StaffArea area) {
        return requireStaff(actorId, area, area.primaryId()).thenMany(Flux.defer(() -> records.list(area)));
    }

    public Mono<StaffRecord> open(UUID actorId, StaffArea area, UUID recordId) {
        return requireStaff(actorId, area, recordId).then(Mono.defer(() -> records.get(area, recordId)));
    }

    public Mono<StaffRecord> update(UUID actorId, RecordCommand command) {
        return customerCommand(actorId, command, "UPDATE", () -> records.update(command.withActor(actorId)));
    }

    public Mono<StaffRecord> close(UUID actorId, RecordCommand command) {
        return customerCommand(actorId, command, "CLOSE", () -> configuration.getRequired(command.area().closeStatusKey())
                .flatMap(status -> records.close(command.withActor(actorId).withStatus(status))));
    }

    public Mono<Void> removeDraft(UUID actorId, RecordCommand command) {
        if (!command.area().draftRemoval()) {
            return Mono.error(new TerminalDeniedException("REMOVAL_NOT_APPLICABLE"));
        }
        return customerCommand(actorId, command, "CLOSE", () -> configuration.getRequired(command.area().removeStatusKey())
                .flatMap(required -> records.removeDraft(command.withActor(actorId), required)));
    }

    public Mono<StaffRecord> decide(UUID actorId, RecordCommand command) {
        if (command.area().decideMode() == DecideMode.NONE) {
            return Mono.error(new TerminalDeniedException("DECISION_NOT_APPLICABLE"));
        }
        return customerCommand(actorId, command, "DECIDE", () -> records.decide(command.withActor(actorId)));
    }

    public Mono<StaffRecord> attachDistributor(UUID actorId, RecordCommand command) {
        return customerCommand(actorId, command, "UPDATE", () -> configuration.getRequired(propertyPrefix + ".distributor.channel")
                .flatMap(configuredChannel -> {
                    String channel = command.channel() == null || command.channel().isBlank() ? configuredChannel : command.channel();
                    DistributorLink link = new DistributorLink(command.distributorId(), command.recordId(),
                            command.customerPartyId(), actorId, channel, command.collaboratorId(), command.area().name());
                    return distributors.link(link).then(records.update(command.withActor(actorId)));
                }));
    }

    public Mono<String> putProperty(UUID actorId, String key, String value) {
        return requireAnchor(actorId).then(Mono.defer(() -> configuration.put(key, value, actorId)));
    }

    public Mono<String> getProperty(UUID actorId, String key) {
        return requireAnchor(actorId).then(Mono.defer(() -> configuration.getRequired(key)));
    }

    public Flux<MasterRecord> masters(UUID actorId, String type) {
        return requireAnchor(actorId).thenMany(Flux.defer(() -> configuration.masters(type)));
    }

    public Flux<DistributorView> distributors(UUID actorId) {
        return requireAnchor(actorId).thenMany(Flux.defer(distributors::list));
    }

    public Flux<AuditEntry> audit(UUID actorId) {
        return requireAnchor(actorId).thenMany(Flux.defer(audit::list));
    }

    private <T> Mono<T> customerCommand(UUID actorId, RecordCommand command, String action, Supplier<Mono<T>> domainCall) {
        return requireStaff(actorId, command.area(), command.recordId())
                .then(Mono.defer(() -> impersonation.impersonate(actorId, command.customerPartyId(), command.reason(),
                        action, command.area().name(), command.recordId())))
                .then(Mono.defer(domainCall));
    }

    private Mono<Void> requireStaff(UUID actorId, StaffArea area, UUID resourceId) {
        String resource = resourceId == null ? area.primaryId().toString() : resourceId.toString();
        return require(actorId, area.accessType(), resource);
    }

    private Mono<Void> requireAnchor(UUID actorId) {
        return require(actorId, anchorType, anchorResourceId);
    }

    private Mono<Void> require(UUID actorId, String resourceType, String resourceId) {
        if (actorId == null) {
            return Mono.error(new TerminalDeniedException("REQUEST_INCOMPLETE"));
        }
        return identity.resolve(actorId.toString())
                .switchIfEmpty(Mono.error(new TerminalDeniedException("ACTOR_UNKNOWN")))
                .flatMap(subject -> access.check(new AccessCheck(subject.actorId(), "ADMINISTER", resourceType, resourceId)))
                .flatMap(outcome -> outcome.allowed()
                        ? Mono.empty()
                        : Mono.error(new TerminalDeniedException(outcome.reasonCode())));
    }
}
