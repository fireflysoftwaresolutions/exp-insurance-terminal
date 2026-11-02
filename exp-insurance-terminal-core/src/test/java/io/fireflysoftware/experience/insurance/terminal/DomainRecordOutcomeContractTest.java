package io.fireflysoftware.experience.insurance.terminal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Transport contract only: the HTTP responder does not establish a persisted insurance operation. */
class DomainRecordOutcomeContractTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final UUID RECORD = UUID.fromString("10000000-0000-4000-8000-000000000004");
    private static final UUID PARTY = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID EMPLOYEE = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private DomainRecordClientImpl client;
    private volatile String response;
    private volatile int status;

    @BeforeEach
    void setUp() throws IOException {
        status = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/submissions/" + RECORD, this::respond);
        server.start();
        ConfigurationPort configuration = new SandboxConfigurationStoreImpl(
                InsuranceSamples.properties(), InsuranceSamples.currencies(), InsuranceSamples.countries());
        client = new DomainRecordClientImpl(configuration,
                Map.of("submissions", "http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void withdrawalReturnsTheOwnerStatusInsteadOfTheRequestedStatus() {
        response = submission("WITHDRAWN");
        StaffRecord result = client.close(command("CLOSED", null)).block(TIMEOUT);
        assertEquals(1, requests.get());
        assertEquals("WITHDRAWN", result.status());
        assertEquals("UW-OWNER-41", result.reference());
        assertEquals(PARTY, result.customerPartyId());
        assertNull(result.note());
    }

    @Test
    void decisionReturnsTheOwnerRecordInsteadOfEchoingTheCommand() {
        response = submission("DECLINED");
        StaffRecord result = client.decide(command(null, "DECLINE")).block(TIMEOUT);
        assertEquals(1, requests.get());
        assertEquals("DECLINED", result.status());
        assertEquals("UW-OWNER-41", result.reference());
        assertEquals(PARTY, result.customerPartyId());
        assertNull(result.note());
    }

    @Test
    void anEmptyOwnerResponseCannotConfirmWithdrawal() {
        status = 204;
        response = "";
        assertThrows(RuntimeException.class, () -> client.close(command("WITHDRAWN", null)).block(TIMEOUT));
        assertEquals(1, requests.get());
    }

    @Test
    void anObjectWithoutAnOwnerStatusCannotConfirmDecision() {
        response = "{}";
        assertThrows(RuntimeException.class, () -> client.decide(command(null, "DECLINE")).block(TIMEOUT));
        assertEquals(1, requests.get());
    }

    @Test
    void anOwnerFailureCannotProduceACompletedDecision() {
        status = 503;
        response = "{}";
        assertThrows(RuntimeException.class, () -> client.decide(command(null, "DECLINE")).block(TIMEOUT));
        assertEquals(1, requests.get());
    }

    private static RecordCommand command(String status, String decision) {
        return new RecordCommand(InsuranceArea.SUBMISSION, RECORD, PARTY, "Requested reason",
                "Unpersisted channel note", null, null, null, EMPLOYEE, status, decision);
    }

    private static String submission(String status) {
        return "{\"submissionId\":\"" + RECORD + "\",\"partyId\":\"" + PARTY
                + "\",\"submissionNumber\":\"UW-OWNER-41\",\"submissionStatus\":\"" + status + "\"}";
    }

    private void respond(HttpExchange exchange) throws IOException {
        try {
            exchange.getRequestBody().readAllBytes();
            requests.incrementAndGet();
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (status != 204) {
                exchange.getResponseBody().write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
