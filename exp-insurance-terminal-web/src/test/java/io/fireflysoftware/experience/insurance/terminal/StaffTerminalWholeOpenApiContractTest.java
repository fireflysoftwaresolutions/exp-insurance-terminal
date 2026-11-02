/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.insurance.terminal;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fireflysoftware.experience.insurance.terminal.staff.GovernedStaffUnderwritingService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/** Whole native metadata producer only; neither controlled service double supplies a business result. */
@ActiveProfiles("openapi-gen")
@SpringBootTest(classes=OpenApiGenApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={
    "spring.main.web-application-type=reactive","insurance.terminal.staff-client.enabled=false",
    "firefly.security.resource-server.enabled=true","firefly.eda.enabled=false","firefly.stepevents.enabled=false",
    "firefly.security.resource-server.permit-matchers[0]=/v3/api-docs",
    "firefly.security.resource-server.permit-matchers[1]=/v3/api-docs.yaml",
    "springdoc.api-docs.enabled=true","springdoc.api-docs.version=OPENAPI_3_1"})
class StaffTerminalWholeOpenApiContractTest {
    private static final Set<String> VERBS=Set.of("get","post","put","patch","delete","head","options","trace");
    @Autowired ApplicationContext context;
    @MockitoBean StaffTerminal legacy;
    @MockitoBean GovernedStaffUnderwritingService governed;

