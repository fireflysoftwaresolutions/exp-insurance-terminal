# Insurance Staff Terminal: source reference

[Repository guide](../../README.md) · [Machine-readable inventory](source-inventory.json)

This reference is derived from checked-in source. It separates declared HTTP mappings, typed contracts, property placeholders, and validation locations. Java reflection, inherited framework routes, generated targets, and runtime infrastructure are outside this static inventory.

## HTTP operations

| Mapping | Method | Controller | Kind |
| --- | --- | --- | --- |
| `GET /api/v1/staff/records/{area}` | `public Flux<StaffRecord> list(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `GET /api/v1/staff/records/{area}/{recordId}` | `public Mono<StaffRecord> open(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area, @PathVariable UUID recordId)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `GET /api/v1/staff/distributors` | `public Flux<DistributorView> distributors(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `GET /api/v1/staff/masters/{type}` | `public Flux<MasterRecord> masters(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String type)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `POST /api/v1/staff/sessions` | `public Mono<StaffSession> signIn( @Parameter(hidden = true) @AuthenticationPrincipal SecurityPrincipal principal, @RequestBody SignInRequest body)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `POST /api/v1/staff/records/{area}/{recordId}/close` | `public Mono<StaffRecord> close(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area, @PathVariable UUID recordId, @RequestBody RecordBody body)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `POST /api/v1/staff/records/{area}/{recordId}/remove` | `public Mono<Void> removeDraft(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area, @PathVariable UUID recordId, @RequestBody RecordBody body)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `POST /api/v1/staff/records/{area}/{recordId}/decide` | `public Mono<StaffRecord> decide(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area, @PathVariable UUID recordId, @RequestBody RecordBody body)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `POST /api/v1/staff/records/{area}/{recordId}/distributor` | `public Mono<StaffRecord> attach(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area, @PathVariable UUID recordId, @RequestBody RecordBody body)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `PUT /api/v1/staff/records/{area}/{recordId}` | `public Mono<StaffRecord> update(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String area, @PathVariable UUID recordId, @RequestBody RecordBody body)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |
| `PUT /api/v1/staff/configuration/{key}` | `public Mono<String> putProperty(@RequestHeader(value = "X-Actor-Id", required = false) UUID actorId, @PathVariable String key, @RequestBody PropertyBody body)` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) | Local declaration |

## Data contracts

### AccessCheck

