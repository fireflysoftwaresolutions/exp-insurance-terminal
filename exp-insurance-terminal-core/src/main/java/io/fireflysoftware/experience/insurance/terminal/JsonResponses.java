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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

/**
 * Reads JSON from an HTTP body as text so the WebClient codec does not have to know JsonNode.
 */
public final class JsonResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonResponses() {
    }

    public static Mono<JsonNode> body(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("{}")
                .map(JsonResponses::tree);
    }

    public static JsonNode tree(String raw) {
        if (raw == null || raw.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception exception) {
            throw new TerminalDeniedException("DOMAIN_RESPONSE_UNREADABLE");
        }
    }
}
