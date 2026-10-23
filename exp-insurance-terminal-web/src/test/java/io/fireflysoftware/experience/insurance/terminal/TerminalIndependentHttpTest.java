package io.fireflysoftware.experience.insurance.terminal;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import reactor.core.publisher.*;

@SpringBootTest(classes={TerminalBoundaryHttpTest.Application.class,TerminalIndependentHttpTest.Ports.class},
 webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"server.address=127.0.0.1","firefly.eda.enabled=false","firefly.stepevents.enabled=false"})
class TerminalIndependentHttpTest {
 static final AtomicReference<String> MODE=new AtomicReference<>("PERMIT");
 static final AtomicInteger READS=new AtomicInteger(), MUTATIONS=new AtomicInteger(), AUDITS=new AtomicInteger();
 @LocalServerPort int port;
 @DynamicPropertySource static void trust(DynamicPropertyRegistry p){TerminalBoundaryHttpTest.trust(p);}
 @AfterAll static void stop(){TerminalBoundaryHttpTest.stop();}
 @BeforeEach void reset(){new TerminalBoundaryHttpTest().reset();MODE.set("PERMIT");READS.set(0);MUTATIONS.set(0);AUDITS.set(0);}
 @Configuration(proxyBeanMethods=false) static class Ports {
  @Bean @Primary org.fireflyframework.security.spi.PolicyDecisionPort reviewCurrentPolicy(){return (principal,action,resource,context)->{
    if(MODE.get().equals("PDP_TIMEOUT"))return Mono.never();
    if(MODE.get().equals("PDP_EXPIRY"))return Mono.delay(java.time.Duration.ofSeconds(3)).map(i->org.fireflyframework.security.api.domain.Decision.permit());
    return Mono.just(org.fireflyframework.security.api.domain.Decision.permit());
  };}
  @Bean @Primary AccessPort reviewAccess(){return check->Mono.defer(()->{
    TerminalBoundaryHttpTest.ACCESS.incrementAndGet();TerminalBoundaryHttpTest.BUSINESS_ORDER.add(check.action());
    if(MODE.get().equals(check.action()+"_EMPTY"))return Mono.empty();
    return Mono.just(new AccessOutcome(true,""));
  });}
  @Bean @Primary StaffTerminal reviewTerminal(IdentityPort identity,AccessPort access){
    var config=new SandboxConfigurationStore(InsuranceSamples.properties(),InsuranceSamples.currencies(),InsuranceSamples.countries());
    var book=new SandboxRecordBook(config,InsuranceSamples.records());
    StaffRecordPort records=new StaffRecordPort(){
      public Flux<StaffRecord> list(StaffArea area){READS.incrementAndGet();return book.list(area);}
      public Mono<StaffRecord> get(StaffArea area,UUID id){READS.incrementAndGet();return book.get(area,id);}
      public Mono<StaffRecord> update(RecordCommand cmd){MUTATIONS.incrementAndGet();return book.update(cmd);}
      public Mono<StaffRecord> close(RecordCommand cmd){MUTATIONS.incrementAndGet();return book.close(cmd);}
      public Mono<Void> removeDraft(RecordCommand cmd,String required){MUTATIONS.incrementAndGet();return book.removeDraft(cmd,required);}
      public Mono<StaffRecord> decide(RecordCommand cmd){MUTATIONS.incrementAndGet();return book.decide(cmd);}
    };
    AuditPort audit=new AuditPort(){
      public Mono<AuditEntry> append(AuditEntry entry){AUDITS.incrementAndGet();return MODE.get().equals("AUDIT_EMPTY")?Mono.empty():Mono.just(entry);}
      public Flux<AuditEntry> list(){return Flux.empty();}
    };
    return new StaffTerminal(identity,access,new ImpersonationCoordinator(access,audit),config,
      new SandboxDistributorDirectory(config,InsuranceArea.POLICY.channelKey(),InsuranceSamples.distributors()),records,audit,
      InsuranceArea.POLICY.accessType(),InsuranceArea.POLICY.primaryId().toString(),InsuranceArea.PREFIX);
  }
 }
 HttpResponse<String> call(String method,String path,String body)throws Exception{
   var h=new TerminalBoundaryHttpTest();h.port=port;
   return h.signed(method,path,body,h.token(Map.of()),null);
 }
 String updateBody(){return "{\"customerPartyId\":\""+InsuranceSamples.CUSTOMER_PARTY+"\",\"reason\":\"owned independent business-contract review\",\"note\":\"synthetic-update\"}";}
 @Test void currentPolicyTimeoutMustDenyBeforeAnyBusinessPort()throws Exception{
  MODE.set("PDP_TIMEOUT");var response=call("GET","/api/v1/staff/records/POLICY",null);
  assertAll(()->assertEquals(403,response.statusCode(),response.body()),()->assertEquals(0,TerminalBoundaryHttpTest.IDENTITIES.get()),()->assertEquals(0,READS.get()));
 }
 @Test void expiryWhileCurrentPolicyIsPendingMustDenyBeforeAnyBusinessPort()throws Exception{
  var h=new TerminalBoundaryHttpTest();h.port=port;
  assertEquals(200,call("GET","/api/v1/staff/records/POLICY",null).statusCode());
  TerminalBoundaryHttpTest.IDENTITIES.set(0);READS.set(0);MODE.set("PDP_EXPIRY");
  var token=h.token(Map.of("exp",Date.from(java.time.Instant.now().plusSeconds(2))));
  var response=h.signed("GET","/api/v1/staff/records/POLICY",null,token,null);
  assertAll(()->assertEquals(403,response.statusCode(),response.body()),()->assertEquals(0,TerminalBoundaryHttpTest.IDENTITIES.get()),()->assertEquals(0,READS.get()));
 }
 @Test void affirmativeControlReachesExactlyOneRecordEffect()throws Exception{
  var response=call("PUT","/api/v1/staff/records/POLICY/"+InsuranceArea.POLICY.primaryId(),updateBody());
  assertEquals(200,response.statusCode(),response.body());assertEquals(1,MUTATIONS.get());assertEquals(1,AUDITS.get());
  assertEquals(List.of("ADMINISTER","IMPERSONATE"),TerminalBoundaryHttpTest.BUSINESS_ORDER);
 }
 @Test void emptyAdministerMustNotReadAnyRecords()throws Exception{
  MODE.set("ADMINISTER_EMPTY");var response=call("GET","/api/v1/staff/records/POLICY",null);
  assertAll(()->assertEquals(403,response.statusCode(),response.body()),()->assertEquals(0,READS.get()));
 }
 @Test void emptyImpersonateMustNotMutateAnyRecord()throws Exception{
  MODE.set("IMPERSONATE_EMPTY");var response=call("PUT","/api/v1/staff/records/POLICY/"+InsuranceArea.POLICY.primaryId(),updateBody());
  assertAll(()->assertEquals(403,response.statusCode(),response.body()),()->assertEquals(0,MUTATIONS.get()),()->assertEquals(0,AUDITS.get()));
 }
 @Test void missingImpersonationAuditAcknowledgmentMustNotMutateAnyRecord()throws Exception{
  MODE.set("AUDIT_EMPTY");var response=call("PUT","/api/v1/staff/records/POLICY/"+InsuranceArea.POLICY.primaryId(),updateBody());
  assertAll(()->assertTrue(response.statusCode()>=400,response.body()),()->assertEquals(0,MUTATIONS.get()));
 }
 @Test void emptyAdministerMustNotCreateAnAuthenticatedSession()throws Exception{
  MODE.set("ADMINISTER_EMPTY");var response=call("POST","/api/v1/staff/sessions","{\"username\":\"unchosen\"}");
  assertEquals(403,response.statusCode(),response.body());
 }
}
