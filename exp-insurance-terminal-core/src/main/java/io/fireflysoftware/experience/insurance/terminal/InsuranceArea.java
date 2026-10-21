package io.fireflysoftware.experience.insurance.terminal;

import java.util.UUID;

/**
 * Insurance administration areas. Policies, claims, and underwriting submissions
 * are reached through their domain services.
 */
public enum InsuranceArea implements StaffArea {
    POLICY("POLICY", "10000000-0000-4000-8000-000000000005", "policies",
            "/api/v1/policies/{id}", UpdateMode.PUT_DOCUMENT, CloseMode.JSON_SUFFIX, "cancel",
            DecideMode.NONE, "CANCELLED", false),
    CLAIM("CLAIM", "10000000-0000-4000-8000-000000000006", "claims",
            "/api/v1/claims/{id}", UpdateMode.PUT_DOCUMENT, CloseMode.JSON_SUFFIX, "close",
            DecideMode.DENY, "CLOSED", false),
    SUBMISSION("APPLICATION", "10000000-0000-4000-8000-000000000004", "submissions",
            "/api/v1/submissions/{id}", UpdateMode.PUT_DOCUMENT, CloseMode.JSON_SUFFIX, "withdraw",
            DecideMode.DECLINE, "WITHDRAWN", false);

    public static final String PREFIX = "insurance-terminal";

    private final String accessType;
    private final UUID primaryId;
    private final String domainKey;
    private final String itemTemplate;
    private final UpdateMode updateMode;
    private final CloseMode closeMode;
    private final String closeSuffix;
    private final DecideMode decideMode;
    private final String defaultCloseStatus;
    private final boolean draftRemoval;

    InsuranceArea(String accessType, String primaryId, String domainKey, String itemTemplate,
                  UpdateMode updateMode, CloseMode closeMode, String closeSuffix, DecideMode decideMode,
                  String defaultCloseStatus, boolean draftRemoval) {
        this.accessType = accessType;
        this.primaryId = UUID.fromString(primaryId);
        this.domainKey = domainKey;
        this.itemTemplate = itemTemplate;
        this.updateMode = updateMode;
        this.closeMode = closeMode;
        this.closeSuffix = closeSuffix;
        this.decideMode = decideMode;
        this.defaultCloseStatus = defaultCloseStatus;
        this.draftRemoval = draftRemoval;
    }

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public String accessType() {
        return accessType;
    }

    @Override
    public UUID primaryId() {
        return primaryId;
    }

    @Override
    public String domainKey() {
        return domainKey;
    }

    @Override
    public String itemPath(UUID id) {
        return itemTemplate.replace("{id}", id.toString());
    }

    @Override
    public UpdateMode updateMode() {
        return updateMode;
    }

    @Override
    public CloseMode closeMode() {
        return closeMode;
    }

    @Override
    public String closeSuffix() {
        return closeSuffix;
    }

    @Override
    public DecideMode decideMode() {
        return decideMode;
    }

    @Override
    public String closeStatusKey() {
        return PREFIX + ".close-status." + name();
    }

    @Override
    public String defaultCloseStatus() {
        return defaultCloseStatus;
    }

    @Override
    public boolean draftRemoval() {
        return draftRemoval;
    }

    public static InsuranceArea fromName(String name) {
        return InsuranceArea.valueOf(name);
    }
}
