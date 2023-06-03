package link.locutus.core.api.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum MilitaryUnit {
    SOLDIERS(1, true, 100, 0, 0, 0, 1, 1, 1),
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
                case FEY -> 3;
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
                case FEY -> 3;
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
    EXP(false, 0, 0, 1, 0, 0, 0, 0),
    BATTLE_POINTS(false, 0, 0, 0, 1, 0, 0, 0)


    ;

    public static MilitaryUnit[] values = values();
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

    public static Map.Entry<Map<MilitaryUnit, Long> , Map<MilitaryUnit, Long>> getUnits(Map<MilitaryUnit, Long> myCasualties, Map<MilitaryUnit, Long> opponentCasualties, HeroType myType, HeroType opponentType, boolean isAttack, boolean isVictory, boolean factorInLosses) {
        myCasualties = new EnumMap<>(myCasualties);
        opponentCasualties = new EnumMap<>(opponentCasualties);
        // remove non buildable
        myCasualties.entrySet().removeIf(f -> !f.getKey().isBuildable());
        opponentCasualties.entrySet().removeIf(f -> !f.getKey().isBuildable());

        int intervals = 1000;
        double incrementAtt = 25d / intervals;
        double incrementDef = 20d / intervals;

        Map<MilitaryUnit, Long> attLossesNormalized = myCasualties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (long) (e.getValue() / e.getKey().getCasualtyRatio())));
        Map<MilitaryUnit, Long> defLossesNormalized = opponentCasualties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (long) (e.getValue() / e.getKey().getCasualtyRatio())));

        List<Double> attPcts = new ArrayList<>();
        List<Map<MilitaryUnit, Long>> attTotalAtPct = new ArrayList<>();
        List<Long> attStrength = new ArrayList<>();

        List<Double> defPcts = new ArrayList<>();
        List<Map<MilitaryUnit, Long>> defTotalAtPct = new ArrayList<>();
        List<Long> defStrength = new ArrayList<>();

        for (double attPct = incrementAtt; attPct <= 25; attPct += incrementAtt) {
            double attackFactor = 100 / attPct;
            Map<MilitaryUnit, Long> result = new EnumMap<>(MilitaryUnit.class);
            attLossesNormalized.forEach((k, v) -> result.put(k, (long) (v * attackFactor)));

            attPcts.add(attPct);
            attTotalAtPct.add(result);
            attStrength.add((long) (isAttack ? getAttack(result, myType) : getDefense(result, myType)));
        }

        for (double defPct = incrementDef; defPct <= 20; defPct += incrementDef) {
            double defFactor = 100 / defPct;
            Map<MilitaryUnit, Long> result = new EnumMap<>(MilitaryUnit.class);
            defLossesNormalized.forEach((k, v) -> result.put(k, (long) (v * defFactor)));

            defPcts.add(defPct);
            defTotalAtPct.add(result);
            defStrength.add((long) (isAttack ? getDefense(result, opponentType) : getAttack(result, opponentType)));
        }

        int attIClosest = 0;
        int defJClosest = 0;
        long closest = Long.MAX_VALUE;

        Map<MilitaryUnit, Long> finalMyCasualties = myCasualties;
        Map<MilitaryUnit, Long> finalOpponentCasualties = opponentCasualties;

        for (int i = 0; i < attPcts.size(); i++) {
            double attPct = attPcts.get(i);
            Map<MilitaryUnit, Long> attMap = attTotalAtPct.get(i);
            long attStr = attStrength.get(i);
            for (int j = 0; j < defPcts.size(); j++) {
                double defPct = defPcts.get(j);
                Map<MilitaryUnit, Long> defMap = defTotalAtPct.get(j);
                long defStr = defStrength.get(j);

                if (isVictory && attStr < defStr) continue;
                if (!isVictory && attStr > defStr) continue;

                Map<MilitaryUnit, Long> myCasualtiesEst = getCasualties((int) attStr, (int) defStr, attMap, isAttack);
                Map<MilitaryUnit, Long> opponentCasualtiesEst = getCasualties((int) defStr, (int) attStr, defMap, !isAttack);

                // sum diff between myCasualtiesEst and myCasualties
                long myDiff = myCasualtiesEst.entrySet().stream().mapToLong(e -> Math.abs(e.getValue() - finalMyCasualties.getOrDefault(e.getKey(), 0L))).sum();
                long opponentDiff = opponentCasualtiesEst.entrySet().stream().mapToLong(e -> Math.abs(e.getValue() - finalOpponentCasualties.getOrDefault(e.getKey(), 0L))).sum();

                long totalDiff = myDiff + opponentDiff;
                if (totalDiff < closest) {
                    closest = totalDiff;
                    attIClosest = i;
                    defJClosest = j;
                }
            }
        }

        Map<MilitaryUnit, Long> attUnits = attTotalAtPct.get(attIClosest);
        Map<MilitaryUnit, Long> defUnits = defTotalAtPct.get(defJClosest);

        System.out.println("att units: " + attUnits);
        System.out.println("def units: " + defUnits);
        return Map.entry(attUnits, defUnits);
    }

    public static Map<MilitaryUnit, Long> getCasualties(int attStrength, int defStrength, Map<MilitaryUnit, Long> units, boolean isAttack) {
        double attRatio = 0.06 * (double) defStrength / (double) attStrength;
        double defRatio = 0.04 * (double) attStrength / (double) defStrength;

        attRatio = Math.min(attRatio, 0.25);
        defRatio = Math.min(defRatio, 0.20);

        double ratio = isAttack ? attRatio : defRatio;

        Map<MilitaryUnit, Long> casualties = new EnumMap<>(MilitaryUnit.class);
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
        Long soldiers = units.getOrDefault(MilitaryUnit.SOLDIERS, 0L);
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
