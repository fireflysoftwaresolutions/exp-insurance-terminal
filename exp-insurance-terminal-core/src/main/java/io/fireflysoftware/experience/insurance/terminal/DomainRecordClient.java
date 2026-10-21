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

import io.fireflysoftware.domain.insurance.claims.sdk.api.ClaimsApi;
import io.fireflysoftware.domain.insurance.claims.sdk.model.ClaimDTO;
import io.fireflysoftware.domain.insurance.policies.sdk.api.PoliciesApi;
import io.fireflysoftware.domain.insurance.policies.sdk.model.PolicyDTO;
import io.fireflysoftware.domain.insurance.underwriting.sdk.api.UnderwritingApi;
import io.fireflysoftware.domain.insurance.underwriting.sdk.model.SubmissionDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Reads and updates policies, claims, and submissions through their domain SDKs.
 */
public final class DomainRecordClient implements StaffRecordPort {

    private final ConfigurationPort configuration;
    private final Map<String, String> domainBaseUrls;

    public DomainRecordClient(ConfigurationPort configuration, Map<String, String> domainBaseUrls) {
        this.configuration = configuration;
        this.domainBaseUrls = Map.copyOf(domainBaseUrls);
    }

    @Override
    public Flux<StaffRecord> list(StaffArea area) {
        return configuration.getRequired(area.recordIndexKey())
                .flatMapMany(csv -> Flux.fromArray(csv.split(",")))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .concatMap(token -> get(area, UUID.fromString(token)));
    }

    @Override
    public Mono<StaffRecord> get(StaffArea area, UUID recordId) {
        InsuranceArea insurance = insurance(area);
        String idempotency = key();
        return switch (insurance) {
            case POLICY -> policies(insurance).get(recordId, idempotency)
                    .map(dto -> policyRecord(dto, recordId, null));
            case CLAIM -> claims(insurance).get(recordId, idempotency)
                    .map(dto -> claimRecord(dto, recordId, null));
            case SUBMISSION -> underwriting(insurance).get(recordId, idempotency)
                    .map(dto -> submissionRecord(dto, recordId, null));
        };
    }

    @Override
    public Mono<StaffRecord> update(RecordCommand command) {
        InsuranceArea area = insurance(command.area());
        if (area.updateMode() != UpdateMode.PUT_DOCUMENT) {
            return Mono.error(new TerminalDeniedException("UPDATE_NOT_APPLICABLE"));
        }
        UUID recordId = command.recordId();
        return switch (area) {
            case POLICY -> {
                PoliciesApi api = policies(area);
                yield api.get(recordId, key())
                        .flatMap(current -> api.update(recordId, current, key()))
                        .map(written -> policyRecord(written, recordId, command));
            }
            case CLAIM -> {
                ClaimsApi api = claims(area);
                yield api.get(recordId, key())
                        .flatMap(current -> api.update(recordId, current, key()))
                        .map(written -> claimRecord(written, recordId, command));
            }
            case SUBMISSION -> {
                UnderwritingApi api = underwriting(area);
                yield api.get(recordId, key())
                        .map(current -> applySubmission(current, command))
                        .flatMap(current -> api.update(recordId, current, key()))
                        .map(written -> submissionRecord(written, recordId, command));
            }
        };
    }

    @Override
    public Mono<StaffRecord> close(RecordCommand command) {
        InsuranceArea area = insurance(command.area());
        if (area.closeMode() != CloseMode.JSON_SUFFIX) {
            return Mono.error(new TerminalDeniedException("CLOSE_NOT_APPLICABLE"));
        }
        UUID recordId = command.recordId();
        String idempotency = key();
        Mono<?> call = switch (area) {
            case POLICY -> policies(area).cancel(recordId, policyReason(command), idempotency);
            case CLAIM -> claims(area).close(recordId, claimReason(command), idempotency);
            case SUBMISSION -> underwriting(area).withdraw(recordId, submissionReason(command), idempotency);
        };
        return call.thenReturn(commanded(command, command.status()));
    }

    @Override
    public Mono<Void> removeDraft(RecordCommand command, String requiredStatus) {
        InsuranceArea area = insurance(command.area());
        if (!area.draftRemoval()) {
            return Mono.error(new TerminalDeniedException("REMOVAL_NOT_APPLICABLE"));
        }
        return get(area, command.recordId()).flatMap(current -> {
            String status = current.status() == null || current.status().isBlank() ? "DRAFT" : current.status();
            if (!requiredStatus.equals(status)) {
                return Mono.error(new TerminalDeniedException("DRAFT_REQUIRED"));
            }
            String idempotency = key();
            return switch (area) {
                case POLICY -> policies(area).remove(command.recordId(), idempotency);
                case CLAIM -> claims(area).remove(command.recordId(), idempotency);
                case SUBMISSION -> underwriting(area).remove(command.recordId(), idempotency);
            };
        });
    }

