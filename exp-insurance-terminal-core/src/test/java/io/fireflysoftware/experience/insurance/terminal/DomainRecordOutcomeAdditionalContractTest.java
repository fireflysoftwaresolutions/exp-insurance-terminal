package io.fireflysoftware.experience.insurance.terminal;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine generated client over controlled HTTP; no persisted owner/business admission. */
class DomainRecordOutcomeAdditionalContractTest {
    private static final UUID ID=UUID.fromString("10000000-0000-4000-8000-000000000004");
    private static final UUID REQUEST_PARTY=UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID OWNER_PARTY=UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final Duration TIMEOUT=Duration.ofSeconds(3);
    private static RecordCommand command(){return new RecordCommand(InsuranceArea.SUBMISSION,ID,REQUEST_PARTY,
        "Caller reason","Caller note",null,null,null,UUID.fromString("30000000-0000-4000-8000-000000000001"),"CLOSED","DECLINE");}
    private static String record(UUID id){return "{\"submissionId\":\""+id+"\",\"partyId\":\""+OWNER_PARTY+"\",\"submissionNumber\":\"OWNER-17\",\"submissionStatus\":\"RECEIVED\"}";}
    private static final class Fixture implements AutoCloseable {
        final HttpServer server;final AtomicInteger calls=new AtomicInteger();final AtomicReference<String> requestKey=new AtomicReference<>();
        final DomainRecordClientImpl client;
        Fixture(int status,String body)throws Exception{
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",request->{try{
                request.getRequestBody().readAllBytes();calls.incrementAndGet();requestKey.set(request.getRequestHeaders().getFirst("X-Idempotency-Key"));
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);request.getResponseHeaders().set("Content-Type","application/json");
                request.sendResponseHeaders(status,bytes.length);request.getResponseBody().write(bytes);
            }finally{request.close();}});server.start();
            client=new DomainRecordClientImpl(new SandboxConfigurationStoreImpl(InsuranceSamples.properties(),InsuranceSamples.currencies(),InsuranceSamples.countries()),
                Map.of("submissions","http://127.0.0.1:"+server.getAddress().getPort()));
        }
        public void close(){server.stop(0);}
    }
    @Test void updateDoesNotOverlayUnpersistedCallerPartyActorOrNote()throws Exception{
        try(var fixture=new Fixture(200,record(ID))){
            var returned=fixture.client.update(command()).block(TIMEOUT);
            assertEquals(2,fixture.calls.get());assertEquals(OWNER_PARTY,returned.customerPartyId());
            assertNull(returned.actorId());assertNull(returned.note());assertEquals("OWNER-17",returned.reference());
        }
    }
    @Test void anotherOwnerRecordCannotConfirmTheRequestedMutation()throws Exception{
        try(var fixture=new Fixture(200,record(UUID.fromString("10000000-0000-4000-8000-000000000009")))){
            var original=command();var unknown=assertThrows(RecordOutcomeUnknownException.class,()->fixture.client.close(original).block(TIMEOUT));
            assertSame(original,unknown.originalRequest());assertEquals(1,fixture.calls.get());
            assertEquals(fixture.requestKey.get(),unknown.transportRequestKey());
        }
    }
    @Test void proxy503RetainsOriginalRequestAndSentTransportKeyWithoutRetry()throws Exception{
        try(var fixture=new Fixture(503,"{}")){
            var original=command();var unknown=assertThrows(RecordOutcomeUnknownException.class,()->fixture.client.decide(original).block(TIMEOUT));
            assertSame(original,unknown.originalRequest());assertNotNull(unknown.transportRequestKey());
            assertEquals(fixture.requestKey.get(),unknown.transportRequestKey());assertEquals(1,fixture.calls.get());
        }
    }
}
