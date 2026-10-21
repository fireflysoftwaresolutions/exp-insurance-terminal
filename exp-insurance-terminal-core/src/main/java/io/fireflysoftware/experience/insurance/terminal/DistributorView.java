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

import java.util.UUID;

/**
 * Staff view of a distributor. Brokers, agencies, and collaborators are parties reached
 * through the distributor modules. This terminal does not keep a second agency master.
 */
public record DistributorView(
        UUID distributorId,
        String name,
        String role,
        UUID partyId,
        String channel,
        UUID agencyId,
        UUID collaboratorId) {
}
