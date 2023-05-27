package link.locutus.core.api.game;

public enum MilitaryUnit {
    SOLDIER(true, 100, 0, 0, 0, 1),
    CAVALRY(true, 100 + 200, 0, 0, 0, 2),
    ARCHER(true, 100 + 200, 0, 0, 0, 2),
    ELITE(true, 100 + 500, 0, 0, 0, 3),
    GOLD(false, 1, 0, 0, 0, 0),
    LAND(false, 500, 0, 0, 0, -10),
    DEVELOPED_LAND(false, 500 + 500, 0, 0, 0, -30),
    MANA(false, 0, 1, 0, 0, 0),
    EXP(false, 0, 0, -1, 0, 0),
    BATTLE_POINTS(false, 0, 0, 0, 200, 0)


    ;

    private final boolean buildable;
    private final int goldValue, manaValue;
    private final int upkeep;
    private final int expValue;
    private final int bpValue;

    MilitaryUnit(boolean buildable, int goldValue, int manaValue, int expValue, int bpValue, int upkeep) {
        this.buildable = buildable;
        this.goldValue = goldValue;
        this.manaValue = manaValue;
        this.expValue = expValue;
        this.bpValue = bpValue;
        this.upkeep = upkeep;
    }

    public boolean isBuildable() {
        return buildable;
    }

    public int getGoldValue() {
        return goldValue;
    }

    public int getManaValue() {
        return manaValue;
    }

    public int getExpValue() {
        return expValue;
    }

    public int getBpValue() {
        return bpValue;
    }

    public int getUpkeep() {
        return upkeep;
    }
}
