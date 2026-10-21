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
 * Domain command. {@code actorId} is the employee. {@code customerPartyId} is the business party.
 */
public record RecordCommand(
        StaffArea area,
        UUID recordId,
        UUID customerPartyId,
        String reason,
        String note,
        UUID distributorId,
        UUID collaboratorId,
        String channel,
        UUID actorId,
        String status,
        String decision) {

    public RecordCommand withActor(UUID actor) {
        return new RecordCommand(area, recordId, customerPartyId, reason, note, distributorId, collaboratorId, channel, actor, status, decision);
    }

    public RecordCommand withStatus(String nextStatus) {
        return new RecordCommand(area, recordId, customerPartyId, reason, note, distributorId, collaboratorId, channel, actorId, nextStatus, decision);
    }

    public static RecordCommand of(StaffArea area, UUID recordId, UUID customerPartyId, String reason) {
        return new RecordCommand(area, recordId, customerPartyId, reason, null, null, null, null, null, null, null);
    }
}
