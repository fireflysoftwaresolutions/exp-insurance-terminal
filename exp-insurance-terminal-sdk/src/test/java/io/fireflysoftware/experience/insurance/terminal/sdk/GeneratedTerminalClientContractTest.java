package io.fireflysoftware.experience.insurance.terminal.sdk;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneratedTerminalClientContractTest {
  @ParameterizedTest
  @ValueSource(strings = {"StaffTerminalApi"})
  void generatedPublicClientRetainsTheOriginalSignatures(String name) throws Exception {
    String qualified="io.fireflysoftware.experience.insurance.terminal.sdk.api."+name;
    Class<?> type=Class.forName(qualified);
    var signatures=new ArrayList<String>();
    for(var method:type.getDeclaredMethods())if(Modifier.isPublic(method.getModifiers()))signatures.add(method.toGenericString());
    for(var constructor:type.getConstructors())signatures.add(constructor.toGenericString());
    Collections.sort(signatures);
    try(var expected=getClass().getResourceAsStream("/"+name+"-original-public-signatures.txt")) {
      assertNotNull(expected);
      assertEquals(new String(expected.readAllBytes(),StandardCharsets.UTF_8),String.join("\n",signatures)+"\n");
    }
    var path=Path.of("io/fireflysoftware/experience/insurance/terminal/sdk/api/"+name+".java");
    assertFalse(Files.exists(Path.of("src/main/java").resolve(path)));
    assertTrue(Files.isRegularFile(Path.of("target/generated-sources/src/gen/java/main").resolve(path)));
    assertEquals(Object.class,type.getSuperclass());
  }
}
