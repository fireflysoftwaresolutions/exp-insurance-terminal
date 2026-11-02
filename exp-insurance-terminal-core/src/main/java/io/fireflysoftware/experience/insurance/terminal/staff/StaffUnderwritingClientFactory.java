/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.insurance.terminal.staff;

import io.fireflysoftware.domain.insurance.underwriting.sdk.api.StaffUnderwritingApi;
import java.time.Duration;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface StaffUnderwritingClientFactory {
    Mono<Client> current(String authorization,Set<String> scopes);
    interface Client {
        StaffUnderwritingApi api();
        Duration remaining();
        Mono<Void> revalidate();
    }
}
