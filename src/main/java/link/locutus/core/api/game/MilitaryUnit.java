package link.locutus.core.api.game;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public enum MilitaryUnit {
    SOLDIER(1, true, 100, 0, 0, 0, 1, 1, 1),
    CAVALRY(0.9, true, 100 + 200, 0, 0, 0, 2, 3, 1),
    ARCHER(0.9, true, 100 + 200, 0, 0, 0, 2, 1, 3),
    ELITE(0.8, true, 100 + 500, 0, 0, 0, 3, 4, 4) {
        @Override
        public int getAttack(HeroType hero) {
            return switch (hero) {
                case VISIONARY -> 2;
                case COMMANDER -> 5;
                case WARMONGER -> 8;
                case MOGUL -> 3;
                case MAGICIAN -> 5;
                case SLAYER -> 6;
                case NECROMANCER -> 3;
                case AUTOMATOR -> 5;
                default -> {
                    throw new IllegalStateException("Unexpected value: " + hero);
                }
            };
        }

        @Override
        public int getDefense(HeroType hero) {
            return switch (hero) {
                case VISIONARY -> 8;
                case COMMANDER -> 5;
                case WARMONGER -> 2;
                case MOGUL -> 5;
                case MAGICIAN -> 3;
                case SLAYER -> 2;
                case NECROMANCER -> 3;
                case AUTOMATOR -> 5;
                default -> {
                    throw new IllegalStateException("Unexpected value: " + hero);
                }
            };
        }
    },
    GOLD(false, 1, 0, 0, 0, 0, 0, 0),
    LAND(false, 500, 0, 0, 0, -10, 0, 0),
    DEVELOPED_LAND(false, 500 + 500, 0, 0, 0, -30, 0, 0),
    MANA(false, 0, 1, 0, 0, 0, 0, 0),
    EXP(false, 0, 0, -1, 0, 0, 0, 0),
    BATTLE_POINTS(false, 0, 0, 0, 1, 0, 0, 0)


    ;

    private static MilitaryUnit[] values = values();
    private final boolean buildable;
    private final int goldValue, manaValue;
    private final int upkeep;
    private final int expValue;
    private final int bpValue;
    private final int attack,defense;
    private final double casualtyRatio;

    MilitaryUnit(boolean buildable, int goldValue, int manaValue, int expValue, int bpValue, int upkeep, int attack, int defense) {
        this(0, buildable, goldValue, manaValue, expValue, bpValue, upkeep, attack, defense);
    }

    MilitaryUnit(double casualtyRatio, boolean buildable, int goldValue, int manaValue, int expValue, int bpValue, int upkeep, int attack, int defense) {
        this.casualtyRatio = casualtyRatio;
        this.buildable = buildable;
        this.goldValue = goldValue;
        this.manaValue = manaValue;
        this.expValue = expValue;
        this.bpValue = bpValue;
        this.upkeep = upkeep;
        this.attack = attack;
        this.defense = defense;
    }

    public double getCasualtyRatio() {
        return casualtyRatio;
    }

    public static int getAttack(Map<MilitaryUnit, Long> myUnits, HeroType hero) {
        int total = 0;
        for (Map.Entry<MilitaryUnit, Long> entry : myUnits.entrySet()) {
            if (entry.getKey().attack == 0) continue;
            total += entry.getKey().getAttack(hero) * entry.getValue();
        }
        return total;
    }

    public static int getDefense(Map<MilitaryUnit, Long> myUnits, HeroType hero) {
        int total = 0;
        for (Map.Entry<MilitaryUnit, Long> entry : myUnits.entrySet()) {
            if (entry.getKey().defense == 0) continue;
            total += entry.getKey().getDefense(hero) * entry.getValue();
        }
        return total;
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

    public int getAttack(HeroType hero) {
        return attack;
    }

    public int getDefense(HeroType hero) {
        return defense;
    }

    public static Map.Entry<Map<MilitaryUnit, Long> , Map<MilitaryUnit, Long>> getUnits(Map<MilitaryUnit, Long> attackerCasualties, Map<MilitaryUnit, Long> defenderCasualties, HeroType attType, HeroType defType, boolean isAttack, boolean factorInLosses) {
        // double attRatio = 0.06 * (double) defStrength / (double) attStrength;

        // double defRatio = 0.04 * (double) attStrength / (double) defStrength;

        Map<MilitaryUnit, Long> attLossesNormalized = attackerCasualties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (long) (e.getValue() / e.getKey().getCasualtyRatio())));
        Map<MilitaryUnit, Long> defLossesNormalized = defenderCasualties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (long) (e.getValue() / e.getKey().getCasualtyRatio())));

        long attLossStrength = attLossesNormalized.entrySet().stream().mapToLong(e -> (isAttack ? e.getKey().getAttack(attType) : e.getKey().getDefense(attType)) * e.getValue()).sum();
        long defLossStrength = defLossesNormalized.entrySet().stream().mapToLong(e -> (isAttack ? e.getKey().getDefense(defType) : e.getKey().getAttack(defType)) * e.getValue()).sum();

        System.out.println("AttLossStr " + attLossStrength);
        System.out.println("DefLossStr " + defLossStrength);

        double attDefRatio = (double) defLossStrength / (double) attLossStrength;
        double defAttRatio = (double) attLossStrength / (double) defLossStrength;

        double attRatio = (isAttack ? 0.06 / 1.33 : 0.04 * 1.33) * defAttRatio;// / ((double) defLossStrength / attLossStrength); // 1.34
        double defRatio = (isAttack ? 0.04 * 1.33 : 0.06 / 1.33) * attDefRatio;// / ((double) attLossStrength / defLossStrength); // 1.34

        System.out.println("Att Ratio " + attDefRatio + " | " + attRatio);
        System.out.println("Def Ratio " + defAttRatio + " | " + defRatio);

        // attLossStrength

        double attMultiplier = (1d / attLossStrength) * (defLossStrength) / attRatio;
        double defMultiplier = (1d / defLossStrength) * (attLossStrength) / defRatio;

        Map<MilitaryUnit, Long> attUnits = new HashMap<>();
        Map<MilitaryUnit, Long> defUnits = new HashMap<>();
        for (Map.Entry<MilitaryUnit, Long> entry : attLossesNormalized.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            if (unit.isBuildable()) {
                attUnits.put(entry.getKey(), (long) (entry.getValue() * attMultiplier));
            }
        }
        for (Map.Entry<MilitaryUnit, Long> entry : defLossesNormalized.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            if (unit.isBuildable()) {
                defUnits.put(entry.getKey(), (long) (entry.getValue() * defMultiplier));
            }
        }
        if (factorInLosses) {
            for (Map.Entry<MilitaryUnit, Long> entry : attUnits.entrySet()) {
                MilitaryUnit unit = entry.getKey();
                if (unit.isBuildable()) {
                    attUnits.put(entry.getKey(), (long) (entry.getValue() - attackerCasualties.getOrDefault(entry.getKey(), 0L)));
                }
            }
            for (Map.Entry<MilitaryUnit, Long> entry : defUnits.entrySet()) {
                MilitaryUnit unit = entry.getKey();
                if (unit.isBuildable()) {
                    defUnits.put(entry.getKey(), (long) (entry.getValue() - defenderCasualties.getOrDefault(entry.getKey(), 0L)));
                }
            }
        }

        return Map.entry(attUnits, defUnits);
    }

    public static Map<MilitaryUnit, Long> getCasualties(int attStrength, int defStrength, Map<MilitaryUnit, Long> units, boolean isAttack) {
        double attRatio = 0.06 * (double) defStrength / (double) attStrength;
        double defRatio = 0.04 * (double) attStrength / (double) defStrength;

        attRatio = Math.min(attRatio, 0.25);
        defRatio = Math.min(defRatio, 0.20);

        double ratio = isAttack ? attRatio : defRatio;

        Map<MilitaryUnit, Long> casualties = new HashMap<>();
        for (Map.Entry<MilitaryUnit, Long> entry : units.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            if (!unit.isBuildable()) continue;
            long num = entry.getValue();
            if (num == 0) continue;
            long losses = Math.round(num * ratio * unit.casualtyRatio);
            casualties.put(unit, losses);
        }

        return casualties;
    }

    private final static String soldierChar = "\uD83D\uDC82\u200D\uFE0F";
    private final static String archerChar = "\uD83C\uDFF9";
    private final static String cavalryChar = "\uD83C\uDFC7";
    private final static String eliteChar = "\uD83E\uDDD9\u200D\uFE0F";

    public static String getUnitMarkdown(Map<MilitaryUnit, Long> units) {
        StringBuilder body = new StringBuilder();
        Long soldiers = units.getOrDefault(MilitaryUnit.SOLDIER, 0L);
        Long archers = units.getOrDefault(MilitaryUnit.ARCHER, 0L);
        Long cavalry = units.getOrDefault(MilitaryUnit.CAVALRY, 0L);
        Long elites = units.getOrDefault(MilitaryUnit.ELITE, 0L);
        body.append(String.format("%6s", soldiers)).append(" " + soldierChar).append(" | ")
                .append(String.format("%5s", cavalry)).append(" " + archerChar).append(" | ")
                .append(String.format("%5s", archers)).append(" " + cavalryChar).append(" | ")
                .append(String.format("%4s", elites)).append(" " + eliteChar);
        return body.toString();
    }
}
