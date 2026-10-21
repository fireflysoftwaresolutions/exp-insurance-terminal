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

import java.util.UUID;

/**
 * Domain port for staff record operations. The terminal does not call a core SDK.
 */
public interface StaffRecordPort {

    Flux<StaffRecord> list(StaffArea area);

    Mono<StaffRecord> get(StaffArea area, UUID recordId);

    Mono<StaffRecord> update(RecordCommand command);

    Mono<StaffRecord> close(RecordCommand command);

    Mono<Void> removeDraft(RecordCommand command, String requiredStatus);

    Mono<StaffRecord> decide(RecordCommand command);
}
