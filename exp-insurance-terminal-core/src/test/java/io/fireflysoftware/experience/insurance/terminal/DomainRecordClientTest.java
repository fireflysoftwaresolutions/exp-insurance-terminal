package io.fireflysoftware.experience.insurance.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainRecordClientTest {

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private DomainRecordClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ConfigurationPort configuration = new SandboxConfigurationStore(
                InsuranceSamples.properties(), InsuranceSamples.currencies(), InsuranceSamples.countries());
        client = new DomainRecordClient(WebClient.builder().build(), configuration, Map.of(
                "policies", base,
                "claims", base,
                "submissions", base));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void policyUpdatePutsTheEmployeeAndTheCustomerParty() {
        UUID employee = UUID.fromString("33333333-3333-4333-8333-333333333333");
        RecordCommand command = new RecordCommand(InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Endorse", "Staff note", null, null, null, employee, null, null);
        StaffRecord updated = client.update(command).block();
        assertEquals(employee, updated.actorId());
        assertEquals(InsuranceSamples.CUSTOMER_PARTY, updated.customerPartyId());
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("PUT /api/v1/policies/")
                && call.contains(employee.toString())
                && call.contains(InsuranceSamples.CUSTOMER_PARTY.toString())));
    }

    @Test
    void claimClosePostsTheClosePath() {
        RecordCommand command = new RecordCommand(InsuranceArea.CLAIM, InsuranceArea.CLAIM.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Close", null, null, null, null,
                UUID.fromString("33333333-3333-4333-8333-333333333333"), "CLOSED", null);
        assertEquals("CLOSED", client.close(command).block().status());
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("POST /api/v1/claims/") && call.contains("/close")));
    }

    @Test
    void submissionDecisionPostsDecline() {
        RecordCommand command = new RecordCommand(InsuranceArea.SUBMISSION, InsuranceArea.SUBMISSION.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Outside appetite", null, null, null, null,
                UUID.fromString("33333333-3333-4333-8333-333333333333"), null, "DECLINE");
        assertEquals("DECLINED", client.decide(command).block().status());
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("POST /api/v1/submissions/") && call.contains("/decline")));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(exchange.getRequestMethod() + " " + path + " " + raw);
        byte[] response;
        if ("GET".equals(exchange.getRequestMethod())) {
            ObjectNode body = mapper.createObjectNode();
            body.put("policyStatus", "IN_FORCE");
            body.put("notes", "policy");
            response = mapper.writeValueAsBytes(body);
        } else if ("PUT".equals(exchange.getRequestMethod())) {
            response = raw.getBytes(StandardCharsets.UTF_8);
        } else {
            response = "{}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
