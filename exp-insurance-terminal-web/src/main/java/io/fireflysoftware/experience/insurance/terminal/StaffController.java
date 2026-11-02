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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.UUID;

@SecurityScheme(name = "staffBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityRequirement(name = "staffBearer")
@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

    private final StaffTerminal terminal;

    private final org.springframework.beans.factory.ObjectProvider<io.fireflysoftware.experience.insurance.terminal.staff.CurrentStaffSubmissionReader> currentReads;

    public StaffController(StaffTerminal terminal,
            org.springframework.beans.factory.ObjectProvider<io.fireflysoftware.experience.insurance.terminal.staff.CurrentStaffSubmissionReader> currentReads) {
        this.terminal = terminal;
        this.currentReads = currentReads;
    }

    @DisableIdempotency
    @Operation(operationId = "signIn", tags = "Staff Terminal")
    @PostMapping("/sessions")
    public Mono<StaffSession> signIn(
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityPrincipal principal,
            @RequestBody SignInRequest body) {
        return terminal.authenticatedSession(UUID.fromString(
                TerminalAuthentication.uuid(principal.attributes().get("staffActorId"))));
    }

    @DisableIdempotency
    @Operation(operationId = "listRecords", tags = "Staff Terminal")
    @GetMapping("/records/{area}")
    public Flux<StaffRecord> list(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area) {
        return terminal.list(actorId, InsuranceArea.fromName(area));
    }

    @DisableIdempotency
    @Operation(operationId = "openRecord", tags = "Staff Terminal")
    @GetMapping("/records/{area}/{recordId}")
    public Mono<StaffRecord> open(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area, @PathVariable UUID recordId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(hidden = true) @AuthenticationPrincipal SecurityPrincipal principal,
            @Parameter(hidden = true) @RequestHeader org.springframework.http.HttpHeaders headers) {
        var selected=InsuranceArea.fromName(area);
        if(selected!=InsuranceArea.SUBMISSION)return terminal.open(actorId,selected,recordId);
        var suppliedActor=headers.get("X-Actor-Id");
        if(principal==null||suppliedActor!=null&&(suppliedActor.size()!=1||actorId==null||!actorId.toString().equals(principal.attributes().get("staffActorId"))))
            return Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Current staff actor required"));
        var reader=currentReads.getIfAvailable();
        if(reader==null)return Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Current submission read is not configured"));
        return reader.read(authorization,recordId).map(row->{
            if(row.getSubmissionStatus()==null||row.getPartyId()==null||row.getSubmissionNumber()==null||row.getSubmissionNumber().isBlank())
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Complete owner submission read unavailable");
            return new StaffRecord(row.getSubmissionId(),InsuranceArea.SUBMISSION.name(),row.getPartyId(),null,row.getSubmissionStatus().getValue(),row.getSubmissionNumber(),row.getDistributorId(),null);
        });
    }

    @DisableIdempotency
    @Operation(operationId = "updateRecord", tags = "Staff Terminal")
    @PutMapping("/records/{area}/{recordId}")
    public Mono<StaffRecord> update(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area,
                                    @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.update(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @DisableIdempotency
    @Operation(operationId = "closeRecord", tags = "Staff Terminal")
    @PostMapping("/records/{area}/{recordId}/close")
    public Mono<StaffRecord> close(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area,
                                   @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.close(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @DisableIdempotency
    @Operation(operationId = "removeDraft", tags = "Staff Terminal")
    @PostMapping("/records/{area}/{recordId}/remove")
    public Mono<Void> removeDraft(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area,
                                  @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.removeDraft(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @DisableIdempotency
    @Operation(operationId = "decideRecord", tags = "Staff Terminal")
    @PostMapping("/records/{area}/{recordId}/decide")
    public Mono<StaffRecord> decide(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area,
                                    @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.decide(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @DisableIdempotency
    @Operation(operationId = "attachDistributor", tags = "Staff Terminal")
    @PostMapping("/records/{area}/{recordId}/distributor")
    public Mono<StaffRecord> attach(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area,
                                    @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.attachDistributor(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @DisableIdempotency
    @Operation(operationId = "listDistributors", tags = "Staff Terminal")
    @GetMapping("/distributors")
    public Flux<DistributorView> distributors(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId) {
        return terminal.distributors(actorId);
    }

    @DisableIdempotency
    @Operation(operationId = "putProperty", tags = "Staff Terminal")
    @PutMapping("/configuration/{key}")
    public Mono<String> putProperty(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String key,
                                    @RequestBody PropertyBody body) {
        return terminal.putProperty(actorId, key, body.value());
    }

    @DisableIdempotency
    @Operation(operationId = "listMasters", tags = "Staff Terminal")
    @GetMapping("/masters/{type}")
    public Flux<MasterRecord> masters(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String type) {
        return terminal.masters(actorId, type);
    }

    public record SignInRequest(String username) {}

    public record PropertyBody(String value) {}

    public record RecordBody(UUID customerPartyId, String reason, String note, UUID distributorId,
                             UUID collaboratorId, String channel, String decision) {
        RecordCommand command(InsuranceArea area, UUID recordId) {
            return new RecordCommand(area, recordId, customerPartyId, reason, note, distributorId,
                    collaboratorId, channel, null, null, decision);
        }
    }
}
