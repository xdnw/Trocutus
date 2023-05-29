package link.locutus.core.api.game;

public enum AlertLevel {
    UNAWARE(1, 1),
    LOW_ALERT(1 / (1 - 0.2), 0.8),
    ON_GUARD(1 / (1 - 0.4), 0.6),
    HIGH_ALERT(1 / (1 - 0.6), 0.4),
    FULL_ALERT(1 / (1 - 0.8), 0.2)

    ;

    private final double casualtyModifier;
    private final double spellModifier;

    AlertLevel(double casualtyModifier, double spellModifier) {
        this.casualtyModifier = casualtyModifier;
        this.spellModifier = spellModifier;
    }

    public static AlertLevel[] values = values();

    public AlertLevel parse(String input) {
        return valueOf(input.toUpperCase());
    }
}
