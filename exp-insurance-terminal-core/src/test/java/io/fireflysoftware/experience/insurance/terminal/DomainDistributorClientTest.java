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

import io.fireflysoftware.domain.distributor.branding.sdk.api.AgencyApi;
import io.fireflysoftware.domain.distributor.branding.sdk.api.AgentApi;
import io.fireflysoftware.domain.distributor.branding.sdk.api.DistributorQueryApi;
import io.fireflysoftware.domain.distributor.branding.sdk.model.DistributorDTO;
import io.fireflysoftware.domain.distributor.branding.sdk.model.PaginationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainDistributorClientTest {

    private DistributorQueryApi distributors;
    private AgencyApi agencies;
    private AgentApi agents;
    private DomainDistributorClient client;

    @BeforeEach
    void setUp() {
        distributors = mock(DistributorQueryApi.class);
        agencies = mock(AgencyApi.class);
        agents = mock(AgentApi.class);
        when(agencies.listAgencies(any(), any())).thenReturn(Mono.just(new PaginationResponse()));
        when(agents.listAgents(any(), any())).thenReturn(Mono.just(new PaginationResponse()));
        ConfigurationPort configuration = new SandboxConfigurationStore(
                InsuranceSamples.properties(), InsuranceSamples.currencies(), InsuranceSamples.countries());
        client = new DomainDistributorClient(configuration, distributors, agencies, agents,
                InsuranceArea.POLICY.channelKey());
    }

    @Test
    void listReadsDistributorsFromTheDomainSdk() {
        UUID id = UUID.fromString("20000000-0000-4000-8000-000000000021");
        DistributorDTO dto = new DistributorDTO().id(id).name("Harbor Brokerage").externalCode("BROKER");
        PaginationResponse page = new PaginationResponse().addContentItem(dto);
        when(distributors.filterDistributors(any())).thenReturn(Mono.just(page));

        DistributorView view = client.list().blockFirst(Duration.ofSeconds(2));

        verify(distributors).filterDistributors(any());
        assertEquals(id, view.distributorId());
        assertEquals("Harbor Brokerage", view.name());
        assertEquals("BROKER", view.role());
        assertEquals("AGENCY", view.channel());
        assertNull(view.partyId());
    }

    @Test
    void linkReadsTheViewFromTheDistributorProfile() {
        UUID distributorId = UUID.fromString("20000000-0000-4000-8000-000000000022");
        UUID agencyId = UUID.fromString("20000000-0000-4000-8000-000000000023");
        UUID agentId = UUID.fromString("20000000-0000-4000-8000-000000000024");
        UUID requestedCollaborator = UUID.fromString("20000000-0000-4000-8000-000000000025");
        UUID customerPartyId = UUID.fromString("20000000-0000-4000-8000-000000000026");
        DistributorDTO profile = new DistributorDTO().id(distributorId).name("Harbor Brokerage").externalCode("AGENCY");
        PaginationResponse agencyPage = new PaginationResponse().addContentItem(Map.of("id", agencyId.toString()));
        PaginationResponse agentPage = new PaginationResponse().addContentItem(Map.of("id", agentId.toString()));
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("id", distributorId.toString());
        when(distributors.recordAuditLog(any(), any(), any())).thenReturn(Mono.just(saved));
        when(distributors.getDistributorProfile(any(), any())).thenReturn(Mono.just(profile));
        when(agencies.listAgencies(any(), any())).thenReturn(Mono.just(agencyPage));
        when(agents.listAgents(any(), any())).thenReturn(Mono.just(agentPage));

        DistributorLink link = new DistributorLink(distributorId, UUID.randomUUID(), customerPartyId,
                UUID.randomUUID(), "REQUEST-CHANNEL", requestedCollaborator, "POLICY");
        DistributorView view = client.link(link).block(Duration.ofSeconds(2));

        verify(distributors).recordAuditLog(any(), any(), any());
        verify(distributors).getDistributorProfile(any(), any());
        assertEquals("Harbor Brokerage", view.name());
        assertEquals("AGENCY", view.role());
        assertEquals("AGENCY", view.channel());
        assertEquals(agencyId, view.agencyId());
        assertEquals(agentId, view.collaboratorId());
        assertNull(view.partyId());
    }

    @Test
    void linkDoesNotInventAViewWhenTheAuditRowIsMissing() {
        when(distributors.recordAuditLog(any(), any(), any())).thenReturn(Mono.just(new LinkedHashMap<>()));
        DistributorLink link = new DistributorLink(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "REQUEST-CHANNEL", UUID.randomUUID(), "POLICY");

        assertThrows(IllegalStateException.class, () -> client.link(link).block(Duration.ofSeconds(2)));
        verify(distributors, never()).getDistributorProfile(any(), any());
    }
}
