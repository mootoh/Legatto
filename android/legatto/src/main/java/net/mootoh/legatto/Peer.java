package net.mootoh.legatto;

import java.util.UUID;

/**
 * represents a peer participating in a session.
 */
public class Peer {
    private final UUID uuid_;

    Peer(UUID uuid_) {
        this.uuid_ = uuid_;
    }

}