[AccessCheck](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/AccessCheck.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `actorId` | `UUID` | See declaration / service rules |
| `action` | `String` | See declaration / service rules |
| `resourceType` | `String` | See declaration / service rules |
| `resourceId` | `String` | See declaration / service rules |

### AccessOutcome

[AccessOutcome](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/AccessOutcome.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `allowed` | `boolean` | See declaration / service rules |
| `reasonCode` | `String` | See declaration / service rules |

### AuditEntry

[AuditEntry](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/AuditEntry.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `auditId` | `UUID` | See declaration / service rules |
| `employeeId` | `UUID` | See declaration / service rules |
| `customerPartyId` | `UUID` | See declaration / service rules |
| `reason` | `String` | See declaration / service rules |
| `action` | `String` | See declaration / service rules |
| `area` | `String` | See declaration / service rules |
| `recordId` | `UUID` | See declaration / service rules |

### DistributorLink

[DistributorLink](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DistributorLink.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `distributorId` | `UUID` | See declaration / service rules |
| `recordId` | `UUID` | See declaration / service rules |
| `customerPartyId` | `UUID` | See declaration / service rules |
| `employeeId` | `UUID` | See declaration / service rules |
| `channel` | `String` | See declaration / service rules |
| `collaboratorId` | `UUID` | See declaration / service rules |
| `area` | `String` | See declaration / service rules |

### DistributorView

[DistributorView](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DistributorView.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `distributorId` | `UUID` | See declaration / service rules |
| `name` | `String` | See declaration / service rules |
| `role` | `String` | See declaration / service rules |
| `partyId` | `UUID` | See declaration / service rules |
| `channel` | `String` | See declaration / service rules |
| `agencyId` | `UUID` | See declaration / service rules |
| `collaboratorId` | `UUID` | See declaration / service rules |

### MasterRecord

[MasterRecord](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/MasterRecord.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `type` | `String` | See declaration / service rules |
| `id` | `UUID` | See declaration / service rules |
| `code` | `String` | See declaration / service rules |
| `name` | `String` | See declaration / service rules |

### RecordCommand

[RecordCommand](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/RecordCommand.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `area` | `StaffArea` | See declaration / service rules |
| `recordId` | `UUID` | See declaration / service rules |
| `customerPartyId` | `UUID` | See declaration / service rules |
| `reason` | `String` | See declaration / service rules |
| `note` | `String` | See declaration / service rules |
| `distributorId` | `UUID` | See declaration / service rules |
| `collaboratorId` | `UUID` | See declaration / service rules |
| `channel` | `String` | See declaration / service rules |
| `actorId` | `UUID` | See declaration / service rules |
| `status` | `String` | See declaration / service rules |
| `decision` | `String` | See declaration / service rules |

### StaffRecord

[StaffRecord](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffRecord.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `id` | `UUID` | See declaration / service rules |
| `area` | `String` | See declaration / service rules |
| `customerPartyId` | `UUID` | See declaration / service rules |
| `actorId` | `UUID` | See declaration / service rules |
| `status` | `String` | See declaration / service rules |
| `reference` | `String` | See declaration / service rules |
| `distributorId` | `UUID` | See declaration / service rules |
| `note` | `String` | See declaration / service rules |

### StaffSession

[StaffSession](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffSession.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `actorId` | `UUID` | See declaration / service rules |
| `provider` | `String` | See declaration / service rules |

### SubjectRef

[SubjectRef](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SubjectRef.java) · DTO / record / model

| Field | Java type | Validation annotations |
| --- | --- | --- |
| `actorId` | `UUID` | See declaration / service rules |
| `provider` | `String` | See declaration / service rules |

## Ports and service contracts

### AccessPort

[AccessPort](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/AccessPort.java)

```java
Mono<AccessOutcome> check(AccessCheck request);
```

### AuditPort

[AuditPort](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/AuditPort.java)

```java
Mono<AuditEntry> append(AuditEntry entry);
Flux<AuditEntry> list();
```

### ConfigurationPort

[ConfigurationPort](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/ConfigurationPort.java)

```java
Mono<String> getRequired(String key);
Mono<String> put(String key, String value, java.util.UUID employeeId);
Flux<MasterRecord> masters(String type);
```

### DistributorPort

[DistributorPort](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DistributorPort.java)

```java
Flux<DistributorView> list();
Mono<DistributorView> link(DistributorLink link);
```

### IdentityPort

[IdentityPort](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/IdentityPort.java)

```java
Mono<SubjectRef> authenticate(String username);
Mono<SubjectRef> resolve(String actorId);
```

### ImpersonationPort

[ImpersonationPort](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/ImpersonationPort.java)

```java
Mono<AuditEntry> impersonate(UUID employeeId, UUID customerPartyId, String reason, String action, String area, UUID recordId);
```

### StaffRecordPort

[StaffRecordPort](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffRecordPort.java)

```java
Flux<StaffRecord> list(StaffArea area);
Mono<StaffRecord> get(StaffArea area, UUID recordId);
Mono<StaffRecord> update(RecordCommand command);
Mono<StaffRecord> close(RecordCommand command);
Mono<Void> removeDraft(RecordCommand command, String requiredStatus);
Mono<StaffRecord> decide(RecordCommand command);
```

## Configuration

Values below come from source placeholders. Plain YAML values and configuration-property classes must also be reviewed at the linked configuration sources. Secret fallback values are deliberately redacted.

| Key | Fallback | Source |
| --- | --- | --- |
| `api-configuration.domain-platform.security-center.base-path` | `http://localhost:8085` | [TerminalWiring](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalWiring.java) |
| `firefly.terminal.security.issuer` | Empty | [TerminalSecurityConfiguration](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalSecurityConfiguration.java) |
| `firefly.terminal.security.audience` | Empty | [TerminalSecurityConfiguration](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalSecurityConfiguration.java) |
| `firefly.terminal.security.jwk-set-uri` | Empty | [TerminalSecurityConfiguration](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalSecurityConfiguration.java) |
| `firefly.security.resource-server.strict-policy` | `false` | [TerminalSecurityConfiguration](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalSecurityConfiguration.java) |
| `SERVER_PORT` | `8121` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `SECURITY_CENTER_URL` | `http://localhost:8085` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `FIREFLY_TERMINAL_ADAPTER` | `sandbox` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `CONFIG_MGMT_URL` | `http://localhost:8088` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `MASTER_DATA_URL` | `http://localhost:8089` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `DISTRIBUTOR_MGMT_URL` | `http://localhost:8070` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `INSURANCE_POLICIES_URL` | `http://localhost:8071` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `INSURANCE_CLAIMS_URL` | `http://localhost:8072` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |
| `INSURANCE_UNDERWRITING_URL` | `http://localhost:8073` | [application](../../exp-insurance-terminal-web/src/main/resources/application.yaml) |

## Security and failure evidence

| Declaration or failure site | Source |
| --- | --- |
| `new IllegalStateException("PROPERTY_NOT_SET")));` | [DomainConfigurationClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainConfigurationClient.java) |
| `new TerminalDeniedException(exception.getMessage()));` | [DomainConfigurationClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainConfigurationClient.java) |
| `new TerminalDeniedException(exception.getMessage()));` | [DomainConfigurationClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainConfigurationClient.java) |
| `new IllegalStateException("Distributor audit log was not recorded"));` | [DomainDistributorClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainDistributorClient.java) |
| `new IllegalStateException("Distributor id was not recorded"));` | [DomainDistributorClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainDistributorClient.java) |
| `new TerminalDeniedException("UPDATE_NOT_APPLICABLE"));` | [DomainRecordClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClient.java) |
| `new TerminalDeniedException("CLOSE_NOT_APPLICABLE"));` | [DomainRecordClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClient.java) |
| `new TerminalDeniedException("REMOVAL_NOT_APPLICABLE"));` | [DomainRecordClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClient.java) |
| `new TerminalDeniedException("DRAFT_REQUIRED"));` | [DomainRecordClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClient.java) |
| `new TerminalDeniedException("DECISION_NOT_APPLICABLE"));` | [DomainRecordClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClient.java) |
| `new TerminalDeniedException("PROPERTY_NOT_SET");` | [DomainRecordClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClient.java) |
| `new TerminalDeniedException("AREA_NOT_INSURANCE");` | [DomainRecordClient](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClient.java) |
| `new TerminalDeniedException("REQUEST_INCOMPLETE"));` | [ImpersonationCoordinator](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/ImpersonationCoordinator.java) |
| `new TerminalDeniedException("ACCESS_DECISION_MISSING")))` | [ImpersonationCoordinator](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/ImpersonationCoordinator.java) |
| `new TerminalDeniedException(outcome.reasonCode()));` | [ImpersonationCoordinator](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/ImpersonationCoordinator.java) |
| `new TerminalDeniedException("IMPERSONATION_AUDIT_NOT_CONFIRMED")));` | [ImpersonationCoordinator](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/ImpersonationCoordinator.java) |
| `new TerminalDeniedException("DOMAIN_RESPONSE_UNREADABLE");` | [JsonResponses](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/JsonResponses.java) |
| `new TerminalDeniedException("PROPERTY_NOT_SET"));` | [SandboxConfigurationStore](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxConfigurationStore.java) |
| `new TerminalDeniedException("REQUEST_INCOMPLETE"));` | [SandboxConfigurationStore](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxConfigurationStore.java) |
| `new TerminalDeniedException("MASTER_TYPE_UNKNOWN"));` | [SandboxConfigurationStore](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxConfigurationStore.java) |
| `new TerminalDeniedException("DISTRIBUTOR_NOT_FOUND"));` | [SandboxDistributorDirectory](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxDistributorDirectory.java) |
| `new TerminalDeniedException("RECORD_NOT_FOUND"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("PARTY_MISMATCH"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("PARTY_MISMATCH"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("REMOVAL_NOT_APPLICABLE"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("PARTY_MISMATCH"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("DRAFT_REQUIRED"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("DECISION_NOT_APPLICABLE"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("PARTY_MISMATCH"));` | [SandboxRecordBook](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/SandboxRecordBook.java) |
| `new TerminalDeniedException("ACTOR_UNKNOWN")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("ACCESS_DECISION_MISSING")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException(outcome.reasonCode()))));` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("REQUEST_INCOMPLETE"));` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("ACTOR_UNKNOWN")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("ACTOR_MISMATCH")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("ACCESS_DECISION_MISSING")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException(outcome.reasonCode()))));` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("REMOVAL_NOT_APPLICABLE"));` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("DECISION_NOT_APPLICABLE"));` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("IMPERSONATION_NOT_CONFIRMED"))))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("REQUEST_INCOMPLETE"));` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("ACTOR_UNKNOWN")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("ACTOR_MISMATCH")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("ACCESS_DECISION_MISSING")))` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException(outcome.reasonCode())));` | [StaffTerminal](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminal.java) |
| `new TerminalDeniedException("PROPERTY_NOT_SET");` | [TerminalWiring](../../exp-insurance-terminal-core/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalWiring.java) |
| `@SecurityRequirement(name = "staffBearer")` | [StaffController](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/StaffController.java) |
| `HttpStatus.FORBIDDEN` | [TerminalAccessFilter](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalAccessFilter.java) |
| `new TerminalDeniedException("STAFF_STRICT_POLICY_REQUIRED"));` | [TerminalAccessFilter](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalAccessFilter.java) |
| `new TerminalDeniedException("STAFF_ACTOR_MISMATCH"));` | [TerminalAccessFilter](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalAccessFilter.java) |
| `new TerminalDeniedException("STAFF_ACTOR_MISMATCH"));` | [TerminalAccessFilter](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalAccessFilter.java) |
| `new TerminalDeniedException("STAFF_ACCESS_DENIED")))` | [TerminalAccessFilter](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalAccessFilter.java) |
| `new TerminalDeniedException("STAFF_ACCESS_DENIED"));` | [TerminalAccessFilter](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalAccessFilter.java) |
| `new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),"Invalid staff authentication");` | [TerminalAuthentication](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalAuthentication.java) |
| `new BadJwtException("Staff trust is not configured"));` | [TerminalSecurityConfiguration](../../exp-insurance-terminal-web/src/main/java/io/fireflysoftware/experience/insurance/terminal/TerminalSecurityConfiguration.java) |

## Build and dependencies

### exp-insurance-terminal-core

[exp-insurance-terminal-core/pom.xml](../../exp-insurance-terminal-core/pom.xml)

Coordinates: `io.fireflysoftware:exp-insurance-terminal-core:26.10.6`. Parent: `io.fireflysoftware:exp-insurance-terminal:26.10.6`.

- `com.fasterxml.jackson.core:jackson-databind:`
- `io.fireflysoftware:domain-core-configuration-sdk:26.10.6`
- `io.fireflysoftware:domain-core-reference-data-sdk:26.10.6`
- `io.fireflysoftware:domain-core-security-center-core:26.10.6`
- `io.fireflysoftware:domain-core-security-center-sdk:26.10.6`
- `io.fireflysoftware:domain-distributor-branding-sdk:26.10.6`
- `io.fireflysoftware:domain-insurance-claims-sdk:26.10.6`
- `io.fireflysoftware:domain-insurance-policies-sdk:26.10.6`
- `io.fireflysoftware:domain-insurance-underwriting-sdk:26.10.6`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework.boot:spring-boot-starter-webflux:`

