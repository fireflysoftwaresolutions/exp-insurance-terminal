/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.insurance.terminal.sdk;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedStaffUnderwritingClientContractTest {
    @Test void originalGeneratorProducesAllThreeStaffOperationsFromTheSameProducerRaw()throws Exception{
        var type=Class.forName("io.fireflysoftware.experience.insurance.terminal.sdk.api.StaffUnderwritingApi");
        var names=new HashSet<String>();Arrays.stream(type.getDeclaredMethods()).forEach(method->names.add(method.getName()));
        for(String operation:List.of("prepareStaffUnderwritingIntent","executeStaffUnderwritingAction","recoverStaffUnderwritingAction")){
            assertTrue(names.contains(operation));assertTrue(names.contains(operation+"WithHttpInfo"));
        }
        var relative=Path.of("io/fireflysoftware/experience/insurance/terminal/sdk/api/StaffUnderwritingApi.java");
        assertFalse(Files.exists(Path.of("src/main/java").resolve(relative)));
        assertTrue(Files.isRegularFile(Path.of("target/generated-sources/src/gen/java/main").resolve(relative)));
        byte[] actual=Files.readAllBytes(Path.of("../exp-insurance-terminal-web/target/whole-terminal-openapi.yml"));
        assertArrayEquals(actual,Files.readAllBytes(Path.of("src/main/resources/openapi.yml")));
        try(var resource=type.getResourceAsStream("/openapi.yml")){assertNotNull(resource);assertArrayEquals(actual,resource.readAllBytes());}
        assertEquals(Object.class,type.getSuperclass());
    }
}
