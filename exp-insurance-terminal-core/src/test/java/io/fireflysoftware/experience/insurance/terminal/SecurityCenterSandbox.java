/*
 * Copyright 2025 Firefly Software Foundation
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Controlled SDK protocol responses for Terminal unit tests; no Security Center implementation or native grant is qualified. */
public final class SecurityCenterSandbox implements AutoCloseable {
    private static final String CUSTOMER="11111111-1111-4111-8111-111111111111";
    private static final String DENIED="22222222-2222-4222-8222-222222222222";
    private static final String EMPLOYEE="33333333-3333-4333-8333-333333333333";
    private static final Map<String,String> SUBJECTS=Map.of("sample-journey",CUSTOMER,"denied-actor",DENIED,"sample-employee",EMPLOYEE);
    private final HttpServer server;
    private final ObjectMapper mapper=new ObjectMapper();
    private final List<String> accessBodies=new CopyOnWriteArrayList<>();
    public final String baseUrl;

    public SecurityCenterSandbox()throws IOException{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/identity/subjects",this::identity);
        server.createContext("/api/v1/access/checks",this::access);
        server.start();baseUrl="http://127.0.0.1:"+server.getAddress().getPort();
    }
    public List<String> accessBodies(){return List.copyOf(accessBodies);}
    private void identity(HttpExchange exchange)throws IOException{
        JsonNode request=mapper.readTree(body(exchange));String username=request.path("username").asText("").trim();
        String actor=request.path("actorId").asText("").trim().toLowerCase(java.util.Locale.ROOT);
        if(!username.isEmpty())actor=SUBJECTS.get(username);
        if(actor==null||!SUBJECTS.containsValue(actor)){write(exchange,404,Map.of());return;}
        write(exchange,200,Map.of("actorId",actor,"provider","sandbox","roles",List.of()));
    }
    private void access(HttpExchange exchange)throws IOException{
        String raw=body(exchange);accessBodies.add(raw);JsonNode request=mapper.readTree(raw);
        String actor=request.path("actorId").asText(),action=request.path("action").asText(),type=request.path("resourceType").asText(),id=request.path("resourceId").asText();
        String reason;
        if(actor.isBlank()||action.isBlank()||type.isBlank()||id.isBlank())reason="REQUEST_INCOMPLETE";
        else if(!SUBJECTS.containsValue(actor))reason="ACTOR_UNKNOWN";
        else if(actor.equals(DENIED))reason="ACTOR_NOT_ENTITLED";
        else if(actor.equals(CUSTOMER))reason=action.equals("READ")?(type.equals("POLICY")&&id.equals(InsuranceArea.POLICY.primaryId().toString())?"CONTROLLED_TEST_PROTOCOL_GRANT":"RESOURCE_NOT_GRANTED"):"ACTION_NOT_GRANTED";
        else if(action.equals("IMPERSONATE"))reason=type.equals("PARTY")&&id.equals(InsuranceSamples.CUSTOMER_PARTY.toString())?"CONTROLLED_TEST_PROTOCOL_GRANT":"RESOURCE_NOT_GRANTED";
        else if(!Set.of("READ","ADMINISTER").contains(action))reason="ACTION_NOT_GRANTED";
        else reason=java.util.Arrays.stream(InsuranceArea.values()).anyMatch(area->area.accessType().equals(type)&&area.primaryId().toString().equals(id))?"CONTROLLED_TEST_PROTOCOL_GRANT":"RESOURCE_NOT_GRANTED";
        write(exchange,200,Map.of("allowed",reason.equals("CONTROLLED_TEST_PROTOCOL_GRANT"),"reasonCode",reason));
    }
    private static String body(HttpExchange exchange)throws IOException{
        if(!exchange.getRequestMethod().equals("POST"))throw new IOException("Controlled SDK fixture requires POST");
        try(var input=exchange.getRequestBody()){byte[] raw=input.readNBytes(32769);if(raw.length==0||raw.length>32768)throw new IOException("Bounded controlled SDK request required");return new String(raw,StandardCharsets.UTF_8);}
    }
    private void write(HttpExchange exchange,int status,Object value)throws IOException{
        byte[] bytes=mapper.writeValueAsBytes(value);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);
        try(var out=exchange.getResponseBody()){out.write(bytes);}exchange.close();
    }
    @Override public void close(){server.stop(0);}
}
