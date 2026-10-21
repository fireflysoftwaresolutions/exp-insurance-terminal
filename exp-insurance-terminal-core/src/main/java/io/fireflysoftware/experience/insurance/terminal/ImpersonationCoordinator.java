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

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Impersonation asks the security center for IMPERSONATE, then stores both ids.
 * Allow and deny come from that check. This class does not keep its own grant table.
 */
public final class ImpersonationCoordinator implements ImpersonationPort {

    private final AccessPort accessPort;
    private final AuditPort auditPort;

    public ImpersonationCoordinator(AccessPort accessPort, AuditPort auditPort) {
        this.accessPort = accessPort;
        this.auditPort = auditPort;
    }

    @Override
    public Mono<AuditEntry> impersonate(UUID employeeId, UUID customerPartyId, String reason, String action, String area, UUID recordId) {
        if (employeeId == null || customerPartyId == null || reason == null || reason.isBlank()) {
            return Mono.error(new TerminalDeniedException("REQUEST_INCOMPLETE"));
        }
        AccessCheck check = new AccessCheck(employeeId, "IMPERSONATE", "PARTY", customerPartyId.toString());
        return accessPort.check(check).flatMap(outcome -> {
            if (!outcome.allowed()) {
                return Mono.error(new TerminalDeniedException(outcome.reasonCode()));
            }
            AuditEntry entry = new AuditEntry(UUID.randomUUID(), employeeId, customerPartyId, reason, action, area, recordId);
            return auditPort.append(entry);
        });
    }
}
