package link.locutus.core.api.game;

public enum TreatyType {
    WAR,
    LAP,
    NAP,
    MDP,
    ;

    public static TreatyType parse(String input) {
        return valueOf(input.toUpperCase());
    }

    public static final TreatyType[] values = values();
}
