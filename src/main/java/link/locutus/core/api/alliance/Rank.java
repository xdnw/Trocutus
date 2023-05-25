package link.locutus.core.api.alliance;

public enum Rank {
    NONE,
    APPLICANT,
    MEMBER,
    ADMIN,
    FOUNDER

    ;
    public static Rank[] values = values();

    public static Rank parse(String positionStr) {
        if (positionStr.isEmpty()) return NONE;
        return valueOf(positionStr.toUpperCase());
    }
}
