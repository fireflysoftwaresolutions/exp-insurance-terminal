package io.fireflysoftware.experience.insurance.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class DomainRecordClientTest {

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private DomainRecordClientImpl client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ObjectNode> mutationBodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ConfigurationPort configuration = new SandboxConfigurationStoreImpl(
                InsuranceSamples.properties(), InsuranceSamples.currencies(), InsuranceSamples.countries());
        client = new DomainRecordClientImpl(configuration, Map.of(
                "policies", base,
                "claims", base,
                "submissions", base));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void policyUpdateReturnsOnlyOwnerFieldsAndSendsTheActualOwnerDocument() {
        UUID employee = UUID.fromString("33333333-3333-4333-8333-333333333333");
        RecordCommand command = new RecordCommand(InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Endorse", "Staff note", null, null, null, employee, null, null);
        StaffRecord updated = client.update(command).block();
        assertNull(updated.actorId());
        assertNull(updated.customerPartyId());
        assertNull(updated.note());
        assertEquals("POLICY-OWNER-17", updated.reference());
        assertEquals("IN_FORCE", updated.status());
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("PUT /api/v1/policies/")));
        ObjectNode sent = mutationBodies.getFirst();
        assertEquals(InsuranceArea.POLICY.primaryId().toString(), sent.path("policyId").asText());
        assertEquals("POLICY-OWNER-17", sent.path("policyNumber").asText());
        assertEquals("IN_FORCE", sent.path("policyStatus").asText());
        assertTrue(!sent.has("actorId") && !sent.has("partyId") && !sent.has("note"));
    }

    @Test
    void claimClosePostsTheClosePath() {
        RecordCommand command = new RecordCommand(InsuranceArea.CLAIM, InsuranceArea.CLAIM.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Close", null, null, null, null,
                UUID.fromString("33333333-3333-4333-8333-333333333333"), "CLOSED", null);
        StaffRecord returned = client.close(command).block();
        assertEquals("CLOSED", returned.status());
        assertEquals("CLAIM-OWNER-17", returned.reference());
        assertEquals(InsuranceArea.CLAIM.primaryId(), returned.id());
        assertNull(returned.actorId());
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("POST /api/v1/claims/") && call.contains("/close")));
        assertEquals("Close", mutationBodies.getFirst().path("statusReason").asText());
    }

    @Test
    void submissionDecisionPostsDecline() {
        RecordCommand command = new RecordCommand(InsuranceArea.SUBMISSION, InsuranceArea.SUBMISSION.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Outside appetite", null, null, null, null,
                UUID.fromString("33333333-3333-4333-8333-333333333333"), null, "DECLINE");
        StaffRecord returned = client.decide(command).block();
        assertEquals("DECLINED", returned.status());
        assertEquals("SUBMISSION-OWNER-17", returned.reference());
        assertEquals(InsuranceArea.SUBMISSION.primaryId(), returned.id());
        assertEquals(InsuranceSamples.CUSTOMER_PARTY, returned.customerPartyId());
        assertNull(returned.actorId());
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("POST /api/v1/submissions/") && call.contains("/decline")));
        assertEquals("Outside appetite", mutationBodies.getFirst().path("statusReason").asText());
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(exchange.getRequestMethod() + " " + path + " " + raw);
        byte[] response;
        if ("GET".equals(exchange.getRequestMethod())) {
            ObjectNode body = mapper.createObjectNode();
            body.put("policyId", InsuranceArea.POLICY.primaryId().toString());
            body.put("policyStatus", "IN_FORCE");
            body.put("policyNumber", "POLICY-OWNER-17");
            response = mapper.writeValueAsBytes(body);
        } else if ("PUT".equals(exchange.getRequestMethod())) {
            mutationBodies.add((ObjectNode) mapper.readTree(raw));
            response = raw.getBytes(StandardCharsets.UTF_8);
        } else {
            mutationBodies.add((ObjectNode) mapper.readTree(raw));
            ObjectNode body = mapper.createObjectNode();
            if (path.contains("/claims/")) {
                body.put("claimId", InsuranceArea.CLAIM.primaryId().toString());
                body.put("claimStatus", "CLOSED");
                body.put("claimNumber", "CLAIM-OWNER-17");
            } else {
                body.put("submissionId", InsuranceArea.SUBMISSION.primaryId().toString());
                body.put("submissionStatus", "DECLINED");
                body.put("submissionNumber", "SUBMISSION-OWNER-17");
                body.put("partyId", InsuranceSamples.CUSTOMER_PARTY.toString());
            }
            response = mapper.writeValueAsBytes(body);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
