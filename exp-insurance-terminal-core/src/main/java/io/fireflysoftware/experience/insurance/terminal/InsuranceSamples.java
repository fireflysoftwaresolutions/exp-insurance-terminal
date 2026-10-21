package io.fireflysoftware.experience.insurance.terminal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sample book and the property keys an installer sets in core-common-config-mgmt.
 * Access grants are not listed here. Those live in the security center sandbox policy.
 */
public final class InsuranceSamples {

    public static final UUID CUSTOMER_PARTY = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID BROKER = UUID.fromString("b1000000-0000-4000-8000-000000000001");
    public static final UUID AGENCY = UUID.fromString("b1000000-0000-4000-8000-000000000002");
    public static final UUID COLLABORATOR = UUID.fromString("b1000000-0000-4000-8000-000000000003");

    private InsuranceSamples() {
    }

    public static Map<String, String> properties() {
        Map<String, String> values = new LinkedHashMap<>();
        for (InsuranceArea area : InsuranceArea.values()) {
            values.put(area.closeStatusKey(), area.defaultCloseStatus());
            values.put(area.recordIndexKey(), area.primaryId().toString());
        }
        values.put(InsuranceArea.POLICY.channelKey(), "AGENCY");
        values.put("insurance-terminal.customer-party-id", CUSTOMER_PARTY.toString());
        return values;
    }

    public static SandboxRecordBook.ListSeed records() {
        return new SandboxRecordBook.ListSeed(List.of(
                record(InsuranceArea.POLICY, "IN_FORCE", "Sample policy"),
                record(InsuranceArea.CLAIM, "OPEN", "Sample claim"),
                record(InsuranceArea.SUBMISSION, "SUBMITTED", "Sample underwriting submission")
        ));
    }

    public static List<DistributorView> distributors() {
        return List.of(
                new DistributorView(BROKER, "North Broker", "BROKER",
                        UUID.fromString("b2000000-0000-4000-8000-000000000001"), "AGENCY", null,
                        UUID.fromString("b3000000-0000-4000-8000-000000000001")),
                new DistributorView(AGENCY, "Harbor Agency", "AGENCY",
                        UUID.fromString("b2000000-0000-4000-8000-000000000002"), "AGENCY",
                        UUID.fromString("b4000000-0000-4000-8000-000000000002"),
                        UUID.fromString("b3000000-0000-4000-8000-000000000002")),
                new DistributorView(COLLABORATOR, "Field Collaborator", "COLLABORATOR",
                        UUID.fromString("b2000000-0000-4000-8000-000000000003"), "PARTNER", null,
                        UUID.fromString("b3000000-0000-4000-8000-000000000003"))
        );
    }

    public static List<MasterRecord> currencies() {
        return List.of(
                new MasterRecord("CURRENCY", UUID.fromString("d1000000-0000-4000-8000-000000000001"), "USD", "US Dollar"),
                new MasterRecord("CURRENCY", UUID.fromString("d1000000-0000-4000-8000-000000000002"), "EUR", "Euro")
        );
    }

    public static List<MasterRecord> countries() {
        return List.of(new MasterRecord("COUNTRY", UUID.fromString("d2000000-0000-4000-8000-000000000001"), "US", "United States"));
    }

    private static StaffRecord record(InsuranceArea area, String status, String reference) {
        return new StaffRecord(area.primaryId(), area.name(), CUSTOMER_PARTY, null, status, reference, null, null);
    }
}
