/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.insurance.terminal;

import java.util.Set;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Controlled native URL policy is separate from the legacy current business-policy assertions. */
final class NativeTerminalFixtureTrust {
    private NativeTerminalFixtureTrust(){}
    static void properties(DynamicPropertyRegistry registry,String issuer,String audience,String jwks){
        registry.add("firefly.security.resource-server.enabled",()->"true");
        registry.add("firefly.security.resource-server.issuer",()->issuer);
        registry.add("firefly.security.resource-server.audiences[0]",()->audience);
        registry.add("firefly.security.resource-server.jwk-set-uri",()->jwks);
        registry.add("firefly.security.resource-server.tenant-required",()->"true");
        registry.add("firefly.security.resource-server.tenant-claim-paths[0]",()->"tenant_id");
        registry.add("firefly.security.resource-server.permit-matchers[0]",()->"/actuator/health");
        registry.add("firefly.security.resource-server.permit-matchers[1]",()->"/v3/api-docs");
        registry.add("firefly.security.resource-server.permit-matchers[2]",()->"/v3/api-docs.yaml");
    }
    static boolean urlAction(String action){return Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS","TRACE").contains(action);}
    static Decision boundaryUrl(SecurityPrincipal principal,String action,String path){
        boolean route=Set.of("GET /api/v1/staff/masters/CURRENCY","GET /api/v1/staff/records/POLICY","POST /api/v1/staff/sessions",
            "PUT /api/v1/staff/records/POLICY/"+InsuranceArea.POLICY.primaryId()).contains(action+" "+path);
        return signed(principal,TerminalBoundaryHttpTest.ISSUER,TerminalBoundaryHttpTest.TENANT.toString())&&route
            ?Decision.permit():Decision.deny("Controlled native URL policy refused");
    }
    static boolean signed(SecurityPrincipal principal,String issuer,String tenant){return principal!=null&&issuer.equals(principal.issuer())
        &&tenant.equals(principal.tenantId())&&principal.subject()!=null&&!principal.subject().isBlank();}
}
