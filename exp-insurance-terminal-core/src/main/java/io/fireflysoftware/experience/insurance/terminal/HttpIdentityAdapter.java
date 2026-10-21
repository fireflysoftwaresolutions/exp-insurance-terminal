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

import io.fireflysoftware.security.center.sdk.api.IdentityControllerApi;
import io.fireflysoftware.security.center.sdk.model.IdentityRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves a staff subject through domain-core-security-center. A 404 is an unknown subject.
 */
public final class HttpIdentityAdapter implements IdentityPort {

    private final IdentityControllerApi api;

    public HttpIdentityAdapter(IdentityControllerApi api) {
        this.api = api;
    }

    public static HttpIdentityAdapter at(String baseUrl) {
        return new HttpIdentityAdapter(new IdentityControllerApi(HttpAccessAdapter.client(baseUrl)));
    }

    @Override
    public Mono<SubjectRef> authenticate(String username) {
        return post(new IdentityRequest().username(username == null ? "" : username));
    }

    @Override
    public Mono<SubjectRef> resolve(String actorId) {
        return post(new IdentityRequest().actorId(actorId == null ? "" : actorId));
    }

    private Mono<SubjectRef> post(IdentityRequest request) {
        return api.resolveSubject(request)
                .map(subject -> new SubjectRef(
                        UUID.fromString(subject.getActorId()),
                        subject.getProvider() == null ? "" : subject.getProvider().getValue()))
                .onErrorResume(error -> missing(error) ? Mono.empty() : Mono.error(error));
    }

    private static boolean missing(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientResponseException http && http.getStatusCode().value() == 404) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
