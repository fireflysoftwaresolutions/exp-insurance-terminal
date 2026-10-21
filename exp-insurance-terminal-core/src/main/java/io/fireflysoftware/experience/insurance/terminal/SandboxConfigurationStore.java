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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for core-common-config-mgmt environment configuration and for
 * reference master data. The terminal process does not open a database for these values.
 * An installer sets the same keys in environment configuration. Set
 * {@code firefly.terminal.adapter=domain} to use {@link DomainConfigurationClient}.
 */
public final class SandboxConfigurationStore implements ConfigurationPort {

    private final Map<String, String> properties = new ConcurrentHashMap<>();
    private final List<MasterRecord> currencies;
    private final List<MasterRecord> countries;

    public SandboxConfigurationStore(Map<String, String> seeded, List<MasterRecord> currencies, List<MasterRecord> countries) {
        this.properties.putAll(seeded);
        this.currencies = List.copyOf(currencies);
        this.countries = List.copyOf(countries);
    }

    @Override
    public Mono<String> getRequired(String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            return Mono.error(new TerminalDeniedException("PROPERTY_NOT_SET"));
        }
        return Mono.just(value);
    }

    @Override
    public Mono<String> put(String key, String value, UUID employeeId) {
        if (key == null || key.isBlank() || value == null || employeeId == null) {
            return Mono.error(new TerminalDeniedException("REQUEST_INCOMPLETE"));
        }
        properties.put(key, value);
        return Mono.just(value);
    }

    @Override
    public Flux<MasterRecord> masters(String type) {
        if ("CURRENCY".equalsIgnoreCase(type)) {
            return Flux.fromIterable(currencies);
        }
        if ("COUNTRY".equalsIgnoreCase(type)) {
            return Flux.fromIterable(countries);
        }
        return Flux.error(new TerminalDeniedException("MASTER_TYPE_UNKNOWN"));
    }
}