    @Override
    public Mono<StaffRecord> decide(RecordCommand command) {
        InsuranceArea area = insurance(command.area());
        UUID recordId = command.recordId();
        String idempotency = key();
        Mono<?> call = switch (area.decideMode()) {
            case DENY -> claims(area).deny(recordId, claimReason(command), idempotency);
            case DECLINE -> underwriting(area).decline(recordId, submissionReason(command), idempotency);
            case APPROVE_OR_REJECT, NONE -> Mono.error(new TerminalDeniedException("DECISION_NOT_APPLICABLE"));
        };
        return call.thenReturn(commanded(command, SandboxRecordBook.decisionStatus(command)));
    }

    private PoliciesApi policies(InsuranceArea area) {
        var client = new io.fireflysoftware.domain.insurance.policies.sdk.invoker.ApiClient();
        client.setBasePath(base(area));
        return new PoliciesApi(client);
    }

    private ClaimsApi claims(InsuranceArea area) {
        var client = new io.fireflysoftware.domain.insurance.claims.sdk.invoker.ApiClient();
        client.setBasePath(base(area));
        return new ClaimsApi(client);
    }

    private UnderwritingApi underwriting(InsuranceArea area) {
        var client = new io.fireflysoftware.domain.insurance.underwriting.sdk.invoker.ApiClient();
        client.setBasePath(base(area));
        return new UnderwritingApi(client);
    }

    private String base(StaffArea area) {
        String url = domainBaseUrls.get(area.domainKey());
        if (url == null || url.isBlank()) {
            throw new TerminalDeniedException("PROPERTY_NOT_SET");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static InsuranceArea insurance(StaffArea area) {
        if (area instanceof InsuranceArea insurance) {
            return insurance;
        }
        throw new TerminalDeniedException("AREA_NOT_INSURANCE");
    }

    private static SubmissionDTO applySubmission(SubmissionDTO current, RecordCommand command) {
        if (command.customerPartyId() != null) {
            current.setPartyId(command.customerPartyId());
        }
        if (command.distributorId() != null) {
            current.setDistributorId(command.distributorId());
        }
        return current;
    }

    private static io.fireflysoftware.domain.insurance.policies.sdk.model.ReasonRequest policyReason(RecordCommand command) {
        return new io.fireflysoftware.domain.insurance.policies.sdk.model.ReasonRequest()
                .statusReason(command.reason() == null ? "" : command.reason());
    }

    private static io.fireflysoftware.domain.insurance.claims.sdk.model.ReasonRequest claimReason(RecordCommand command) {
        return new io.fireflysoftware.domain.insurance.claims.sdk.model.ReasonRequest()
                .statusReason(command.reason() == null ? "" : command.reason());
    }

    private static io.fireflysoftware.domain.insurance.underwriting.sdk.model.ReasonRequest submissionReason(RecordCommand command) {
        return new io.fireflysoftware.domain.insurance.underwriting.sdk.model.ReasonRequest()
                .statusReason(command.reason() == null ? "" : command.reason());
    }

    private static StaffRecord policyRecord(PolicyDTO policy, UUID recordId, RecordCommand command) {
        String status = policy.getPolicyStatus() == null ? "" : policy.getPolicyStatus().getValue();
        StaffRecord record = new StaffRecord(recordId, InsuranceArea.POLICY.name(), null, null, status,
                policy.getPolicyNumber(), null, null);
        return command == null ? record : record.withUpdate(command);
    }

    private static StaffRecord claimRecord(ClaimDTO claim, UUID recordId, RecordCommand command) {
        String status = claim.getClaimStatus() == null ? "" : claim.getClaimStatus().getValue();
        StaffRecord record = new StaffRecord(recordId, InsuranceArea.CLAIM.name(), null, null, status,
                claim.getClaimNumber(), null, null);
        return command == null ? record : record.withUpdate(command);
    }

    private static StaffRecord submissionRecord(SubmissionDTO submission, UUID recordId, RecordCommand command) {
        String status = submission.getSubmissionStatus() == null ? "" : submission.getSubmissionStatus().getValue();
        StaffRecord record = new StaffRecord(recordId, InsuranceArea.SUBMISSION.name(), submission.getPartyId(), null, status,
                submission.getSubmissionNumber(), submission.getDistributorId(), null);
        return command == null ? record : record.withUpdate(command);
    }

    private static StaffRecord commanded(RecordCommand command, String status) {
        return new StaffRecord(command.recordId(), command.area().name(), command.customerPartyId(), command.actorId(),
                status, command.reason(), command.distributorId(), command.note());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
