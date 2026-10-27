package com.paymatch.matching;

/** How we know who sent the money. Account and NIP are reliable, a name match less so. */
public record Identification(CounterpartySnapshot counterparty, Method method, String detail) {

    public enum Method {
        ACCOUNT,
        NIP,
        NAME
    }

    public boolean isStrong() {
        return method != Method.NAME;
    }

    public MatchReason reason() {
        return switch (method) {
            case ACCOUNT -> new MatchReason("account", "Sender account " + detail + " belongs to " + counterparty.name());
            case NIP -> new MatchReason("nip", "NIP " + detail + " in the title belongs to " + counterparty.name());
            case NAME -> new MatchReason("name", "Sender name \"" + detail + "\" looks like " + counterparty.name());
        };
    }
}
