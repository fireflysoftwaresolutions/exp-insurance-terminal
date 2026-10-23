package io.fireflysoftware.experience.insurance.terminal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;

abstract class TerminalStrictPolicyGuardFixture {
  @LocalServerPort int port;
  @DynamicPropertySource static void trust(DynamicPropertyRegistry registry) {
    TerminalBoundaryHttpTest.trust(registry);
  }
  @AfterAll static void stop() { TerminalBoundaryHttpTest.stop(); }
  @Test void aPermittingProjectProviderCannotBypassRequiredStrictPolicyMode() throws Exception {
    var fixture=new TerminalBoundaryHttpTest();fixture.port=port;fixture.reset();
    var response=fixture.signed("GET","/api/v1/staff/masters/CURRENCY",null,fixture.token(Map.of()),null);
    assertEquals(403,response.statusCode(),response.body());
    assertTrue(response.body().contains("STAFF_STRICT_POLICY_REQUIRED"),response.body());
    assertEquals(0,TerminalBoundaryHttpTest.IDENTITIES.get());
    assertEquals(0,TerminalBoundaryHttpTest.ACCESS.get());
    assertTrue(TerminalBoundaryHttpTest.POLICY_CALLS.isEmpty());
  }
}

@SpringBootTest(classes=TerminalBoundaryHttpTest.Application.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"server.address=127.0.0.1","firefly.security.resource-server.strict-policy=false",
      "firefly.eda.enabled=false","firefly.stepevents.enabled=false"})
class TerminalStrictPolicyRequiredHttpTest extends TerminalStrictPolicyGuardFixture {}

@SpringBootTest(classes=TerminalBoundaryHttpTest.Application.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"server.address=127.0.0.1","spring.config.location=optional:classpath:/no-terminal-config.yaml",
      "firefly.eda.enabled=false","firefly.stepevents.enabled=false"})
class TerminalMissingStrictPolicyHttpTest extends TerminalStrictPolicyGuardFixture {}
