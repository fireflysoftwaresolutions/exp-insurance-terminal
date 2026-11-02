package io.fireflysoftware.experience.insurance.terminal;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.test.context.*;
import org.springframework.web.server.WebFilter;

@SpringBootTest(classes = {TerminalBoundaryHttpTest.Application.class,
    TerminalSessionAuthorityHttpTest.SessionWriter.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"server.address=127.0.0.1", "firefly.eda.enabled=false", "firefly.stepevents.enabled=false"})
class TerminalSessionAuthorityHttpTest {
  @LocalServerPort int port;

  @DynamicPropertySource static void trust(DynamicPropertyRegistry registry) {
    TerminalBoundaryHttpTest.trust(registry);
  }
  @AfterAll static void stopJwks() { TerminalBoundaryHttpTest.stop(); }
  @BeforeEach void reset() {
    TerminalBoundaryHttpTest.IDENTITIES.set(0);
    TerminalBoundaryHttpTest.ACCESS.set(0);
    TerminalBoundaryHttpTest.AUTHENTICATES.set(0);
    TerminalBoundaryHttpTest.DECISION.set(org.fireflyframework.security.api.domain.Decision.permit());
    TerminalBoundaryHttpTest.POLICY_CALLS.clear();
    TerminalBoundaryHttpTest.DENIAL.set(null);
    TerminalBoundaryHttpTest.POLICY_FAILURE.set(null);
    TerminalBoundaryHttpTest.REMAP.set(null);
  }

  @Configuration(proxyBeanMethods=false)
  static class SessionWriter {
    @Bean @Order(Ordered.HIGHEST_PRECEDENCE)
    WebFilter seedVerifiedStaffSession(ReactiveJwtDecoder decoder, PrincipalFactory principals) {
      return (exchange, chain) -> {
        if (!exchange.getRequest().getPath().value().equals("/v3/api-docs")
            || !"true".equals(exchange.getRequest().getQueryParams().getFirst("ambient_seed")))
          return chain.filter(exchange);
        return reactor.core.publisher.Mono.fromCallable(() -> signedToken("ambient-session"))
            .flatMap(decoder::decode)
            .flatMap(jwt -> new TerminalAuthentication(principals,Clock.systemUTC()).convert(jwt))
            .flatMap(authentication -> new WebSessionServerSecurityContextRepository()
                .save(exchange,new SecurityContextImpl(authentication)))
            .then(reactor.core.publisher.Mono.defer(() -> chain.filter(exchange)));
      };
    }
  }

  static String signedToken(String subject) throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(TerminalBoundaryHttpTest.ISSUER)
        .audience("staff-insurance").subject(subject)
        .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(120)))
        .claim("staff_actor_id",TerminalBoundaryHttpTest.ACTOR.toString())
        .claim("tenant_id",TerminalBoundaryHttpTest.TENANT.toString()).build();
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
        .keyID(TerminalBoundaryHttpTest.KEY.getKeyID()).build(),claims);
    jwt.sign(new RSASSASigner(TerminalBoundaryHttpTest.KEY)); return jwt.serialize();
  }
  HttpResponse<String> send(String method,String path,String body,String... headers) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
        .header("Content-Type","application/json")
        .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
    for(int i=0;i<headers.length;i+=2) request.header(headers[i],headers[i+1]);
    return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
  }
  String verifiedSession() throws Exception {
    var response = send("GET","/v3/api-docs?ambient_seed=true",null);
    assertEquals(200,response.statusCode());
    return response.headers().allValues("Set-Cookie").stream().filter(value -> value.startsWith("SESSION="))
        .map(value -> value.split(";",2)[0]).findFirst().orElseThrow();
  }
  void noBusinessEffects() {
    assertEquals(0,TerminalBoundaryHttpTest.IDENTITIES.get());
    assertEquals(0,TerminalBoundaryHttpTest.ACCESS.get());
    assertEquals(0,TerminalBoundaryHttpTest.AUTHENTICATES.get());
    assertTrue(TerminalBoundaryHttpTest.POLICY_CALLS.isEmpty());
  }
  @Test void retainedAuthenticatedSessionCannotReadStaffData() throws Exception {
    String cookie = verifiedSession();
    var response=send("GET","/api/v1/staff/masters/CURRENCY",null,"Cookie",cookie);
    assertEquals(401,response.statusCode()); noBusinessEffects();
  }
  @Test void retainedSessionCannotPerformUnsafeStaffOperationWithoutBearer() throws Exception {
    String cookie = verifiedSession();
    var response=send("POST","/api/v1/staff/sessions","{\"username\":\"sample-employee\"}","Cookie",cookie);
    assertEquals(403,response.statusCode()); noBusinessEffects();
  }
  @Test void signedBearerWinsOverRetainedSessionAndStillUsesCurrentPolicy() throws Exception {
    String cookie=verifiedSession(); String token=signedToken("explicit-header-staff");
    var response=send("POST","/api/v1/staff/sessions","{\"username\":\"sample-employee\"}",
        "Cookie",cookie,"Authorization","Bearer "+token);
    assertEquals(200,response.statusCode());
    var principal=(org.fireflyframework.security.api.domain.SecurityPrincipal)
        TerminalBoundaryHttpTest.POLICY_CALLS.getFirst().get("principal");
    assertEquals("explicit-header-staff",principal.subject());
    int effects=TerminalBoundaryHttpTest.IDENTITIES.get(); assertTrue(effects>0);
    TerminalBoundaryHttpTest.DECISION.set(org.fireflyframework.security.api.domain.Decision.deny("synthetic withdrawal"));
    assertEquals(403,send("POST","/api/v1/staff/sessions","{\"username\":\"sample-employee\"}",
        "Cookie",cookie,"Authorization","Bearer "+token).statusCode());
    assertEquals(effects,TerminalBoundaryHttpTest.IDENTITIES.get());
  }
  @Test void invalidBearerCannotBorrowVerifiedSession() throws Exception {
    String cookie=verifiedSession();
    assertEquals(401,send("GET","/api/v1/staff/masters/CURRENCY",null,
        "Cookie",cookie,"Authorization","Bearer invalid-terminal-token").statusCode());
    noBusinessEffects();
  }
}
