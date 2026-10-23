package io.fireflysoftware.experience.insurance.terminal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/** Starts the production controller and wiring without scanning test fixture configurations. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Profile("openapi-gen")
@Import({StaffController.class, TerminalWiring.class, TerminalExceptionHandler.class,
    TerminalSecurityConfiguration.class})
public class OpenApiGenApplication {
  public static void main(String[] args) {
    SpringApplication.run(OpenApiGenApplication.class, args);
  }
}
