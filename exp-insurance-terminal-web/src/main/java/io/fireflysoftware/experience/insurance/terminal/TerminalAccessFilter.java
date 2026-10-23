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

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.fireflyframework.security.spi.*;
import org.springframework.http.*;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Runs inside the security chain, before any controller or response replay cache. */
final class TerminalAccessFilter implements WebFilter {
  private final SecurityContextPort contexts;
  private final PolicyDecisionPort policies;
  private final Clock clock;
  private final boolean strictPolicy;

  TerminalAccessFilter(SecurityContextPort contexts,PolicyDecisionPort policies,Clock clock,boolean strictPolicy) {
    this.contexts=Objects.requireNonNull(contexts);
    this.policies=Objects.requireNonNull(policies);
    this.clock=Objects.requireNonNull(clock);
    this.strictPolicy=strictPolicy;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain) {
    String path=exchange.getRequest().getPath().pathWithinApplication().value();
    if (!path.equals("/api/v1/staff") && !path.startsWith("/api/v1/staff/"))
      return chain.filter(exchange);
    return authorizedActor(exchange,path)
      .flatMap(actor -> chain.filter(exchange.mutate().request(request -> request
          .headers(headers -> headers.set("X-Actor-Id",actor))).build()))
      .onErrorResume(TerminalDeniedException.class,error -> {
        var response=exchange.getResponse();
        if(response.isCommitted()) return Mono.error(error);
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes=("{\"reasonCode\":\""+error.reasonCode()+"\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
      });
  }

  private Mono<String> authorizedActor(ServerWebExchange exchange,String path) {
    return contexts.currentPrincipal()
      .filter(principal -> TerminalAuthentication.valid(principal,clock))
      .switchIfEmpty(Mono.error(new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Staff authentication is required")))
      .flatMap(principal -> {
        if (!strictPolicy) return Mono.error(new TerminalDeniedException("STAFF_STRICT_POLICY_REQUIRED"));
        String actor;
        String tenant;
        try {
          actor=TerminalAuthentication.uuid(principal.attributes().get("staffActorId"));
          tenant=TerminalAuthentication.uuid(principal.tenantId());
          var header=exchange.getRequest().getHeaders().get("X-Actor-Id");
          if(header!=null && (header.size()!=1 || !actor.equals(TerminalAuthentication.uuid(header.getFirst()))))
            return Mono.error(new TerminalDeniedException("STAFF_ACTOR_MISMATCH"));
        } catch(Exception error) {
          return Mono.error(new TerminalDeniedException("STAFF_ACTOR_MISMATCH"));
        }
        String method=exchange.getRequest().getMethod().name();
        String operation=path.equals("/api/v1/staff/sessions") && method.equals("POST")
            ? "session" : method.equals("GET") ? "read" : "manage";
        return Mono.defer(() -> policies.authorize(principal,"terminal.insurance."+operation,
            "tenant/"+tenant+"/terminal/insurance"+path,
            Map.of("tenantId",tenant,"actorId",actor,"method",method,"path",path,"terminal","insurance")))
          .timeout(Duration.ofSeconds(5))
          .filter(decision -> decision.granted() && decision.obligations().isEmpty())
          .filter(decision -> TerminalAuthentication.valid(principal,clock))
          .map(decision -> actor)
          .switchIfEmpty(Mono.error(new TerminalDeniedException("STAFF_ACCESS_DENIED")))
          .onErrorMap(error -> new TerminalDeniedException("STAFF_ACCESS_DENIED"));
      });
  }
}
