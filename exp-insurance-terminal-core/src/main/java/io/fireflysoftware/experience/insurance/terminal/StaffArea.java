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

import java.util.UUID;

/**
 * One record family the staff terminal lists, opens, and changes through a domain port.
 */
public interface StaffArea {

    String name();

    String propertyPrefix();

    String accessType();

    UUID primaryId();

    String domainKey();

    String itemPath(UUID id);

    UpdateMode updateMode();

    CloseMode closeMode();

    String closeSuffix();

    DecideMode decideMode();

    String closeStatusKey();

    String defaultCloseStatus();

    boolean draftRemoval();

    default String recordIndexKey() {
        return propertyPrefix() + ".records." + name();
    }

    default String removeStatusKey() {
        return propertyPrefix() + ".remove-status." + name();
    }

    default String holderRoleKey() {
        return propertyPrefix() + ".accounts.holder-role-id";
    }

    default String cardSecurityKey() {
        return propertyPrefix() + ".cards.security-setting-id";
    }

    default String channelKey() {
        return propertyPrefix() + ".distributor.channel";
    }
}
