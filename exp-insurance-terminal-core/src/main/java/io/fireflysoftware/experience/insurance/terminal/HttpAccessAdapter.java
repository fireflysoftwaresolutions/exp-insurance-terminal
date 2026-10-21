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

import io.fireflysoftware.security.center.sdk.api.AccessControllerApi;
import io.fireflysoftware.security.center.sdk.invoker.ApiClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Asks domain-core-security-center to decide an access check.
 */
public final class HttpAccessAdapter implements AccessPort {

    private final AccessControllerApi api;

    public HttpAccessAdapter(AccessControllerApi api) {
        this.api = api;
    }

    public static HttpAccessAdapter at(String baseUrl) {
        return new HttpAccessAdapter(new AccessControllerApi(client(baseUrl)));
    }

    @Override
    public Mono<AccessOutcome> check(AccessCheck request) {
        String resource = request.resourceType() == null ? "" : request.resourceType().trim().toUpperCase();
        if ("CUSTOMER".equals(resource)) {
            resource = "PARTY";
        }
        io.fireflysoftware.security.center.sdk.model.AccessRequest body =
                new io.fireflysoftware.security.center.sdk.model.AccessRequest()
                        .actorId(request.actorId().toString())
                        .action(io.fireflysoftware.security.center.sdk.model.AccessRequest.ActionEnum.fromValue(request.action()))
                        .resourceType(io.fireflysoftware.security.center.sdk.model.AccessRequest.ResourceTypeEnum.fromValue(resource))
                        .resourceId(request.resourceId());
        return api.decideAccess(body, UUID.randomUUID().toString())
                .map(decision -> new AccessOutcome(
                        Boolean.TRUE.equals(decision.getAllowed()),
                        decision.getReasonCode() == null ? "" : decision.getReasonCode()));
    }

    static ApiClient client(String baseUrl) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        return client;
    }
}
