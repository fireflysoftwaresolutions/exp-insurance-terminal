/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.insurance.terminal.staff;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.experience.insurance.terminal.TerminalSecurityConfiguration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.policy.PolicyRule;
import org.fireflyframework.security.rs.ResourceServerAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;

/** Actual global/native receiver crypto over controlled metadata routes; no owner business acceptance. */
class StaffReceiverGlobalSecurityTest {
    private static final String ISSUER="https://receiver.invalid",SUBJECT="opaque:original-staff",TENANT="opaque:terminal:tenant";
    private static final String STAFF="019a3497-6099-789a-9234-c34d4549e128",SCOPE="insurance:underwriting:referrals:create";
    private static final String UNRELATED="/unrelated-terminal-receiver",SCOPED="/api/v1/staff/underwriting/receiver-probe";
    private static final KeyPair KEYS=keys();
    private static final RSAKey JWK=new RSAKey.Builder((RSAPublicKey)KEYS.getPublic()).privateKey((RSAPrivateKey)KEYS.getPrivate()).keyID("terminal-receiver").build();
    private static final HttpServer JWKS=jwks();
    private ReactiveWebApplicationContextRunner receiver(String profile,boolean staff){return new ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ResourceServerAutoConfiguration.class))
        .withUserConfiguration(ReceiverFixtureImpl.class,TerminalSecurityConfiguration.class,StaffUnderwritingSecurityConfiguration.class)
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.profiles.active="+profile,"firefly.security.resource-server.enabled=true",
            "firefly.security.resource-server.issuer="+ISSUER,"firefly.security.resource-server.audiences[0]=experience",
            "firefly.security.resource-server.tenant-required=true","firefly.security.resource-server.tenant-claim-paths[0]=tenant_id",
            "firefly.security.resource-server.scope-claim-paths[0]=scope","firefly.security.resource-server.strict-policy=true",
            "firefly.security.resource-server.permit-matchers[0]=/actuator/health",
            "insurance.terminal.staff-client.enabled="+staff,"insurance.terminal.staff-client.endpoint=https://domain.invalid",
            "insurance.terminal.staff-client.issuer="+ISSUER,"insurance.terminal.staff-client.jwk-set-uri=http://127.0.0.1:"+JWKS.getAddress().getPort()+"/jwks",
            "insurance.terminal.staff-client.experience-audience=experience","insurance.terminal.staff-client.domain-audience=domain",
            "insurance.terminal.staff-client.inbound-client-id=registered-staff-client","insurance.terminal.staff-client.delegating-client-id=experience-client",
            "insurance.terminal.staff-client.registration-id=staff-exchange","insurance.terminal.staff-client.platform-tenant="+TENANT,
            "insurance.terminal.staff-client.allow-loopback-http=true");}
    @Test void scopedConfigurationNeverSuppressesNativeGlobalAuthentication(){
        for(String profile:List.of("default","dev","testing","prod"))receiver(profile,false).run(context->{
            assertThat(context).hasNotFailed().hasSingleBean(SecurityWebFilterChain.class).hasSingleBean(ReactiveJwtDecoder.class)
                .hasBean("fireflySecurityWebFilterChain").hasBean("terminalSecurity").hasBean("staffUnderwritingSecurity").doesNotHaveBean("terminalJwtDecoder");
            var client=WebTestClient.bindToApplicationContext(context).build();
            client.get().uri(UNRELATED).exchange().expectStatus().isUnauthorized();
            client.get().uri(UNRELATED).headers(h->h.setBearerAuth(token(false))).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("native-global");
        });
    }
    @Test void sameIssuerSignedStaffOpaqueProjectionWorksAlongsideTheActualGlobalChain(){
        receiver("prod",true).run(context->{
            assertThat(context).hasNotFailed().hasSingleBean(SecurityWebFilterChain.class).hasBean("fireflySecurityWebFilterChain");
            var client=WebTestClient.bindToApplicationContext(context).build();
            client.get().uri(SCOPED).headers(h->h.setBearerAuth(token(true))).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("native-staff");
            client.get().uri(UNRELATED).headers(h->h.setBearerAuth(token(false))).exchange().expectStatus().isOk();
        });
    }
    @Test void changedSignatureOrNonStaffTokenCannotAcquireTheScopedProjection(){
        receiver("prod",true).run(context->{
            var client=WebTestClient.bindToApplicationContext(context).build();
            client.get().uri(SCOPED).exchange().expectStatus().isUnauthorized();
            client.get().uri(SCOPED).headers(h->h.setBearerAuth(token(false))).exchange().expectStatus().isForbidden();
            String[] parts=token(true).split("\\.",-1);parts[2]=(parts[2].charAt(0)=='A'?"B":"A")+parts[2].substring(1);
            client.get().uri(SCOPED).headers(h->h.setBearerAuth(String.join(".",parts))).exchange().expectStatus().isUnauthorized();
        });
    }
    @Test void globalFeatureCanOnlyBeDisabledExplicitly(){receiver("prod",true).withPropertyValues("firefly.security.resource-server.enabled=false")
        .run(context->assertThat(context).hasNotFailed().doesNotHaveBean(SecurityWebFilterChain.class));}
    private static KeyPair keys(){try{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();}catch(GeneralSecurityException error){throw new IllegalStateException(error);}}
    private static HttpServer jwks(){try{var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/jwks",request->{
        byte[] body=new JWKSet(JWK.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);request.getResponseHeaders().set("Content-Type","application/json");
        request.sendResponseHeaders(200,body.length);try(var out=request.getResponseBody()){out.write(body);}request.close();});server.start();return server;}catch(Exception error){throw new IllegalStateException(error);}}
    @AfterAll static void stop(){JWKS.stop(0);}
    private static String token(boolean staff){var now=Instant.now();var claims=JwtClaimsSet.builder().issuer(ISSUER).subject(SUBJECT).audience(List.of("experience"))
        .notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(60)).claim("principal_kind",staff?"STAFF":"HUMAN").claim("tenant_id",TENANT)
        .claim("client_id","registered-staff-client").claim("staff_actor_id",STAFF).claim("scope",staff?SCOPE:"receiver:read").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(JWK))).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("terminal-receiver").build(),claims)).getTokenValue();}
    @TestConfiguration(proxyBeanMethods=false) @EnableWebFlux static class ReceiverFixtureImpl {
        @Bean ReactiveJwtDecoder nativeReceiverDecoder(){var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)KEYS.getPublic()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),jwt->List.of("experience").equals(jwt.getAudience())?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));return decoder;}
        @Bean PolicyRule receiverRule(){return (actor,action,resource,facts)->Mono.just(ISSUER.equals(actor.issuer())&&SUBJECT.equals(actor.subject())&&TENANT.equals(actor.tenantId())&&action.equals("GET")
            &&(resource.equals(UNRELATED)&&actor.hasScope("receiver:read")||resource.equals(SCOPED)&&actor.hasScope(SCOPE)&&"STAFF".equals(actor.claims().get("principal_kind")))?Decision.permit():Decision.deny("controlled receiver refusal"));}
        @Bean ReceiverControllerImpl receiverController(){return new ReceiverControllerImpl();}
    }
    @RestController static class ReceiverControllerImpl {
        @GetMapping(value=UNRELATED,produces=MediaType.TEXT_PLAIN_VALUE) Mono<String> unrelated(){return Mono.just("native-global");}
        @GetMapping(value=SCOPED,produces=MediaType.TEXT_PLAIN_VALUE) Mono<String> scoped(){return ReactiveSecurityContextHolder.getContext().map(c->c.getAuthentication())
            .map(authentication->{var principal=(SecurityPrincipal)authentication.getPrincipal();
                if(!STAFF.equals(principal.attributes().get("staffActorId"))||!TENANT.equals(principal.tenantId())||!SUBJECT.equals(principal.subject()))throw new IllegalStateException("Exact native STAFF projection required");
                return "native-staff";});}
    }
}
