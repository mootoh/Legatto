package net.mootoh.legatto;

import java.util.UUID;

/**
 * represents a peer participating in a session.
 */
public class Peer {
    private byte identifier_;

    public Peer(byte identifier) {
        identifier_ = identifier;
    }

    public byte getIdentifier() {
        return identifier_;
    }
}
