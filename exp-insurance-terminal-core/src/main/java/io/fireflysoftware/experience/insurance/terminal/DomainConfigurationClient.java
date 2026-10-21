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
import io.fireflysoftware.domain.configuration.sdk.model.EnvironmentConfig;
import io.fireflysoftware.domain.referencedata.sdk.api.MastersApi;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration and reference data go through the domain SDKs.
 * Property values are not kept in this process.
 */
public final class DomainConfigurationClient implements ConfigurationPort {

    private final EnvironmentConfigApi configuration;
    private final MastersApi masters;

    public DomainConfigurationClient(EnvironmentConfigApi configuration, MastersApi masters) {
        this.configuration = configuration;
        this.masters = masters;
    }

    @Override
    public Mono<String> getRequired(String key) {
        return denied(configuration.filterConfigs(0, 200, Map.of())
                .flatMap(page -> {
                    List<EnvironmentConfig> rows = page.getContent() == null ? List.of() : page.getContent();
                    return rows.stream()
                            .filter(row -> key.equals(row.getConfigKey()))
                            .map(EnvironmentConfig::getConfigValue)
                            .filter(value -> value != null && !value.isBlank())
                            .findFirst()
                            .map(Mono::just)
                            .orElseGet(() -> Mono.error(new IllegalStateException("PROPERTY_NOT_SET")));
                }));
    }

    @Override
    public Mono<String> put(String key, String value, UUID employeeId) {
        EnvironmentConfig body = new EnvironmentConfig()
                .environmentName("sandbox")
                .configKey(key)
                .configValue(value)
                .configType("STRING")
                .isSecret(false)
                .description("Staff terminal property set by " + employeeId);
        return denied(configuration.createConfig(body)
                .map(saved -> {
                    String stored = saved.getConfigValue();
                    return stored == null || stored.isBlank() ? value : stored;
                }));
    }

    @Override
    public Flux<MasterRecord> masters(String type) {
        return masters.listMaster(type)
                .map(row -> new MasterRecord(type, row.getId(), row.getCode(), row.getLabel() == null ? "" : row.getLabel()))
                .onErrorMap(IllegalStateException.class, exception -> new TerminalDeniedException(exception.getMessage()));
    }

    private static <T> Mono<T> denied(Mono<T> source) {
        return source.onErrorMap(IllegalStateException.class, exception -> new TerminalDeniedException(exception.getMessage()));
    }
}
