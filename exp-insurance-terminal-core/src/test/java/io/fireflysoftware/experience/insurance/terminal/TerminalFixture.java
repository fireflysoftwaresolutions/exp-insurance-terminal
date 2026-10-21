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

import org.springframework.web.reactive.function.client.WebClient;

public final class TerminalFixture {

    private TerminalFixture() {
    }

    public static StaffTerminal insurance(String securityCenterBaseUrl) {
        WebClient security = WebClient.builder().baseUrl(securityCenterBaseUrl).build();
        IdentityPort identity = new HttpIdentityAdapter(security);
        AccessPort access = new HttpAccessAdapter(security);
        AuditPort audit = new InMemoryAuditLog();
        ConfigurationPort configuration = new SandboxConfigurationStore(
                InsuranceSamples.properties(), InsuranceSamples.currencies(), InsuranceSamples.countries());
        StaffRecordPort records = new SandboxRecordBook(configuration, InsuranceSamples.records());
        DistributorPort distributors = new SandboxDistributorDirectory(
                configuration, InsuranceArea.POLICY.channelKey(), InsuranceSamples.distributors());
        return new StaffTerminal(identity, access, new ImpersonationCoordinator(access, audit), configuration,
                distributors, records, audit, "POLICY", InsuranceArea.POLICY.primaryId().toString(), InsuranceArea.PREFIX);
    }
}
