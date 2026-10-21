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

import io.fireflysoftware.domain.configuration.sdk.api.EnvironmentConfigApi;
import io.fireflysoftware.domain.referencedata.sdk.api.ReferenceMastersApi;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Configuration and reference data go through the domain SDKs.
 * Property values are not kept in this process.
 */
public final class DomainConfigurationClient implements ConfigurationPort {

    private final EnvironmentConfigApi configuration;
    private final ReferenceMastersApi masters;

    public DomainConfigurationClient(EnvironmentConfigApi configuration, ReferenceMastersApi masters) {
        this.configuration = configuration;
        this.masters = masters;
    }

    @Override
    public Mono<String> getRequired(String key) {
        return denied(configuration.getRequired(key));
    }

    @Override
    public Mono<String> put(String key, String value, UUID employeeId) {
        return denied(configuration.put(key, value, employeeId));
    }

    @Override
    public Flux<MasterRecord> masters(String type) {
        return masters.list(type)
                .map(row -> new MasterRecord(row.type(), row.id(), row.code(), row.name()))
                .onErrorMap(IllegalStateException.class, exception -> new TerminalDeniedException(exception.getMessage()));
    }

    private static <T> Mono<T> denied(Mono<T> source) {
        return source.onErrorMap(IllegalStateException.class, exception -> new TerminalDeniedException(exception.getMessage()));
    }
}