### exp-insurance-terminal-sdk

[exp-insurance-terminal-sdk/pom.xml](../../exp-insurance-terminal-sdk/pom.xml)

Coordinates: `io.fireflysoftware:exp-insurance-terminal-sdk:26.10.6`. Parent: `io.fireflysoftware:exp-insurance-terminal:26.10.6`.

- `com.fasterxml.jackson.core:jackson-databind:`
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:`
- `com.squareup.okhttp3:logging-interceptor:`
- `com.squareup.okhttp3:okhttp:`
- `io.fireflysoftware:exp-insurance-terminal-web:`
- `io.swagger.core.v3:swagger-annotations:`
- `jakarta.annotation:jakarta.annotation-api:`
- `javax.annotation:javax.annotation-api:1.3.2`
- `org.openapitools:jackson-databind-nullable:0.2.8`
- `org.projectlombok:lombok:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework:spring-webflux:`

### exp-insurance-terminal-web

[exp-insurance-terminal-web/pom.xml](../../exp-insurance-terminal-web/pom.xml)

Coordinates: `io.fireflysoftware:exp-insurance-terminal-web:26.10.6`. Parent: `io.fireflysoftware:exp-insurance-terminal:26.10.6`.

- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:`
- `io.fireflysoftware:exp-insurance-terminal-core:`
- `org.fireflyframework:fireflyframework-security-resource-server:`
- `org.springdoc:springdoc-openapi-starter-webflux-api:${springdoc-openapi.version}`
- `org.springframework.boot:spring-boot-starter-actuator:`
- `org.springframework.boot:spring-boot-starter-test:`
- `org.springframework.boot:spring-boot-starter-webflux:`

