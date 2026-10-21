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

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posts {@code POST /api/v1/access/checks}. Allowed and denied both come back as HTTP 200.
 */
public final class HttpAccessAdapter implements AccessPort {

    private final WebClient webClient;

    public HttpAccessAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<AccessOutcome> check(AccessCheck request) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("actorId", request.actorId().toString());
        body.put("action", request.action());
        body.put("resourceType", request.resourceType());
        body.put("resourceId", request.resourceId());
        return webClient.post()
                .uri("/api/v1/access/checks")
                .bodyValue(body)
                .exchangeToMono(JsonResponses::body)
                .map(node -> new AccessOutcome(node.path("allowed").asBoolean(false),
                        node.path("reasonCode").asText("")));
    }
}