    @Test void completeOriginalProducerPreservesElevenOperationsAndAddsThreeTypedStaffRoutes()throws Exception{
        assertEquals(1,context.getBeansOfType(org.springframework.security.oauth2.jwt.ReactiveJwtDecoder.class).size());
        assertInstanceOf(org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder.class,context.getBean(org.springframework.security.oauth2.jwt.ReactiveJwtDecoder.class));
        assertTrue(context.containsBean("fireflySecurityWebFilterChain"));
        verifyNoInteractions(legacy,governed);
        var client=WebTestClient.bindToApplicationContext(context).configureClient().responseTimeout(Duration.ofSeconds(30))
            .codecs(codecs->codecs.defaultCodecs().maxInMemorySize(4*1024*1024)).build();
        byte[] json=client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();
        byte[] yaml=client.get().uri("/v3/api-docs.yaml").exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();
        assertNotNull(json);assertNotNull(yaml);assertTrue(json.length<=4*1024*1024);assertTrue(yaml.length<=4*1024*1024);
        var actual=new ObjectMapper().readTree(json);assertEquals(actual,new ObjectMapper(new YAMLFactory()).readTree(yaml));
        var canonical=Path.of("../exp-insurance-terminal-sdk/src/main/resources/openapi.yml");
        byte[] preimage=Files.readAllBytes(canonical);
        var original=new ObjectMapper(new YAMLFactory()).readTree(preimage);
        var before=operations(original);var after=operations(actual);
        var newRoutes=Map.of("post /api/v1/staff/underwriting/intents","prepareStaffUnderwritingIntent",
            "post /api/v1/staff/underwriting/actions","executeStaffUnderwritingAction",
            "get /api/v1/staff/underwriting/actions/{actionId}","recoverStaffUnderwritingAction");
        var legacyRoutes=new TreeMap<>(before);newRoutes.keySet().forEach(legacyRoutes::remove);assertEquals(11,legacyRoutes.size());
        var expected=new TreeMap<>(legacyRoutes);expected.putAll(newRoutes);assertEquals(expected,after);assertEquals(14,after.size());
        assertEquals(14,new HashSet<>(after.values()).size());
        for(var method:StaffController.class.getDeclaredMethods())if(method.isAnnotationPresent(io.swagger.v3.oas.annotations.Operation.class))
            assertTrue(method.isAnnotationPresent(org.fireflyframework.web.idempotency.annotation.DisableIdempotency.class),method.getName());
        original.path("paths").fields().forEachRemaining(path->path.getValue().fields().forEachRemaining(method->{
            if(VERBS.contains(method.getKey())){
                String route=method.getKey()+" "+path.getKey();var observed=actual.path("paths").path(path.getKey()).path(method.getKey());
                if(legacyRoutes.containsKey(route))assertLegacyOperation(method.getValue(),observed,route);
                else assertEquals(method.getValue(),observed,route);
            }
        }));
        var originalSession=original.at("/paths/~1api~1v1~1staff~1sessions/post");var observedSession=actual.at("/paths/~1api~1v1~1staff~1sessions/post");
        var changedSchema=observedSession.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)changedSchema.at("/requestBody/content/application~1json/schema")).put("$ref","#/components/schemas/Unexpected");
        assertThrows(AssertionError.class,()->assertLegacyOperation(originalSession,changedSchema,"changed schema"));
        var extraExtension=observedSession.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)extraExtension).put("x-unrelated",true);
        assertThrows(AssertionError.class,()->assertLegacyOperation(originalSession,extraExtension,"unknown extension"));
        var wrongMarker=observedSession.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)wrongMarker).put("x-disable-idempotency",false);
        assertThrows(AssertionError.class,()->assertLegacyOperation(originalSession,wrongMarker,"wrong explicit marker"));
        original.path("components").fields().forEachRemaining(section->section.getValue().fields().forEachRemaining(component->
            assertEquals(component.getValue(),actual.path("components").path(section.getKey()).path(component.getKey()),section.getKey()+"/"+component.getKey())));
        assertEquals("bearer",actual.at("/components/securitySchemes/staffUnderwritingBearer/scheme").asText());
        for(var route:List.of(new String[]{"/api/v1/staff/underwriting/intents","post","GovernedUnderwritingPreparedIntent"},
            new String[]{"/api/v1/staff/underwriting/actions","post","GovernedUnderwritingCommandResponse"},
            new String[]{"/api/v1/staff/underwriting/actions/{actionId}","get","GovernedUnderwritingRecovery"})){
            var operation=actual.path("paths").path(route[0]).path(route[1]);
            assertEquals("#/components/schemas/"+route[2],operation.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText());
            assertEquals(1,operation.path("security").size());assertTrue(operation.path("security").get(0).has("staffUnderwritingBearer"));
            for(var parameter:operation.path("parameters")){
                assertFalse(Set.of("Authorization","X-Idempotency-Key","tenantId","staff_actor_id","principal").contains(parameter.path("name").asText()));
            }
        }
        var alternatives=actual.at("/paths/~1api~1v1~1staff~1underwriting~1actions/post/responses/503/content/application~1json/schema/oneOf");
        var refs=new HashSet<String>();alternatives.forEach(value->refs.add(value.path("$ref").asText()));
        assertEquals(Set.of("#/components/schemas/GovernedUnderwritingCommandResponse","#/components/schemas/ErrorResponse","#/components/schemas/ProblemDetail"),refs);
        verifyRefs(actual,actual);
        var controllers=new TreeSet<String>();for(var bean:context.getBeansWithAnnotation(RestController.class).values()){
            var type=org.springframework.util.ClassUtils.getUserClass(bean);
            if(type.getName().startsWith("io.fireflysoftware.experience.insurance.terminal.")){
                controllers.add(type.getName());assertEquals(OpenApiGenApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI().resolve("../classes/"),type.getProtectionDomain().getCodeSource().getLocation().toURI());
            }
        }
        assertEquals(Set.of("io.fireflysoftware.experience.insurance.terminal.StaffController","io.fireflysoftware.experience.insurance.terminal.staff.StaffUnderwritingController"),controllers);
        verifyNoInteractions(legacy,governed);
        Files.createDirectories(Path.of("target"));Files.write(Path.of("target/whole-terminal-openapi.json"),json);Files.write(Path.of("target/whole-terminal-openapi.yml"),yaml);
        if(Boolean.getBoolean("owned.insurance.terminal-sdk-boundary.enabled")){
            assertEquals("validated-hosted-manual-main-v1",System.getProperty("owned.insurance.terminal-sdk-boundary.hosted"));
            var root=Path.of(System.getProperty("owned.insurance.terminal-sdk-boundary.root")).normalize();
            assertTrue(root.isAbsolute());assertEquals(root,root.toRealPath());assertEquals(Path.of("..").toRealPath(),root);
            assertEquals(Path.of(System.getProperty("owned.insurance.terminal-sdk-boundary.workspace"),"qualification","insurance-terminal-staff-source-chain","exp-insurance-terminal").toRealPath(),root);
            canonical=root.resolve("exp-insurance-terminal-sdk/src/main/resources/openapi.yml");
            assertTrue(Files.isRegularFile(canonical,LinkOption.NOFOLLOW_LINKS));assertEquals(canonical,canonical.toRealPath());
            assertEquals(System.getProperty("owned.insurance.terminal-sdk-boundary.preimage-sha256"),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(canonical))));
            assertArrayEquals(preimage,Files.readAllBytes(canonical));
            Files.write(Path.of("target/whole-terminal-sdk-preimage.yml"),preimage,StandardOpenOption.CREATE_NEW);
            Files.write(canonical,yaml,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);assertArrayEquals(yaml,Files.readAllBytes(canonical));
        }
    }
    private static void assertLegacyOperation(JsonNode original,JsonNode actual,String route){
        var expected=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)expected).put("x-disable-idempotency",true);
        assertEquals(expected,actual,route);
    }
    private static Map<String,String> operations(JsonNode root){var result=new TreeMap<String,String>();root.path("paths").fields().forEachRemaining(path->path.getValue().fields().forEachRemaining(method->{
        if(VERBS.contains(method.getKey()))assertNull(result.put(method.getKey()+" "+path.getKey(),method.getValue().path("operationId").asText()));}));return result;}
    private static void verifyRefs(JsonNode root,JsonNode node){if(node.isObject()&&node.has("$ref")){String ref=node.path("$ref").asText();assertTrue(ref.startsWith("#/"));assertFalse(root.at(ref.substring(1)).isMissingNode(),ref);}if(node.isContainerNode())node.forEach(child->verifyRefs(root,child));}
}
