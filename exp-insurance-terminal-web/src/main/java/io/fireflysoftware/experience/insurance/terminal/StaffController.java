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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

    private final StaffTerminal terminal;

    public StaffController(StaffTerminal terminal) {
        this.terminal = terminal;
    }

    @PostMapping("/sessions")
    public Mono<StaffSession> signIn(@RequestBody Map<String, String> body) {
        return terminal.signIn(body.get("username"));
    }

    @GetMapping("/records/{area}")
    public Flux<StaffRecord> list(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String area) {
        return terminal.list(actorId, InsuranceArea.fromName(area));
    }

    @GetMapping("/records/{area}/{recordId}")
    public Mono<StaffRecord> open(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String area, @PathVariable UUID recordId) {
        return terminal.open(actorId, InsuranceArea.fromName(area), recordId);
    }

    @PutMapping("/records/{area}/{recordId}")
    public Mono<StaffRecord> update(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String area,
                                    @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.update(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @PostMapping("/records/{area}/{recordId}/close")
    public Mono<StaffRecord> close(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String area,
                                   @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.close(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @PostMapping("/records/{area}/{recordId}/remove")
    public Mono<Void> removeDraft(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String area,
                                  @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.removeDraft(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @PostMapping("/records/{area}/{recordId}/decide")
    public Mono<StaffRecord> decide(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String area,
                                    @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.decide(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @PostMapping("/records/{area}/{recordId}/distributor")
    public Mono<StaffRecord> attach(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String area,
                                    @PathVariable UUID recordId, @RequestBody RecordBody body) {
        return terminal.attachDistributor(actorId, body.command(InsuranceArea.fromName(area), recordId));
    }

    @GetMapping("/distributors")
    public Flux<DistributorView> distributors(@RequestHeader("X-Actor-Id") UUID actorId) {
        return terminal.distributors(actorId);
    }

    @PutMapping("/configuration/{key}")
    public Mono<String> putProperty(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String key,
                                    @RequestBody Map<String, String> body) {
        return terminal.putProperty(actorId, key, body.get("value"));
    }

    @GetMapping("/masters/{type}")
    public Flux<MasterRecord> masters(@RequestHeader("X-Actor-Id") UUID actorId, @PathVariable String type) {
        return terminal.masters(actorId, type);
    }

    public record RecordBody(UUID customerPartyId, String reason, String note, UUID distributorId,
                             UUID collaboratorId, String channel, String decision) {
        RecordCommand command(InsuranceArea area, UUID recordId) {
            return new RecordCommand(area, recordId, customerPartyId, reason, note, distributorId,
                    collaboratorId, channel, null, null, decision);
        }
    }
}
