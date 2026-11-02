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

import io.fireflysoftware.experience.insurance.terminal.security.impl.ScopedStaffBearerWebFilterImpl;
import java.time.Clock;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.rs.AccessTokenValidators;
import org.fireflyframework.security.spi.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/** Private legacy receiver authentication leaves the native global chain and decoder configured independently. */
@Configuration(proxyBeanMethods=false)
public class TerminalSecurityConfiguration {
  @Bean
  @Order(-50)
  WebFilter terminalSecurity(ObjectProvider<PrincipalFactory> principals,
      ObjectProvider<SecurityContextPort> contexts,ObjectProvider<PolicyDecisionPort> policies,
      @Value("${firefly.terminal.security.issuer:}") String issuer,
      @Value("${firefly.terminal.security.audience:}") String audience,
      @Value("${firefly.terminal.security.jwk-set-uri:}") String jwks,
      @Value("${firefly.security.resource-server.strict-policy:false}") boolean strictPolicy) {
    var clock=Clock.systemUTC();
    ServerWebExchangeMatcher routes=exchange->{
      String path=exchange.getRequest().getPath().pathWithinApplication().value();
      return legacy(path)?ServerWebExchangeMatcher.MatchResult.match():ServerWebExchangeMatcher.MatchResult.notMatch();
    };
    var receiver=new ScopedStaffBearerWebFilterImpl(routes,decoder(issuer,audience,jwks),jwt->{
      var factory=principals.getIfAvailable();
      if(factory==null)return Mono.error(new BadJwtException("Native staff principal projection is not configured"));
      return new TerminalAuthentication(factory,clock).convert(jwt).cast(AbstractAuthenticationToken.class);
    });
    return (exchange,chain)->receiver.filter(exchange,authenticated->{
      if(!legacy(authenticated.getRequest().getPath().pathWithinApplication().value()))return chain.filter(authenticated);
      var context=contexts.getIfAvailable();var policy=policies.getIfAvailable();
      if(context==null||policy==null){authenticated.getResponse().setStatusCode(HttpStatus.FORBIDDEN);return authenticated.getResponse().setComplete();}
      return new TerminalAccessFilterImpl(context,policy,clock,strictPolicy).filter(authenticated,chain);
    });
  }

  private static boolean legacy(String path) {
    return (path.equals("/api/v1/staff")||path.startsWith("/api/v1/staff/"))
        &&!path.equals("/api/v1/staff/underwriting")&&!path.startsWith("/api/v1/staff/underwriting/");
  }

  private static ReactiveJwtDecoder decoder(String issuer,String audience,String jwks) {
    if(issuer.isBlank()||audience.isBlank()||jwks.isBlank())
      return token->Mono.error(new BadJwtException("Staff trust is not configured"));
    var decoder=NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(AccessTokenValidators.forIssuerAndAudience(issuer,audience));return decoder;
  }
}
