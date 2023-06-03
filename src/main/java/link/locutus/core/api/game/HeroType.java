package link.locutus.core.api.game;

public enum HeroType {
    /*
     Visionary
     Commander
     Warmonger
     Mogul
     Magician
     Slayer
     Necromancer
     Automator
     */

    VISIONARY,
    COMMANDER,
    WARMONGER,
    MOGUL,
    MAGICIAN,
    SLAYER,
    NECROMANCER,
    AUTOMATOR,
    FEY,

    ;

    public static HeroType[] values = values();

    public HeroType parse(String input) {
        return valueOf(input.toUpperCase());
    }
}
