package io.fireflysoftware.experience.insurance.terminal;

import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(classes=TerminalDefaultDenyHttpTest.Application.class,
  webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties={"server.address=127.0.0.1","firefly.eda.enabled=false","firefly.stepevents.enabled=false"})
class TerminalDefaultDenyHttpTest {
  static final AtomicInteger EFFECTS=new AtomicInteger();
  static final RSAKey KEY;
  static final HttpServer JWKS;
  static {
    try {
      KEY=new RSAKeyGenerator(2048).keyID("synthetic-default-deny").generate();
      JWKS=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
      JWKS.createContext("/jwks",x->{
        var bytes=new JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);
        try(var out=x.getResponseBody()){out.write(bytes);}
      });JWKS.start();
    }catch(Exception e){throw new ExceptionInInitializerError(e);}
  }
  @DynamicPropertySource static void trust(DynamicPropertyRegistry p){
    p.add("firefly.terminal.security.issuer",()->"https://synthetic.default.invalid");
    p.add("firefly.terminal.security.audience",()->"staff-insurance");
    p.add("firefly.terminal.security.jwk-set-uri",()->"http://127.0.0.1:"+JWKS.getAddress().getPort()+"/jwks");
  }
  @AfterAll static void stop(){JWKS.stop(0);}
  @BeforeEach void reset(){EFFECTS.set(0);}
  @LocalServerPort int port;
  @SpringBootConfiguration @EnableAutoConfiguration
  @Import({TerminalSecurityConfiguration.class,Probe.class})
  static class Application{}
  @RestController static class Probe{
    @GetMapping({"/api/v1/staff/probe","/unclassified-probe"}) String get(){EFFECTS.incrementAndGet();return "must-not-run";}
  }
  @Test void nativeJaflyPolicyDeniesWithoutProjectRules() throws Exception{
    var response=send("/api/v1/staff/probe",token());
    assertEquals(403,response.statusCode(),response.body());assertTrue(response.body().contains("STAFF_ACCESS_DENIED"));
    assertEquals(0,EFFECTS.get());
  }
  @Test void unmatchedRoutesCannotBecomeAnonymousOrAuthenticatedBypass() throws Exception{
    assertEquals(401,send("/unclassified-probe",null).statusCode());
    assertEquals(403,send("/unclassified-probe",token()).statusCode());assertEquals(0,EFFECTS.get());
  }
  @Test void healthHasNoStaffDataOrAuthority() throws Exception{
    var response=send("/actuator/health",null);assertEquals(200,response.statusCode(),response.body());
    assertEquals(0,EFFECTS.get());
  }
  HttpResponse<String> send(String path,String token) throws Exception{
    var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET();
    if(token!=null)b.header("Authorization","Bearer "+token);
    return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
  }
  static String token() throws Exception{
    var claims=new JWTClaimsSet.Builder().issuer("https://synthetic.default.invalid").audience("staff-insurance")
      .subject("synthetic-staff").expirationTime(Date.from(Instant.now().plusSeconds(120)))
      .claim("staff_actor_id","33333333-3333-4333-8333-333333333333")
      .claim("tenant_id","44444444-4444-4444-8444-444444444444").build();
    var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),claims);
    jwt.sign(new RSASSASigner(KEY));return jwt.serialize();
  }
}
