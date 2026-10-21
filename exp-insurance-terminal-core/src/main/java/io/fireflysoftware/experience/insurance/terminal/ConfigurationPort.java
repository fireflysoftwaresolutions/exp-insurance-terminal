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

/**
 * Configuration and reference master data. Sandbox settings are not stored in a terminal database.
 * An installer sets the keys in core-common-config-mgmt through domain-core-configuration.
 */
public interface ConfigurationPort {

    Mono<String> getRequired(String key);

    Mono<String> put(String key, String value, java.util.UUID employeeId);

    Flux<MasterRecord> masters(String type);
}
