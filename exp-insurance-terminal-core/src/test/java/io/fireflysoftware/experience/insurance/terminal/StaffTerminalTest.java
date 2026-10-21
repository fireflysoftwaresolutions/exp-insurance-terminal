package io.fireflysoftware.experience.insurance.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffTerminalTest {

    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DENIED = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID EMPLOYEE = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private SecurityCenterSandbox security;
    private StaffTerminal terminal;

    @BeforeEach
    void setUp() throws Exception {
        security = new SecurityCenterSandbox();
        terminal = TerminalFixture.insurance(security.baseUrl);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void sampleEmployeeSignsIn() {
        StaffSession session = terminal.signIn("sample-employee").block();
        assertEquals(EMPLOYEE, session.actorId());
        assertEquals("sandbox", session.provider());
    }

    @Test
    void customerSubjectCannotOpenTheTerminal() {
        TerminalDeniedException error = assertThrows(TerminalDeniedException.class,
                () -> terminal.signIn("sample-journey").block());
        assertEquals("ACTION_NOT_GRANTED", error.reasonCode());
    }

    @Test
    void deniedActorCannotOpenTheTerminal() {
        TerminalDeniedException error = assertThrows(TerminalDeniedException.class,
                () -> terminal.signIn("denied-actor").block());
        assertEquals("ACTOR_NOT_ENTITLED", error.reasonCode());
    }

    @Test
    void customerCanReadTheSamplePolicyAndStillCannotAdminister() {
        AccessPort access = HttpAccessAdapter.at(security.baseUrl);
        AccessOutcome read = access.check(new AccessCheck(CUSTOMER, "READ", "POLICY",
                InsuranceArea.POLICY.primaryId().toString())).block();
        assertTrue(read.allowed());
        TerminalDeniedException error = assertThrows(TerminalDeniedException.class,
                () -> terminal.open(CUSTOMER, InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId()).block());
        assertEquals("ACTION_NOT_GRANTED", error.reasonCode());
    }

    @Test
    void sampleEmployeeImpersonatesBeforeACustomerUpdate() {
        RecordCommand command = new RecordCommand(InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Customer asked for an endorsement", "Reviewed by staff",
                null, null, null, null, null, null);
        StaffRecord updated = terminal.update(EMPLOYEE, command).block();
        assertEquals(EMPLOYEE, updated.actorId());
        assertEquals(InsuranceSamples.CUSTOMER_PARTY, updated.customerPartyId());
        assertEquals("Reviewed by staff", updated.note());
        AuditEntry audit = terminal.audit(EMPLOYEE).blockFirst();
        assertEquals(EMPLOYEE, audit.employeeId());
        assertEquals(InsuranceSamples.CUSTOMER_PARTY, audit.customerPartyId());
        assertTrue(security.accessBodies().stream().anyMatch(body -> body.contains("IMPERSONATE")
                && body.contains(EMPLOYEE.toString())
                && body.contains(InsuranceSamples.CUSTOMER_PARTY.toString())));
    }

    @Test
    void deniedActorCannotImpersonateAndTheRecordStaysUnchanged() {
        RecordCommand command = new RecordCommand(InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Should not apply", "Changed",
                null, null, null, null, null, null);
        TerminalDeniedException error = assertThrows(TerminalDeniedException.class,
                () -> terminal.update(DENIED, command).block());
        assertEquals("ACTOR_NOT_ENTITLED", error.reasonCode());
        StaffRecord record = terminal.open(EMPLOYEE, InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId()).block();
        assertNull(record.note());
        assertTrue(security.accessBodies().stream().noneMatch(body -> body.contains("IMPERSONATE")));
    }

    @Test
    void policyCloseUsesCancelledFromConfiguration() {
        RecordCommand command = RecordCommand.of(InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Cancel at the customer's request");
        StaffRecord closed = terminal.close(EMPLOYEE, command).block();
        assertEquals("CANCELLED", closed.status());
        assertEquals(EMPLOYEE, closed.actorId());
        assertEquals(InsuranceSamples.CUSTOMER_PARTY, closed.customerPartyId());
    }

    @Test
    void claimCloseUsesClosedAndADeclineIsADecision() {
        RecordCommand close = RecordCommand.of(InsuranceArea.CLAIM, InsuranceArea.CLAIM.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Close the claim");
        assertEquals("CLOSED", terminal.close(EMPLOYEE, close).block().status());

        RecordCommand decline = new RecordCommand(InsuranceArea.SUBMISSION, InsuranceArea.SUBMISSION.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Decline the submission", null, null, null, null, null, null, "DECLINE");
        assertEquals("DECLINED", terminal.decide(EMPLOYEE, decline).block().status());
    }

    @Test
    void employeeAttachesADistributorUuidToAPolicy() {
        RecordCommand command = new RecordCommand(InsuranceArea.POLICY, InsuranceArea.POLICY.primaryId(),
                InsuranceSamples.CUSTOMER_PARTY, "Attach the agency", null, InsuranceSamples.AGENCY,
                UUID.fromString("b3000000-0000-4000-8000-000000000002"), null, null, null, null);
        StaffRecord updated = terminal.attachDistributor(EMPLOYEE, command).block();
        assertEquals(InsuranceSamples.AGENCY, updated.distributorId());
        assertEquals(EMPLOYEE, updated.actorId());
    }

    @Test
    void propertyChangeGoesThroughConfiguration() {
        String key = "insurance-terminal.distributor.channel";
        assertEquals("PARTNER", terminal.putProperty(EMPLOYEE, key, "PARTNER").block());
        TerminalDeniedException error = assertThrows(TerminalDeniedException.class,
                () -> terminal.putProperty(CUSTOMER, key, "WEB").block());
        assertEquals("ACTION_NOT_GRANTED", error.reasonCode());
    }
}
