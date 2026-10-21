/*
 * Copyright 2026 Firefly Software Foundation
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
import io.fireflysoftware.domain.configuration.sdk.model.EnvironmentConfigPage;
import io.fireflysoftware.domain.referencedata.sdk.api.MastersApi;
import io.fireflysoftware.domain.referencedata.sdk.model.CatalogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainConfigurationClientTest {

    @Mock
    private EnvironmentConfigApi configuration;
    @Mock
    private MastersApi masters;

    @Test
    void readsAndWritesConfigurationAndReferenceDataThroughTheDomainSdks() {
        DomainConfigurationClient client = new DomainConfigurationClient(configuration, masters);
        UUID employeeId = UUID.randomUUID();
        UUID countryId = UUID.randomUUID();
        when(configuration.filterConfigs(eq(0), eq(200), any())).thenReturn(Mono.just(
                new EnvironmentConfigPage().addContentItem(
                        new EnvironmentConfig().configKey("insurance.catalog").configValue("sandbox"))));
        when(configuration.createConfig(any())).thenReturn(Mono.just(new EnvironmentConfig().configValue("live")));
        when(masters.listMaster("COUNTRY")).thenReturn(Flux.just(
                new CatalogEntry().id(countryId).code("ES").label("Spain")));

        assertThat(client.getRequired("insurance.catalog").block()).isEqualTo("sandbox");
        assertThat(client.put("insurance.catalog", "live", employeeId).block()).isEqualTo("live");
        MasterRecord row = client.masters("COUNTRY").blockFirst();

        assertThat(row.code()).isEqualTo("ES");
        assertThat(row.name()).isEqualTo("Spain");
        verify(configuration).filterConfigs(eq(0), eq(200), eq(Map.of()));
        verify(configuration).createConfig(any());
        verify(masters).listMaster("COUNTRY");
    }
}
