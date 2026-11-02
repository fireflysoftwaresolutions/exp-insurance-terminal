package io.fireflysoftware.experience.insurance.terminal;

import io.fireflysoftware.domain.insurance.underwriting.sdk.model.SubmissionDTO;
import io.fireflysoftware.experience.insurance.terminal.staff.CurrentStaffSubmissionReader;
import java.util.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Controller selection and response mapping only; controlled service doubles establish no business acceptance. */
class StaffSubmissionReadControllerTest{
    final UUID staff=UUID.randomUUID(),submission=UUID.randomUUID(),party=UUID.randomUUID();
    SecurityPrincipal principal(){return SecurityPrincipal.builder().subject("controlled-staff").attributes(Map.of("staffActorId",staff.toString())).build();}
    StaffController controller(StaffTerminal legacy,CurrentStaffSubmissionReader read){var beans=new StaticListableBeanFactory();if(read!=null)beans.addBean("currentReads",read);return new StaffController(legacy,beans.getBeanProvider(CurrentStaffSubmissionReader.class));}
    @Test void existingSubmissionOpenMapsOnlyCurrentDomainOwnerFields(){
        var legacy=mock(StaffTerminal.class);var read=mock(CurrentStaffSubmissionReader.class);var dto=new SubmissionDTO().submissionId(submission).tenantId(UUID.randomUUID()).partyId(party).submissionNumber("SUB-CONTROLLED").submissionStatus(SubmissionDTO.SubmissionStatusEnum.REFERRED);
        when(read.read("Bearer controlled",submission)).thenReturn(Mono.just(dto));var result=controller(legacy,read).open(null,"SUBMISSION",submission,"Bearer controlled",principal(),new HttpHeaders()).block();
        assertNotNull(result);assertEquals(submission,result.id());assertEquals(party,result.customerPartyId());assertEquals("REFERRED",result.status());assertEquals("SUB-CONTROLLED",result.reference());assertNull(result.actorId());assertNull(result.note());verify(read).read("Bearer controlled",submission);verifyNoInteractions(legacy);
    }
    @Test void suppliedWrongDuplicateOrEmptyActorNeverReachesEitherService(){
        var legacy=mock(StaffTerminal.class);var read=mock(CurrentStaffSubmissionReader.class);var controller=controller(legacy,read);
        for(var pair:List.of(Map.entry(UUID.randomUUID(),List.of(UUID.randomUUID().toString())),Map.entry(staff,List.of(staff.toString(),staff.toString())))){
            var headers=new HttpHeaders();headers.put("X-Actor-Id",pair.getValue());assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.open(pair.getKey(),"SUBMISSION",submission,"Bearer controlled",principal(),headers).block()).getStatusCode().value());
        }
        var headers=new HttpHeaders();headers.add("X-Actor-Id","");assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.open(null,"SUBMISSION",submission,"Bearer controlled",principal(),headers).block()).getStatusCode().value());verifyNoInteractions(legacy,read);
    }
    @Test void absentCurrentReadHasNoLegacyFallback(){var legacy=mock(StaffTerminal.class);assertEquals(503,assertThrows(ResponseStatusException.class,()->controller(legacy,null).open(null,"SUBMISSION",submission,"Bearer controlled",principal(),new HttpHeaders()).block()).getStatusCode().value());verifyNoInteractions(legacy);}
    @Test void unrelatedPolicyRouteRetainsItsExistingService(){var legacy=mock(StaffTerminal.class);var read=mock(CurrentStaffSubmissionReader.class);when(legacy.open(staff,InsuranceArea.POLICY,submission)).thenReturn(Mono.empty());controller(legacy,read).open(staff,"POLICY",submission,"Bearer controlled",principal(),new HttpHeaders()).block();verify(legacy).open(staff,InsuranceArea.POLICY,submission);verifyNoInteractions(read);}
}
