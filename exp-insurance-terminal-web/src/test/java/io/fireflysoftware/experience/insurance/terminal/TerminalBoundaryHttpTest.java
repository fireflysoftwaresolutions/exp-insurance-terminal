package io.fireflysoftware.experience.insurance.terminal;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.Obligation;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.test.context.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = TerminalBoundaryHttpTest.Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"server.address=127.0.0.1", "firefly.eda.enabled=false", "firefly.stepevents.enabled=false"})
class TerminalBoundaryHttpTest {
  static final UUID ACTOR = UUID.fromString("33333333-3333-4333-8333-333333333333");
  static final AtomicInteger IDENTITIES = new AtomicInteger();
  static final AtomicInteger ACCESS = new AtomicInteger();
  static final AtomicInteger AUTHENTICATES = new AtomicInteger();
  static final AtomicReference<String> DENIAL = new AtomicReference<>();
  static final AtomicReference<String> POLICY_FAILURE = new AtomicReference<>();
  static final List<String> BUSINESS_ORDER = new java.util.concurrent.CopyOnWriteArrayList<>();
  static final UUID TENANT = UUID.fromString("44444444-4444-4444-8444-444444444444");
  static final String ISSUER = "https://synthetic.staff.invalid";
  static final AtomicReference<Decision> DECISION = new AtomicReference<>(Decision.permit());
  static final AtomicReference<UUID> REMAP = new AtomicReference<>();
  static final List<Map<String,Object>> POLICY_CALLS = new java.util.concurrent.CopyOnWriteArrayList<>();
  static final RSAKey KEY;
  static HttpServer JWKS;
  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("synthetic-terminal").generate();
      startJwks();
    } catch (Exception e) {throw new ExceptionInInitializerError(e);}
  }
  static synchronized void startJwks() {
    if (JWKS != null) return;
    try {
      JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
      JWKS.createContext("/jwks", exchange -> {
        byte[] bytes = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(200,bytes.length);
        try(var out=exchange.getResponseBody()) {out.write(bytes);}
      });
      JWKS.start();
    } catch (java.io.IOException error) { throw new IllegalStateException(error); }
  }
  @DynamicPropertySource static void trust(DynamicPropertyRegistry p) {
    startJwks();
    p.add("firefly.terminal.security.issuer",()->ISSUER);
    p.add("firefly.terminal.security.audience",()->"staff-insurance");
    p.add("firefly.terminal.security.jwk-set-uri",()->"http://127.0.0.1:"+JWKS.getAddress().getPort()+"/jwks");
  }
  @AfterAll static synchronized void stop(){if(JWKS!=null){JWKS.stop(0);JWKS=null;}}
  @LocalServerPort int port;
  @BeforeEach void reset() { IDENTITIES.set(0); ACCESS.set(0); DECISION.set(Decision.permit()); REMAP.set(null); POLICY_CALLS.clear(); AUTHENTICATES.set(0); DENIAL.set(null); POLICY_FAILURE.set(null); BUSINESS_ORDER.clear(); }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({StaffController.class, TerminalExceptionHandler.class, TerminalSecurityConfiguration.class})
  static class Application {
    @Bean PolicyDecisionPort currentPolicy() {
      return (principal, action, resource, context) -> {
        POLICY_CALLS.add(Map.of("action",action,"resource",resource,"context",context,"principal",principal));
        if("error".equals(POLICY_FAILURE.get())) return Mono.error(new IllegalStateException("synthetic-internal-detail"));
        return Mono.justOrEmpty(DECISION.get());
      };
    }
    @Bean IdentityPort identity() {
      return new IdentityPort() {
        public Mono<SubjectRef> authenticate(String username) {
          AUTHENTICATES.incrementAndGet(); IDENTITIES.incrementAndGet(); return Mono.just(new SubjectRef(ACTOR, "synthetic-sandbox"));
        }
        public Mono<SubjectRef> resolve(String actor) {
          IDENTITIES.incrementAndGet(); return Mono.just(new SubjectRef(REMAP.get()==null?UUID.fromString(actor):REMAP.get(), "synthetic-sandbox"));
        }
      };
    }
    @Bean AccessPort access() {
      return check -> { ACCESS.incrementAndGet(); BUSINESS_ORDER.add(check.action());
        return Mono.just(new AccessOutcome(DENIAL.get()==null,DENIAL.get()==null?"":DENIAL.get())); };
    }
    @Bean StaffTerminal terminal(IdentityPort identity, AccessPort access) {
      var config = new SandboxConfigurationStore(InsuranceSamples.properties(), InsuranceSamples.currencies(), InsuranceSamples.countries());
      var audit = new InMemoryAuditLog();
      return new StaffTerminal(identity, access, new ImpersonationCoordinator(access, audit), config,
          new SandboxDistributorDirectory(config, InsuranceArea.POLICY.channelKey(), InsuranceSamples.distributors()),
          new SandboxRecordBook(config, InsuranceSamples.records()), audit,
          InsuranceArea.POLICY.accessType(), InsuranceArea.POLICY.primaryId().toString(), InsuranceArea.PREFIX);
    }

  }

  @Test void anonymousActorHeaderCannotReadStaffData() throws Exception {
    var response = send("GET", "/api/v1/staff/records/POLICY", null);
    assertEquals(401, response.statusCode(), response.body());
    assertEquals(0, IDENTITIES.get()); assertEquals(0, ACCESS.get());
  }
  @Test void usernameIsNotAnAuthenticationCredential() throws Exception {
    var response = send("POST", "/api/v1/staff/sessions", "{\"username\":\"sample-employee\"}");
    assertEquals(403, response.statusCode(), response.body());
    assertEquals(0, IDENTITIES.get()); assertEquals(0, ACCESS.get());
  }
  @Test void arbitraryBearerCannotTurnActorHeaderIntoIdentity() throws Exception {
    var request = request("GET", "/api/v1/staff/masters/CURRENCY", null)
        .header("Authorization", "Bearer fabricated-token").build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(401, response.statusCode(), response.body());
    assertEquals(0, IDENTITIES.get()); assertEquals(0, ACCESS.get());
  }


  @Test void signedIdentityReadsAndCarriesExactCurrentPolicyContext() throws Exception {
    var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token(Map.of()),ACTOR.toString());
    assertEquals(200,response.statusCode(),response.body());
    assertEquals(1,POLICY_CALLS.size());
    assertEquals("terminal.insurance.read",POLICY_CALLS.getFirst().get("action"));
    assertEquals("tenant/"+TENANT+"/terminal/insurance/api/v1/staff/masters/CURRENCY",POLICY_CALLS.getFirst().get("resource"));
    var context=(Map<?,?>)POLICY_CALLS.getFirst().get("context");
    assertEquals(ACTOR.toString(),context.get("actorId")); assertEquals(TENANT.toString(),context.get("tenantId"));
    assertEquals(1,IDENTITIES.get()); assertEquals(1,ACCESS.get());
  }
  @Test void signedActorIsDerivedWhenCompatibilityHeaderIsAbsent() throws Exception {
    var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token(Map.of()),null);
    assertEquals(200,response.statusCode(),response.body()); assertEquals(1,IDENTITIES.get());
  }
  @Test void mismatchedHeaderCannotSelectAnotherActor() throws Exception {
    var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token(Map.of()),TENANT.toString());
    assertEquals(403,response.statusCode(),response.body()); assertTrue(response.body().contains("STAFF_ACTOR_MISMATCH"));
    assertEquals(0,IDENTITIES.get()); assertEquals(0,ACCESS.get()); assertTrue(POLICY_CALLS.isEmpty());
  }
  @Test void duplicateActorHeaderIsDenied() throws Exception {
    var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/staff/masters/CURRENCY"))
      .header("Authorization","Bearer "+token(Map.of())).header("X-Actor-Id",ACTOR.toString())
      .header("X-Actor-Id",ACTOR.toString()).GET().build();
    var response=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
    assertEquals(403,response.statusCode(),response.body()); assertEquals(0,IDENTITIES.get());
  }
  @Test void currentDenialAfterPermitCannotReuseASuccess() throws Exception {
    String token=token(Map.of());
    assertEquals(200,signed("GET","/api/v1/staff/masters/CURRENCY",null,token,ACTOR.toString()).statusCode());
    DECISION.set(Decision.deny("revoked"));
    var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token,ACTOR.toString());
    assertEquals(403,response.statusCode(),response.body()); assertEquals(2,POLICY_CALLS.size());
    assertEquals(1,IDENTITIES.get()); assertEquals(1,ACCESS.get());
  }
  @Test void emptyOrObligationBearingPolicyIsDenied() throws Exception {
    for(Decision decision : Arrays.asList(null,Decision.permit(List.of(new Obligation("mask",Map.of()))),Decision.indeterminate("offline"))) {
      DECISION.set(decision);
      assertEquals(403,signed("GET","/api/v1/staff/masters/CURRENCY",null,token(Map.of()),ACTOR.toString()).statusCode());
    }
    assertEquals(0,IDENTITIES.get()); assertEquals(0,ACCESS.get());
  }
  @Test void sessionUsernameCannotOverrideSignedActor() throws Exception {
    var response=signed("POST","/api/v1/staff/sessions","{\"username\":\"someone-else\"}",token(Map.of()),null);
    assertEquals(200,response.statusCode(),response.body()); assertTrue(response.body().contains(ACTOR.toString()));
    assertEquals("terminal.insurance.session",POLICY_CALLS.getFirst().get("action")); assertEquals(0,AUTHENTICATES.get());
    assertEquals(1,IDENTITIES.get()); assertEquals(1,ACCESS.get());
  }
  @Test void resolverCannotRemapSignedActor() throws Exception {
    REMAP.set(TENANT);
    var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token(Map.of()),ACTOR.toString());
    assertEquals(403,response.statusCode(),response.body()); assertTrue(response.body().contains("ACTOR_MISMATCH"));
    assertEquals(1,IDENTITIES.get()); assertEquals(0,ACCESS.get());
  }
  @Test void signedTokensNeedIssuerAudienceFiniteExpiryAndExactActorTenantClaims() throws Exception {
    for(Map<String,Object> override : List.<Map<String,Object>>of(
        Map.of("iss","https://other.invalid"),Map.of("aud","other"),Map.of("exp","missing"),
        Map.of("exp",Date.from(Instant.now().minusSeconds(300))),Map.of("staff_actor_id","missing"),
        Map.of("tenant_id","missing"),Map.of("staff_actor_id","not-a-uuid"),Map.of("sub",""))) {
      var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token(override),ACTOR.toString());
      assertEquals(401,response.statusCode(),override+" "+response.body());
    }
    assertEquals(0,IDENTITIES.get()); assertEquals(0,ACCESS.get()); assertTrue(POLICY_CALLS.isEmpty());
  }
  @Test void wrongRsaSignatureIsDenied() throws Exception {
    var key=new RSAKeyGenerator(2048).keyID(KEY.getKeyID()).generate();
    var jwt=SignedJWT.parse(token(Map.of()));
    var forged=new SignedJWT(jwt.getHeader(),jwt.getJWTClaimsSet());forged.sign(new RSASSASigner(key));
    assertEquals(401,signed("GET","/api/v1/staff/masters/CURRENCY",null,forged.serialize(),ACTOR.toString()).statusCode());
    assertEquals(0,IDENTITIES.get());
  }

  @Test void policyFailureHasNoBusinessEffectsOrInternalDetails() throws Exception {
    POLICY_FAILURE.set("error");
    var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token(Map.of()),ACTOR.toString());
    assertEquals(403,response.statusCode(),response.body());
    assertFalse(response.body().contains("synthetic-internal-detail")); assertEquals(0,IDENTITIES.get());
  }
  @Test void legacyBusinessDenialRetainsValidJsonReasonCode() throws Exception {
    DENIAL.set("POLICY_\"BLOCKED\"\nSYNTHETIC");
    var response=signed("GET","/api/v1/staff/masters/CURRENCY",null,token(Map.of()),ACTOR.toString());
    assertEquals(403,response.statusCode(),response.body());
    var parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
    assertEquals(DENIAL.get(),parsed.get("reasonCode").asText());
  }
  @Test void sessionAlsoRejectsResolverActorRemapping() throws Exception {
    REMAP.set(TENANT);
    var response=signed("POST","/api/v1/staff/sessions","{\"username\":\"other\"}",token(Map.of()),ACTOR.toString());
    assertEquals(403,response.statusCode(),response.body()); assertEquals(0,AUTHENTICATES.get()); assertEquals(0,ACCESS.get());
  }

  HttpResponse<String> signed(String method,String path,String body,String token,String actor) throws Exception {
    var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer "+token)
      .header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
    if(actor!=null)b.header("X-Actor-Id",actor);
    return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
  }
  String token(Map<String,Object> overrides) throws Exception {
    var claims=new LinkedHashMap<String,Object>();
    claims.put("iss",ISSUER);claims.put("aud",List.of("staff-insurance"));claims.put("sub","staff-subject-not-a-uuid");
    claims.put("exp",Date.from(Instant.now().plusSeconds(300)));claims.put("iat",Date.from(Instant.now()));
    claims.put("staff_actor_id",ACTOR.toString());claims.put("tenant_id",TENANT.toString());
    overrides.forEach((k,v)->{if("missing".equals(v))claims.remove(k);else claims.put(k,v);});
    var builder=new JWTClaimsSet.Builder();claims.forEach(builder::claim);
    var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),builder.build());
    jwt.sign(new RSASSASigner(KEY));return jwt.serialize();
  }

  HttpResponse<String> send(String method, String path, String body) throws Exception {
    return HttpClient.newHttpClient().send(request(method,path,body).build(), HttpResponse.BodyHandlers.ofString());
  }
  HttpRequest.Builder request(String method, String path, String body) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
        .header("X-Actor-Id", ACTOR.toString()).header("Content-Type", "application/json")
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
  }
}
