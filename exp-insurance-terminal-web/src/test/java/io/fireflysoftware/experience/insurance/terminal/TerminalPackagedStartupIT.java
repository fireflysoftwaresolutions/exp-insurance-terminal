package io.fireflysoftware.experience.insurance.terminal;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TerminalPackagedStartupIT {
  @Test void ordinaryExecutableStartsWithOnlyItsPackagedRuntimeAndPublishesItsContract() throws Exception {
    var jar=Path.of("target/exp-insurance-terminal.jar").toAbsolutePath();assertTrue(Files.isRegularFile(jar));
    var log=Path.of("target/packaged-startup.log");
    var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
        "-Xmx192m","-XX:MaxMetaspaceSize=192m","-jar",jar.toString(),
        "--server.address=127.0.0.1","--server.port=0","--spring.jmx.enabled=false",
        "--firefly.eda.enabled=false","--firefly.stepevents.enabled=false")
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      int port=0;var deadline=Instant.now().plusSeconds(45);
      var pattern=Pattern.compile("Netty started on port (\\d+)");
      while(Instant.now().isBefore(deadline)) {
        var content=Files.readString(log);var match=pattern.matcher(content);
        if(match.find()){port=Integer.parseInt(match.group(1));break;}
        assertTrue(process.isAlive(),content);Thread.sleep(100);
      }
      assertTrue(port>0,Files.readString(log));
      var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
      for(String path:new String[]{"/actuator/health","/v3/api-docs","/v3/api-docs.yaml"}) {
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),path+" "+response.body());
        if(path.startsWith("/v3/"))assertTrue(response.body().contains("staffBearer"),response.body());
      }
      var denied=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/staff/masters/CURRENCY"))
          .timeout(Duration.ofSeconds(5)).header("X-Actor-Id","33333333-3333-4333-8333-333333333333")
          .GET().build(),HttpResponse.BodyHandlers.ofString());
      assertEquals(401,denied.statusCode(),denied.body());
    } finally {
      process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(10,TimeUnit.SECONDS));}
    }
  }
}
