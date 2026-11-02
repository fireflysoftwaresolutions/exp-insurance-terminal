package io.fireflysoftware.experience.insurance.terminal.staff.impl;

import io.fireflysoftware.experience.insurance.terminal.staff.CurrentStaffSubmissionReader;

import io.fireflysoftware.domain.insurance.underwriting.sdk.api.UnderwritingApi;
import io.fireflysoftware.domain.insurance.underwriting.sdk.model.SubmissionDTO;
import io.fireflysoftware.experience.insurance.terminal.staff.StaffUnderwritingClientFactory;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(prefix="insurance.terminal.staff-client",name={"enabled","submission-read.enabled"},havingValue="true")
public final class CurrentStaffSubmissionReaderImpl implements CurrentStaffSubmissionReader {
    private static final Set<String> READ=Set.of("insurance:underwriting:submissions:read");
    private final StaffUnderwritingClientFactory clients;
    private final UUID resourceTenantId;
    public CurrentStaffSubmissionReaderImpl(StaffUnderwritingClientFactory clients,
            @Value("${insurance.terminal.staff-client.submission-read.resource-tenant-id}")UUID resourceTenantId){
        this.clients=Objects.requireNonNull(clients);this.resourceTenantId=Objects.requireNonNull(resourceTenantId);
        if(resourceTenantId.equals(new UUID(0,0)))throw new IllegalArgumentException("Explicit resource tenant required");
    }
    @Override public Mono<SubmissionDTO> read(String authorization,UUID submissionId){
        if(submissionId==null||submissionId.equals(new UUID(0,0)))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact submission identifier required"));
        return Mono.defer(()->clients.current(authorization,READ)).switchIfEmpty(Mono.error(unavailable())).flatMap(client->
            client.revalidate().then(Mono.defer(()->new UnderwritingApi(client.api().getApiClient()).getWithHttpInfo(submissionId,null)))
                .timeout(client.remaining()).map(reply->{
                    var row=reply.getBody();if(reply.getStatusCode().value()!=200||row==null||!submissionId.equals(row.getSubmissionId())||!resourceTenantId.equals(row.getTenantId()))throw unavailable();return row;
                }).switchIfEmpty(Mono.error(unavailable()))
                .flatMap(row->client.revalidate().thenReturn(row))
                .onErrorResume(WebClientResponseException.class,error->client.revalidate().then(Mono.error(safe(error)))))
            .onErrorMap(CurrentStaffSubmissionReaderImpl::safe);
    }
    private static RuntimeException safe(Throwable error){
        if(error instanceof ResponseStatusException known)return known;
        if(error instanceof WebClientResponseException http&&Set.of(400,401,403,404).contains(http.getStatusCode().value()))return new ResponseStatusException(http.getStatusCode(),"Current submission read refused");
        return unavailable();
    }
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Current owner submission read unavailable");}
    @Override public String toString(){return "CurrentStaffSubmissionReaderImpl[redacted]";}
}
