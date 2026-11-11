package com.smartbank.persistence;

import com.smartbank.model.AccountSnapshot;
import java.util.List;

/** Durable storage for account state. */
public interface SnapshotStore {

    List<AccountSnapshot> load();

    void save(List<AccountSnapshot> snapshots);

    /** A store that remembers nothing; for tests and the demo. */
    static SnapshotStore noop() {
        return new SnapshotStore() {
            @Override
            public List<AccountSnapshot> load() {
                return List.of();
            }

            @Override
            public void save(List<AccountSnapshot> snapshots) {
            }
        };
    }
}
