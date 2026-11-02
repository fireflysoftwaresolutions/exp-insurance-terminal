package io.fireflysoftware.experience.insurance.terminal;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes=TerminalMissingTrustHttpTest.Application.class,
  webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties={"server.address=127.0.0.1","firefly.terminal.security.issuer=","firefly.terminal.security.audience=",
    "firefly.terminal.security.jwk-set-uri=","firefly.eda.enabled=false","firefly.stepevents.enabled=false"})
class TerminalMissingTrustHttpTest {
  @SpringBootConfiguration @EnableAutoConfiguration
  @Import({TerminalSecurityConfiguration.class,TerminalDefaultDenyHttpTest.Probe.class})
  static class Application{}
  @LocalServerPort int port;
  @Test void missingTrustCannotUseTheFrameworkGeneratedLocalKeyAsFallback() throws Exception {
    TerminalDefaultDenyHttpTest.EFFECTS.set(0);
    var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/staff/probe"))
      .header("Authorization","Bearer "+TerminalDefaultDenyHttpTest.token()).GET().build();
    var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
    assertEquals(401,response.statusCode(),response.body());assertEquals(0,TerminalDefaultDenyHttpTest.EFFECTS.get());
  }
}
