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
import java.util.UUID;

/**
 * Posts {@code POST /api/v1/identity/subjects}. A 404 is an unknown subject.
 */
public final class HttpIdentityAdapter implements IdentityPort {

    private final WebClient webClient;

    public HttpIdentityAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<SubjectRef> authenticate(String username) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", username == null ? "" : username);
        return post(body);
    }

    @Override
    public Mono<SubjectRef> resolve(String actorId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("actorId", actorId == null ? "" : actorId);
        return post(body);
    }

    private Mono<SubjectRef> post(Map<String, String> body) {
        return webClient.post()
                .uri("/api/v1/identity/subjects")
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.empty();
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return JsonResponses.body(response)
                            .map(node -> new SubjectRef(UUID.fromString(node.path("actorId").asText()),
                                    node.path("provider").asText("")));
                });
    }
}
