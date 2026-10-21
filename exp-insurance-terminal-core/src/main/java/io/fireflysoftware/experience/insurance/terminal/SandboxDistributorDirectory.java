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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sandbox directory of distributors. Roles describe the sample parties.
 * The live adapter calls core-common-distributor-mgmt and does not create agency tables.
 */
public final class SandboxDistributorDirectory implements DistributorPort {

    private final Map<UUID, DistributorView> distributors = new ConcurrentHashMap<>();
    private final List<DistributorLink> links = new CopyOnWriteArrayList<>();
    private final ConfigurationPort configuration;
    private final String channelKey;

    public SandboxDistributorDirectory(ConfigurationPort configuration, String channelKey, List<DistributorView> seeded) {
        this.configuration = configuration;
        this.channelKey = channelKey;
        seeded.forEach(view -> distributors.put(view.distributorId(), view));
    }

    @Override
    public Flux<DistributorView> list() {
        return configuration.getRequired(channelKey)
                .flatMapMany(channel -> Flux.fromIterable(distributors.values())
                        .map(view -> new DistributorView(view.distributorId(), view.name(), view.role(), view.partyId(),
                                channel, view.agencyId(), view.collaboratorId())));
    }

    @Override
    public Mono<DistributorView> link(DistributorLink link) {
        DistributorView known = distributors.get(link.distributorId());
        if (known == null) {
            return Mono.error(new TerminalDeniedException("DISTRIBUTOR_NOT_FOUND"));
        }
        links.add(link);
        return Mono.just(known);
    }

    public List<DistributorLink> links() {
        return List.copyOf(links);
    }
}
