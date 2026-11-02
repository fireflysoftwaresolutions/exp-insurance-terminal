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
import java.util.function.Function;

import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Reads and updates policies, claims, and submissions through their domain SDKs.
 */
public final class DomainRecordClientImpl implements StaffRecordPort {

    private final ConfigurationPort configuration;
    private final Map<String, String> domainBaseUrls;

    public DomainRecordClientImpl(ConfigurationPort configuration, Map<String, String> domainBaseUrls) {
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
                    .map(dto -> policyRecord(dto, recordId));
            case CLAIM -> claims(insurance).get(recordId, idempotency)
                    .map(dto -> claimRecord(dto, recordId));
            case SUBMISSION -> underwriting(insurance).get(recordId, idempotency)
                    .map(dto -> submissionRecord(dto, recordId));
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
                String requestKey = key();
                yield api.get(recordId, key())
                        .flatMap(current -> mutation(command, requestKey, api.update(recordId, current, requestKey),
                                written -> policyRecord(written, recordId)));
            }
            case CLAIM -> {
                ClaimsApi api = claims(area);
                String requestKey = key();
                yield api.get(recordId, key())
                        .flatMap(current -> mutation(command, requestKey, api.update(recordId, current, requestKey),
                                written -> claimRecord(written, recordId)));
            }
            case SUBMISSION -> {
                UnderwritingApi api = underwriting(area);
                String requestKey = key();
                yield api.get(recordId, key())
                        .map(current -> applySubmission(current, command))
                        .flatMap(current -> mutation(command, requestKey, api.update(recordId, current, requestKey),
                                written -> submissionRecord(written, recordId)));
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
        return switch (area) {
            case POLICY -> mutation(command, idempotency, policies(area).cancel(recordId, policyReason(command), idempotency),
                    dto -> policyRecord(dto, recordId));
            case CLAIM -> mutation(command, idempotency, claims(area).close(recordId, claimReason(command), idempotency),
                    dto -> claimRecord(dto, recordId));
            case SUBMISSION -> mutation(command, idempotency, underwriting(area).withdraw(recordId, submissionReason(command), idempotency),
                    dto -> submissionRecord(dto, recordId));
        };
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
        return switch (area.decideMode()) {
            case DENY -> mutation(command, idempotency, claims(area).deny(recordId, claimReason(command), idempotency),
                    dto -> claimRecord(dto, recordId));
            case DECLINE -> mutation(command, idempotency, underwriting(area).decline(recordId, submissionReason(command), idempotency),
                    dto -> submissionRecord(dto, recordId));
            case APPROVE_OR_REJECT, NONE -> Mono.error(new TerminalDeniedException("DECISION_NOT_APPLICABLE"));
        };
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

    private static StaffRecord policyRecord(PolicyDTO policy, UUID recordId) {
        String status = policy.getPolicyStatus() == null ? null : policy.getPolicyStatus().getValue();
        requireOwnerRecord(policy.getPolicyId(), recordId, status);
        return new StaffRecord(policy.getPolicyId(), InsuranceArea.POLICY.name(), null, null, status,
                policy.getPolicyNumber(), null, null);
    }

    private static StaffRecord claimRecord(ClaimDTO claim, UUID recordId) {
        String status = claim.getClaimStatus() == null ? null : claim.getClaimStatus().getValue();
        requireOwnerRecord(claim.getClaimId(), recordId, status);
        return new StaffRecord(claim.getClaimId(), InsuranceArea.CLAIM.name(), null, null, status,
                claim.getClaimNumber(), null, null);
    }

    private static StaffRecord submissionRecord(SubmissionDTO submission, UUID recordId) {
        String status = submission.getSubmissionStatus() == null ? null : submission.getSubmissionStatus().getValue();
        requireOwnerRecord(submission.getSubmissionId(), recordId, status);
        return new StaffRecord(submission.getSubmissionId(), InsuranceArea.SUBMISSION.name(), submission.getPartyId(), null, status,
                submission.getSubmissionNumber(), submission.getDistributorId(), null);
    }

    private static void requireOwnerRecord(UUID actual, UUID expected, String status) {
        if (!expected.equals(actual) || status == null || status.isBlank()) {
            throw new IllegalStateException("Matching complete owner record required");
        }
    }

    private static <T> Mono<StaffRecord> mutation(RecordCommand original, String requestKey, Mono<T> invocation,
                                                Function<T, StaffRecord> mapping) {
        return invocation.map(mapping)
                .switchIfEmpty(Mono.error(new RecordOutcomeUnknownException(original, requestKey)))
                .onErrorMap(error -> {
                    if (error instanceof RecordOutcomeUnknownException) {
                        return error;
                    }
                    if (error instanceof WebClientResponseException response
                            && java.util.Set.of(400, 401, 403, 404, 409, 422).contains(response.getStatusCode().value())) {
                        return new org.springframework.web.server.ResponseStatusException(response.getStatusCode(),
                                "Current owner invocation refused");
                    }
                    return new RecordOutcomeUnknownException(original, requestKey);
                });
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
