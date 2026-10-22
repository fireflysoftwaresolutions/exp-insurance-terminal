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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fireflysoftware.domain.configuration.sdk.api.EnvironmentConfigApi;
import io.fireflysoftware.domain.configuration.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.distributor.branding.sdk.api.AgencyApi;
import io.fireflysoftware.domain.distributor.branding.sdk.api.AgentApi;
import io.fireflysoftware.domain.distributor.branding.sdk.api.DistributorQueryApi;
import io.fireflysoftware.domain.referencedata.sdk.api.MastersApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires staff identity and access to domain-core-security-center.
 * {@code firefly.security-center.identity.adapter} stays on that service and defaults to sandbox.
 */
@Configuration
@EnableConfigurationProperties(TerminalWiring.TerminalLocations.class)
public class TerminalWiring {

    @Bean
    public IdentityPort identityPort(
            @Value("${api-configuration.domain-platform.security-center.base-path:http://localhost:8085}") String basePath) {
        return HttpIdentityAdapter.at(basePath);
    }

    @Bean
    public AccessPort accessPort(
            @Value("${api-configuration.domain-platform.security-center.base-path:http://localhost:8085}") String basePath) {
        return HttpAccessAdapter.at(basePath);
    }

    @Bean
    public AuditPort auditPort() {
        return new InMemoryAuditLog();
    }

    @Bean
    public ImpersonationPort impersonationPort(AccessPort accessPort, AuditPort auditPort) {
        return new ImpersonationCoordinator(accessPort, auditPort);
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.terminal", name = "adapter", havingValue = "sandbox", matchIfMissing = true)
    public ConfigurationPort sandboxConfigurationPort() {
        return new SandboxConfigurationStore(InsuranceSamples.properties(), InsuranceSamples.currencies(), InsuranceSamples.countries());
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.terminal", name = "adapter", havingValue = "sandbox", matchIfMissing = true)
    public StaffRecordPort sandboxRecordPort(ConfigurationPort configurationPort) {
        return new SandboxRecordBook(configurationPort, InsuranceSamples.records());
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.terminal", name = "adapter", havingValue = "sandbox", matchIfMissing = true)
    public DistributorPort sandboxDistributorPort(ConfigurationPort configurationPort) {
        return new SandboxDistributorDirectory(configurationPort, InsuranceArea.POLICY.channelKey(), InsuranceSamples.distributors());
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.terminal", name = "adapter", havingValue = "domain")
    public ConfigurationPort domainConfigurationPort(TerminalLocations locations) {
        ApiClient configuration = new ApiClient();
        configuration.setBasePath(locations.getConfigurationBaseUrl());
        io.fireflysoftware.domain.referencedata.sdk.invoker.ApiClient masters =
                new io.fireflysoftware.domain.referencedata.sdk.invoker.ApiClient();
        masters.setBasePath(locations.getMasterDataBaseUrl());
        return new DomainConfigurationClient(new EnvironmentConfigApi(configuration), new MastersApi(masters));
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.terminal", name = "adapter", havingValue = "domain")
    public StaffRecordPort domainRecordPort(ConfigurationPort configurationPort, TerminalLocations locations) {
        return new DomainRecordClient(configurationPort, locations.getDomains());
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.terminal", name = "adapter", havingValue = "domain")
    public DistributorPort domainDistributorPort(ConfigurationPort configurationPort, TerminalLocations locations) {
        io.fireflysoftware.domain.distributor.branding.sdk.invoker.ApiClient distributors =
                new io.fireflysoftware.domain.distributor.branding.sdk.invoker.ApiClient();
        distributors.setBasePath(trim(locations.getDistributorBaseUrl()));
        return new DomainDistributorClient(configurationPort,
                new DistributorQueryApi(distributors),
                new AgencyApi(distributors),
                new AgentApi(distributors),
                InsuranceArea.POLICY.channelKey());
    }

    @Bean
    public StaffTerminal staffTerminal(IdentityPort identityPort, AccessPort accessPort, ImpersonationPort impersonationPort,
                                       ConfigurationPort configurationPort, DistributorPort distributorPort,
                                       StaffRecordPort recordPort, AuditPort auditPort) {
        return new StaffTerminal(identityPort, accessPort, impersonationPort, configurationPort, distributorPort, recordPort,
                auditPort, "POLICY", InsuranceArea.POLICY.primaryId().toString(), InsuranceArea.PREFIX);
    }

    @ConfigurationProperties(prefix = "firefly.terminal")
    public static class TerminalLocations {
        private String adapter = "sandbox";
        private String configurationBaseUrl = "http://localhost:8088";
        private String masterDataBaseUrl = "http://localhost:8089";
        private String distributorBaseUrl = "http://localhost:8070";
        private Map<String, String> domains = new LinkedHashMap<>();

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }

        public String getConfigurationBaseUrl() {
            return configurationBaseUrl;
        }

        public void setConfigurationBaseUrl(String configurationBaseUrl) {
            this.configurationBaseUrl = configurationBaseUrl;
        }

        public String getMasterDataBaseUrl() {
            return masterDataBaseUrl;
        }

        public void setMasterDataBaseUrl(String masterDataBaseUrl) {
            this.masterDataBaseUrl = masterDataBaseUrl;
        }

        public String getDistributorBaseUrl() {
            return distributorBaseUrl;
        }

        public void setDistributorBaseUrl(String distributorBaseUrl) {
            this.distributorBaseUrl = distributorBaseUrl;
        }

        public Map<String, String> getDomains() {
            return domains;
        }

        public void setDomains(Map<String, String> domains) {
            this.domains = domains;
        }
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            throw new TerminalDeniedException("PROPERTY_NOT_SET");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
