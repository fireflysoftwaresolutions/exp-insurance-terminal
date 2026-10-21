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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sandbox book of domain records. Updates, closes, and draft removal change the book.
 * A deployment replaces this class with {@link DomainRecordClient}.
 */
public final class SandboxRecordBook implements StaffRecordPort {

    private final Map<UUID, StaffRecord> records = new ConcurrentHashMap<>();
    private final ConfigurationPort configuration;

    public SandboxRecordBook(ConfigurationPort configuration, ListSeed seed) {
        this.configuration = configuration;
        seed.records().forEach(record -> records.put(record.id(), record));
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
        StaffRecord record = records.get(recordId);
        if (record == null || !area.name().equals(record.area())) {
            return Mono.error(new TerminalDeniedException("RECORD_NOT_FOUND"));
        }
        return Mono.just(record);
    }

    @Override
    public Mono<StaffRecord> update(RecordCommand command) {
        return mutate(command, current -> {
            if (!current.customerPartyId().equals(command.customerPartyId())) {
                return Mono.error(new TerminalDeniedException("PARTY_MISMATCH"));
            }
            StaffRecord updated = current.withUpdate(command);
            records.put(updated.id(), updated);
            return Mono.just(updated);
        });
    }

    @Override
    public Mono<StaffRecord> close(RecordCommand command) {
        return mutate(command, current -> {
            if (!current.customerPartyId().equals(command.customerPartyId())) {
                return Mono.error(new TerminalDeniedException("PARTY_MISMATCH"));
            }
            StaffRecord updated = current.withStatus(command.status(), command.actorId());
            records.put(updated.id(), updated);
            return Mono.just(updated);
        });
    }

    @Override
    public Mono<Void> removeDraft(RecordCommand command, String requiredStatus) {
        if (!command.area().draftRemoval()) {
            return Mono.error(new TerminalDeniedException("REMOVAL_NOT_APPLICABLE"));
        }
        return get(command.area(), command.recordId()).flatMap(current -> {
            if (!current.customerPartyId().equals(command.customerPartyId())) {
                return Mono.error(new TerminalDeniedException("PARTY_MISMATCH"));
            }
            if (current.status() == null || !current.status().equals(requiredStatus)) {
                return Mono.error(new TerminalDeniedException("DRAFT_REQUIRED"));
            }
            records.remove(current.id());
            return Mono.empty();
        });
    }

    @Override
    public Mono<StaffRecord> decide(RecordCommand command) {
        if (command.area().decideMode() == DecideMode.NONE) {
            return Mono.error(new TerminalDeniedException("DECISION_NOT_APPLICABLE"));
        }
        String status = decisionStatus(command);
        return mutate(command, current -> {
            if (!current.customerPartyId().equals(command.customerPartyId())) {
                return Mono.error(new TerminalDeniedException("PARTY_MISMATCH"));
            }
            StaffRecord updated = current.withStatus(status, command.actorId());
            records.put(updated.id(), updated);
            return Mono.just(updated);
        });
    }

    public static String decisionStatus(RecordCommand command) {
        return switch (command.area().decideMode()) {
            case APPROVE_OR_REJECT -> "APPROVE".equalsIgnoreCase(command.decision()) ? "APPROVED" : "REJECTED";
            case DENY -> "DENIED";
            case DECLINE -> "DECLINED";
            case NONE -> "";
        };
    }

    private Mono<StaffRecord> mutate(RecordCommand command, java.util.function.Function<StaffRecord, Mono<StaffRecord>> change) {
        return get(command.area(), command.recordId()).flatMap(change);
    }

    public record ListSeed(java.util.List<StaffRecord> records) {
    }
}
