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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Finished audit of impersonation. Both identifiers are kept on each entry.
 * A deployment that must ship the same entries elsewhere replaces this class.
 */
public final class InMemoryAuditLog implements AuditPort {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public Mono<AuditEntry> append(AuditEntry entry) {
        entries.add(entry);
        return Mono.just(entry);
    }

    @Override
    public Flux<AuditEntry> list() {
        return Flux.fromIterable(List.copyOf(entries));
    }
}
