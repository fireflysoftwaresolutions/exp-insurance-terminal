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

import java.time.*;
import java.util.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/** Projects only signature-validated staff claims; provider subject and employee UUID are distinct. */
final class TerminalAuthentication {
  private final PrincipalFactory principals;
  private final Clock clock;

  TerminalAuthentication(PrincipalFactory principals, Clock clock) {
    this.principals = Objects.requireNonNull(principals);
    this.clock = Objects.requireNonNull(clock);
  }

  Mono<JwtAuthenticationToken> convert(Jwt jwt) {
    return Mono.fromCallable(() -> {
      if (jwt == null || jwt.getIssuer() == null || !text(jwt.getSubject(),255)
          || !future(jwt.getExpiresAt(),clock) || jwt.getTokenValue() == null
          || jwt.getTokenValue().length() > 16384) throw invalid();
      String actor=uuid(jwt.getClaim("staff_actor_id"));
      String tenant=uuid(jwt.getClaim("tenant_id"));
      var principal=principals.fromClaims(jwt.getSubject(),jwt.getIssuer().toString(),jwt.getClaims())
          .toBuilder().tenantId(tenant).attributes(Map.of("staffActorId",actor)).build();
      return new JwtAuthenticationToken(jwt,principal,
          principal.authorities().stream().map(SimpleGrantedAuthority::new).toList());
    }).onErrorMap(error -> invalid());
  }

  static boolean valid(SecurityPrincipal principal, Clock clock) {
    return principal != null && text(principal.subject(),255)
        && principal.claims().get("exp") instanceof Instant expiry && future(expiry,clock);
  }

  static String uuid(Object claim) {
    if (!(claim instanceof String value) || value.length()!=36
        || !UUID.fromString(value).toString().equals(value)) throw invalid();
    return value;
  }

  private static boolean future(Instant expiry, Clock clock) {
    return expiry != null && expiry.isAfter(clock.instant())
        && !expiry.isAfter(Instant.parse("9999-12-31T23:59:59Z"));
  }

  private static boolean text(String value,int limit) {
    return value!=null && !value.isBlank() && value.length()<=limit
        && !value.codePoints().anyMatch(c -> Character.isISOControl(c)
          || c>=0xd800 && c<=0xdfff || c==0xfffd);
  }

  private static OAuth2AuthenticationException invalid() {
    return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),"Invalid staff authentication");
  }
}
