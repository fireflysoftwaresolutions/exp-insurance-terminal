package io.fireflysoftware.experience.insurance.terminal;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes=TerminalBoundaryHttpTest.Application.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"server.address=127.0.0.1","firefly.eda.enabled=false","firefly.stepevents.enabled=false"})
class TerminalOpenApiContractTest {
  @LocalServerPort int port;
  @Test void nativeProducerRetainsHistoricSdkNamesAndPublishesBearerTrust() throws Exception {
    var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
    assertEquals(200,response.statusCode(),response.body());
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/native-terminal-openapi.json"),response.body());
    var actual=new ObjectMapper().readTree(response.body());
    var legacy=new ObjectMapper(new YAMLFactory()).readTree(getClass().getResourceAsStream("/legacy-terminal-openapi.yml"));
    var paths=legacy.path("paths").fields();
    while(paths.hasNext()){
      var path=paths.next();var methods=path.getValue().fields();
      while(methods.hasNext()){
        var method=methods.next();var original=method.getValue();var operation=actual.path("paths").path(path.getKey()).path(method.getKey());
        assertEquals(original.path("operationId").asText(),operation.path("operationId").asText(),path.getKey());
        assertEquals(original.path("tags"),operation.path("tags"),path.getKey());
        for(var parameter:operation.path("parameters")){
          String name=parameter.path("name").asText();
          assertFalse(Set.of("principal","authentication","jwt","tenantId","staff_actor_id").contains(name),name);
          if(name.equals("X-Actor-Id"))assertFalse(parameter.path("required").asBoolean(),"Actor header is compatibility input, not proof of identity");
        }
      }
    }
    for(String name:List.of("SignInRequest","PropertyBody","StaffSession")){
      assertTrue(actual.path("components").path("schemas").has(name),name);
    }
    var session=actual.path("paths").path("/api/v1/staff/sessions").path("post");
    assertTrue(session.path("parameters").isMissingNode()||session.path("parameters").isEmpty(),session.toString());
    var scheme=actual.path("components").path("securitySchemes").path("staffBearer");
    assertEquals("http",scheme.path("type").asText());assertEquals("bearer",scheme.path("scheme").asText());
    assertTrue(session.path("security").toString().contains("staffBearer"));
  }
}
