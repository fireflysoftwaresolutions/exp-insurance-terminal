/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.insurance.terminal.staff;

import io.fireflysoftware.experience.insurance.terminal.staff.StaffUnderwritingSettings;
import io.fireflysoftware.experience.insurance.terminal.staff.impl.ExchangedStaffUnderwritingClientFactoryImpl;
import io.fireflysoftware.experience.insurance.terminal.staff.impl.*;
import java.time.Clock;
import io.fireflysoftware.experience.insurance.terminal.security.impl.ScopedStaffBearerWebFilterImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(StaffUnderwritingSettings.class)
public class StaffUnderwritingSecurityConfiguration {
    @Bean @Order(-50)
    WebFilter staffUnderwritingSecurity(StaffUnderwritingSettings settings,ObjectProvider<PrincipalFactory> principals){
        ReactiveJwtDecoder decoder=settings.enabled()?ExchangedStaffUnderwritingClientFactoryImpl.decoder(settings,settings.experienceAudience()):
            token->Mono.error(new JwtException("STAFF underwriting trust is not configured"));
        return new ScopedStaffBearerWebFilterImpl(ServerWebExchangeMatchers.pathMatchers("/api/v1/staff/underwriting","/api/v1/staff/underwriting/**"),decoder,token->{
            var factory=principals.getIfAvailable();if(factory==null)return Mono.error(new JwtException("Native STAFF principal projection is not configured"));
            return new StaffUnderwritingJwtConverterImpl(settings,factory,Clock.systemUTC()).convert(token);
        });
    }
}
