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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.security.center.core.access.SandboxPolicyAccessAdapter;
import io.fireflysoftware.security.center.core.access.SubjectBoundAccessAdapter;
import io.fireflysoftware.security.center.core.identity.SandboxIdentityAdapter;
import io.fireflysoftware.security.center.interfaces.access.AccessDecision;
import io.fireflysoftware.security.center.interfaces.access.AccessRequest;
import io.fireflysoftware.security.center.interfaces.identity.IdentityRequest;
import io.fireflysoftware.security.center.interfaces.identity.Subject;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HTTP stand-in that delegates to the security center sandbox identity and access adapters.
 * Allow and deny are the shipped policy, not a second grant table.
 */
public final class SecurityCenterSandbox implements AutoCloseable {

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> accessBodies = new CopyOnWriteArrayList<>();
    public final String baseUrl;

    public SecurityCenterSandbox() throws IOException {
        SandboxIdentityAdapter identity = new SandboxIdentityAdapter();
        SubjectBoundAccessAdapter access = new SubjectBoundAccessAdapter(identity,
                new SandboxPolicyAccessAdapter(new DefaultResourceLoader(), SandboxPolicyAccessAdapter.DEFAULT_LOCATION));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/identity/subjects", exchange -> identity(exchange, identity));
        server.createContext("/api/v1/access/checks", exchange -> access(exchange, access));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<String> accessBodies() {
        return List.copyOf(accessBodies);
    }

    private void identity(HttpExchange exchange, SandboxIdentityAdapter identity) throws IOException {
        IdentityRequest request = mapper.readValue(body(exchange), IdentityRequest.class);
        boolean usernameBlank = request.getUsername() == null || request.getUsername().isBlank();
        boolean actorBlank = request.getActorId() == null || request.getActorId().isBlank();
        if (usernameBlank && actorBlank) {
            write(exchange, 400, "");
            return;
        }
        Subject subject = (usernameBlank ? identity.resolve(request.getActorId()) : identity.authenticate(request)).block();
        if (subject == null) {
            write(exchange, 404, "");
            return;
        }
        write(exchange, 200, mapper.writeValueAsString(subject));
    }

    private void access(HttpExchange exchange, SubjectBoundAccessAdapter access) throws IOException {
        String raw = body(exchange);
        accessBodies.add(raw);
        AccessRequest request = mapper.readValue(raw, AccessRequest.class);
        AccessDecision decision = access.decide(request).block();
        write(exchange, 200, mapper.writeValueAsString(decision));
    }

    private static String body(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