### exp-insurance-terminal

[pom.xml](../../pom.xml)

Coordinates: `io.fireflysoftware:exp-insurance-terminal:26.10.6`. Parent: `io.fireflysoftware:firefly-parent:26.10.6`.

- `io.fireflysoftware:exp-insurance-terminal-core:${project.version}`
- `io.fireflysoftware:exp-insurance-terminal-sdk:${project.version}`
- `io.fireflysoftware:exp-insurance-terminal-web:${project.version}`
- `io.fireflysoftware:firefly-bom:26.10.6`

## Test sources

- [exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/DomainConfigurationClientTest.java](../../exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/DomainConfigurationClientTest.java)
- [exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/DomainDistributorClientTest.java](../../exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/DomainDistributorClientTest.java)
- [exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClientTest.java](../../exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/DomainRecordClientTest.java)
- [exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/SecurityCenterSandbox.java](../../exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/SecurityCenterSandbox.java)
- [exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminalTest.java](../../exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/StaffTerminalTest.java)
- [exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalFixture.java](../../exp-insurance-terminal-core/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalFixture.java)
- [exp-insurance-terminal-sdk/src/test/java/io/fireflysoftware/experience/insurance/terminal/sdk/GeneratedTerminalClientContractTest.java](../../exp-insurance-terminal-sdk/src/test/java/io/fireflysoftware/experience/insurance/terminal/sdk/GeneratedTerminalClientContractTest.java)
- [exp-insurance-terminal-sdk/src/test/resources/StaffTerminalApi-original-public-signatures.txt](../../exp-insurance-terminal-sdk/src/test/resources/StaffTerminalApi-original-public-signatures.txt)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/OpenApiGenApplication.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/OpenApiGenApplication.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalBoundaryHttpTest.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalBoundaryHttpTest.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalDefaultDenyHttpTest.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalDefaultDenyHttpTest.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalIndependentHttpTest.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalIndependentHttpTest.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalMissingTrustHttpTest.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalMissingTrustHttpTest.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalOpenApiContractTest.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalOpenApiContractTest.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalPackagedStartupIT.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalPackagedStartupIT.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalSessionAuthorityHttpTest.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalSessionAuthorityHttpTest.java)
- [exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalStrictPolicyRequiredHttpTest.java](../../exp-insurance-terminal-web/src/test/java/io/fireflysoftware/experience/insurance/terminal/TerminalStrictPolicyRequiredHttpTest.java)
- [exp-insurance-terminal-web/src/test/resources/legacy-terminal-openapi.yml](../../exp-insurance-terminal-web/src/test/resources/legacy-terminal-openapi.yml)

## CI definitions

- [.github/workflows/maven-central.yml](../../.github/workflows/maven-central.yml)
- [.github/workflows/release.yml](../../.github/workflows/release.yml)
