package com.payledger.ledger;

/**
 * Side of a ledger entry, stored as one character: {@code 'D'} or {@code 'C'}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public enum EntryDirection {
    DEBIT("D"),
    CREDIT("C");

    private final String code;

    EntryDirection(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static EntryDirection fromCode(String code) {
        return switch (code) {
            case "D" -> DEBIT;
            case "C" -> CREDIT;
            default -> throw new IllegalArgumentException("Unknown entry direction: " + code);
        };
    }
}
