/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fireflysoftware.experience.insurance.terminal;

import java.time.Clock;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.rs.AccessTokenValidators;
import org.fireflyframework.security.spi.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods=false)
public class TerminalSecurityConfiguration {
  @Bean
  ReactiveJwtDecoder terminalJwtDecoder(
      @Value("${firefly.terminal.security.issuer:}") String issuer,
      @Value("${firefly.terminal.security.audience:}") String audience,
      @Value("${firefly.terminal.security.jwk-set-uri:}") String jwks) {
    if (issuer.isBlank() || audience.isBlank() || jwks.isBlank())
      return token -> Mono.error(new BadJwtException("Staff trust is not configured"));
    var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(AccessTokenValidators.forIssuerAndAudience(issuer,audience));
    return decoder;
  }

  @Bean
  @Order(-100)
  SecurityWebFilterChain terminalSecurity(ServerHttpSecurity http, ReactiveJwtDecoder decoder,
      PrincipalFactory principals, SecurityContextPort contexts, PolicyDecisionPort policies,
      @Value("${firefly.security.resource-server.strict-policy:false}") boolean strictPolicy) {
    var clock=Clock.systemUTC();
    var authentication=new TerminalAuthentication(principals,clock);
    return http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .authorizeExchange(exchanges -> exchanges
          .pathMatchers(HttpMethod.GET,"/v3/api-docs","/v3/api-docs.yaml","/v3/api-docs/**","/actuator/health").permitAll()
          .pathMatchers("/api/v1/staff","/api/v1/staff/**").authenticated()
          .anyExchange().denyAll())
        .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtDecoder(decoder)
          .jwtAuthenticationConverter(token -> authentication.convert(token).cast(AbstractAuthenticationToken.class))))
        .addFilterBefore(new TerminalAccessFilter(contexts,policies,clock,strictPolicy),SecurityWebFiltersOrder.AUTHORIZATION)
        .build();
  }
}
