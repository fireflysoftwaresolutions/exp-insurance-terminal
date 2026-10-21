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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls domain-core-configuration's environment configuration API and the reference
 * master-data currency and country filters. Property values are not kept in this process.
 */
public final class DomainConfigurationClient implements ConfigurationPort {

    private final WebClient webClient;
    private final String configurationBaseUrl;
    private final String masterDataBaseUrl;

    public DomainConfigurationClient(WebClient webClient, String configurationBaseUrl, String masterDataBaseUrl) {
        this.webClient = webClient;
        this.configurationBaseUrl = trim(configurationBaseUrl);
        this.masterDataBaseUrl = trim(masterDataBaseUrl);
    }

    @Override
    public Mono<String> getRequired(String key) {
        return webClient.post()
                .uri(configurationBaseUrl + "/api/v1/environment-configs/filter?page=0&size=200")
                .bodyValue(Map.of())
                .exchangeToMono(JsonResponses::body)
                .flatMap(body -> {
                    for (JsonNode item : items(body)) {
                        if (key.equals(item.path("configKey").asText())) {
                            String value = item.path("configValue").asText("");
                            if (!value.isBlank()) {
                                return Mono.just(value);
                            }
                        }
                    }
                    return Mono.error(new TerminalDeniedException("PROPERTY_NOT_SET"));
                });
    }

    @Override
    public Mono<String> put(String key, String value, UUID employeeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("environmentName", "sandbox");
        body.put("configKey", key);
        body.put("configValue", value);
        body.put("configType", "STRING");
        body.put("isSecret", false);
        body.put("description", "Staff terminal property set by " + employeeId);
        return webClient.post()
                .uri(configurationBaseUrl + "/api/v1/environment-configs")
                .bodyValue(body)
                .exchangeToMono(JsonResponses::body)
                .map(node -> node.path("configValue").asText(value));
    }

    @Override
    public Flux<MasterRecord> masters(String type) {
        String path = switch (type == null ? "" : type.toUpperCase()) {
            case "CURRENCY" -> "/api/v1/currencies/filter";
            case "COUNTRY" -> "/api/v1/countries/filter";
            default -> null;
        };
        if (path == null) {
            return Flux.error(new TerminalDeniedException("MASTER_TYPE_UNKNOWN"));
        }
        return webClient.post()
                .uri(masterDataBaseUrl + path)
                .bodyValue(Map.of())
                .exchangeToMono(JsonResponses::body)
                .flatMapMany(body -> Flux.fromIterable(items(body)))
                .map(item -> toMaster(type.toUpperCase(), item));
    }

    private MasterRecord toMaster(String type, JsonNode item) {
        String code = firstText(item, "currencyCode", "countryCode", "isoCode", "code", "alpha2Code");
        String name = firstText(item, "name", "currencyName", "countryName");
        String idText = firstText(item, "id", "currencyId", "countryId");
        UUID id = idText.isBlank() ? UUID.randomUUID() : UUID.fromString(idText);
        return new MasterRecord(type, id, code, name);
    }

    private Iterable<JsonNode> items(JsonNode body) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return java.util.List.of();
        }
        if (body.isArray()) {
            return body;
        }
        for (String field : new String[]{"content", "items", "data", "results"}) {
            JsonNode node = body.path(field);
            if (node.isArray()) {
                return node;
            }
        }
        return java.util.List.of();
    }

    private static String firstText(JsonNode item, String... names) {
        for (String name : names) {
            String value = item.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            throw new TerminalDeniedException("PROPERTY_NOT_SET");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
