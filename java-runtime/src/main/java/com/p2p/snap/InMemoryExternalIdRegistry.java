package com.p2p.snap;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("memory")
public class InMemoryExternalIdRegistry implements ExternalIdRegistry {
    private final Set<List<Object>> seen = new HashSet<>();

    @Override
    public synchronized boolean register(String partnerId, String externalId, LocalDate day) {
        return seen.add(List.of(partnerId, externalId, day));
    }

    public synchronized void clear() {
        seen.clear();
    }
}
