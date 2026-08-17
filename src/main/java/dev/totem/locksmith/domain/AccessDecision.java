package dev.totem.locksmith.domain;

public record AccessDecision(boolean allowed, String reason, BreakDisposition breakDisposition) {
    public static AccessDecision allow(String reason) {
        return new AccessDecision(true, reason, BreakDisposition.NOT_A_BREAK);
    }

    public static AccessDecision deny(String reason) {
        return new AccessDecision(false, reason, BreakDisposition.NOT_A_BREAK);
    }

    public static AccessDecision breakAllowed(BreakDisposition disposition, String reason) {
        return new AccessDecision(true, reason, disposition);
    }
}
