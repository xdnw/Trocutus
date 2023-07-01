package link.locutus.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import link.locutus.Trocutus;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.TrouncedDB;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.db.entities.war.DBAttack;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TrounceUtil {
    public static String getAlert(Document dom) {
        // get title
        String title = dom.title();
        StringBuilder response = new StringBuilder();
        response.append("Title: ").append(title).append("\n");
        // get alerts with class: text-red-600
        for (Element elementsByClass : dom.getElementsByClass("text-red-600")) {
            response.append(elementsByClass.text()).append("\n");
        }
        return response.toString();

    }

    public static String getAllianceName(int id) {
        DBAlliance alliance = DBAlliance.get(id);
        if (alliance == null) {
            return "" + id;
        }
        return alliance.getName();
    }

    public static long getLandCost(int start) {
        return Math.round(4.3816269236233213e-7 * start * start + 0.20511814302942483 * start - 105.93918145282488);
    }

    public static long getLandCost(int from, int to) {
        if (from < 0 || from == to) return 0;
        if (to <= from) return 0;
        if (to > 1000000) throw new IllegalArgumentException("Land cannot exceed 1000000");
        // in groups of 1000
        long total = 0;
        for (double i = from; i < to; i += 1000) {
            long cost = getLandCost((int) i);
            double amt = Math.min(1000, to - i);
            total += cost * amt;
        }
        return total;
    }

    public static Map<MilitaryUnit, Long> toConvertedCost(Map<MilitaryUnit, Long> input) {
        Map<MilitaryUnit, Long> convertedCost = new EnumMap<>(MilitaryUnit.class);
        for (Map.Entry<MilitaryUnit, Long> entry : input.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            long amount = entry.getValue();
            convertedCost = ArrayUtil.add(convertedCost, Collections.singletonMap(MilitaryUnit.GOLD, (long) unit.getGoldValue() * amount));
            convertedCost = ArrayUtil.add(convertedCost, Collections.singletonMap(MilitaryUnit.MANA, (long) unit.getManaValue() * amount));
            convertedCost = ArrayUtil.add(convertedCost, Collections.singletonMap(MilitaryUnit.BATTLE_POINTS, (long) unit.getBpValue() * amount));
            convertedCost = ArrayUtil.add(convertedCost, Collections.singletonMap(MilitaryUnit.EXP, (long) unit.getExpValue() * amount));
        }
        convertedCost.entrySet().removeIf(f -> f.getValue() == 0);
        return convertedCost;
    }

    public static int getMaxScoreRange(int score, boolean declare) {
        return (int) (declare ? (score * 1.5) : (score / 0.66));
    }

    public static int getMinScoreRange(int score, boolean declare) {
        return (int) (declare ? (score * 0.66) : (score / 1.5));
    }

    public static String resourcesToString(Map<MilitaryUnit, ? extends Number> resources) {
        Map<MilitaryUnit, String> newMap = new LinkedHashMap<>();
        for (MilitaryUnit resourceType : MilitaryUnit.values()) {
            if (resources.containsKey(resourceType)) {
                Number value = resources.get(resourceType);
                if (value.doubleValue() == 0) continue;
                if (value.doubleValue() == value.longValue()) {
                    newMap.put(resourceType, MathMan.format(value.longValue()));
                } else {
                    newMap.put(resourceType, MathMan.format(value.doubleValue()));
                }
            }
        }
        return StringMan.getString(newMap);
    }

    public static String getName(int id, boolean isAA) {
        if (isAA) {
            DBAlliance aa = DBAlliance.get(id);
            if (aa != null) {
                return aa.getName();
            } else {
                return "AA:" + id;
            }
        } else {
            DBKingdom kd = DBKingdom.get(id);
            if (kd != null) {
                return kd.getName();
            } else {
                return "" + id;
            }
        }
    }

    public static String getMarkdownUrl(int id, boolean isAA) {
        return MarkupUtil.markdownUrl(getName(id, isAA), getUrl(id, isAA));
    }

    public static String getUrl(int id, boolean isAA) {
        if (isAA) {
            DBAlliance aa = DBAlliance.get(id);
            if (aa != null) {
                return aa.getUrl(null);
            } else {
                return "AA:" + id;
            }
        } else {
            DBKingdom kingdom = DBKingdom.get(id);
            if (kingdom != null) {
                return kingdom.getUrl(null);
            } else {
                return "" + id;
            }
        }
    }

    public static int landLoot(int attackerAcres, int defenderAcres, boolean isFey) {
        double defenderRatio = (double) defenderAcres / attackerAcres;
        double loot = 0.1 * attackerAcres * Math.pow(defenderRatio, 2.2);
        if (isFey) loot = Math.min(loot, 1000);
        return (int) (Math.round(loot) * 0.5);
    }

    public static double getFeyLand(int attack) {
        double valueLow = Math.sqrt(20000d * attack / 20d);
        double valueMid = 10 * (Math.sqrt(5) * Math.sqrt(3 * attack + 50) - 500);
        double valueHigh = attack / 40d;

        if (valueHigh < 50000) {
            if (valueMid < 20000 || valueMid >= 50000) {
                return valueLow;
            }
            return valueMid;
        }
        return valueHigh;
    }

    public static double getLandValue(int currentLand, int amt) {
        return getFeyDPA(currentLand) * 50 * amt;
    }

    public static double getFeyDPA(int land) {
        double dpa;
        if (land < 20000) {
            dpa = 20 * land / 20000d;
        } else if (land <= 50000) {
            dpa = 20 + 20 * (land - 20000) / 30000d;
        } else {
            dpa = 40;
        }
        return Math.max(3, dpa);
    }

    public static BiFunction<Double, Double, Integer> getIsKingdomsInSpyRange(Collection<DBKingdom> attackers) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBKingdom attacker : attackers) {
            minScore = (int) Math.min(minScore, getMinScoreRange(attacker.getScore(), true));
            maxScore = (int) Math.max(maxScore, getMaxScoreRange(attacker.getScore(), true));
        }
        int[] scoreRange = new int[maxScore + 1];
        for (DBKingdom attacker : attackers) {
            scoreRange[(int) attacker.getScore()]++;
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Integer>() {
            @Override
            public Integer apply(Double min, Double max) {
                int minVal = min == 0 ? 0 : scoreRange[Math.min(scoreRange.length - 1, min.intValue() - 1)];
                int maxVal = scoreRange[Math.min(scoreRange.length - 1, max.intValue())];
                return maxVal - minVal;
            }
        };
    }

    public static BiFunction<Double, Double, Integer> getIsKingdomsInScoreRange(Collection<DBKingdom> attackers) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBKingdom attacker : attackers) {
            minScore = (int) Math.min(minScore, getMinScoreRange(attacker.getScore(), false));
            maxScore = (int) Math.max(maxScore, getMaxScoreRange(attacker.getScore(), false));
        }
        int[] scoreRange = new int[maxScore + 1];
        for (DBKingdom attacker : attackers) {
            scoreRange[(int) attacker.getScore()]++;
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Integer>() {
            @Override
            public Integer apply(Double min, Double max) {
                max = Math.min(scoreRange.length - 1, max);
                min = Math.min(scoreRange.length - 1, min);
                return scoreRange[(int) Math.ceil(max)] - scoreRange[min.intValue()];
            }
        };
    }

    public static BiFunction<Double, Double, Double> getXInRange(Collection<DBKingdom> attackers, Function<DBKingdom, Double> valueFunc) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBKingdom attacker : attackers) {
            minScore = (int) Math.min(minScore, getMinScoreRange(attacker.getScore(), false));
            maxScore = (int) Math.max(maxScore, getMaxScoreRange(attacker.getScore(), false));
        }
        double[] scoreRange = new double[maxScore + 1];
        for (DBKingdom attacker : attackers) {
            scoreRange[(int) attacker.getScore()] += valueFunc.apply(attacker);
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Double>() {
            @Override
            public Double apply(Double min, Double max) {
                max = Math.min(scoreRange.length - 1, max);
                return scoreRange[(int) Math.ceil(max)] - scoreRange[min.intValue()];
            }
        };
    }

    public static Map<MilitaryUnit, Long> parseUnits(String arg) {
        arg = arg.trim();
        if (!arg.contains(":") && !arg.contains("=")) arg = arg.replaceAll("[ ]+", ":");
        arg = arg.replace(" ", "").replace('=', ':').replaceAll("([0-9]),([0-9])", "$1$2").toUpperCase();

        double sign = 1;
        if (arg.charAt(0) == '-') {
            sign = -1;
            arg = arg.substring(1);
        }
        int preMultiply = arg.indexOf("*{");
        int postMultiply = arg.indexOf("}*");
        if (preMultiply != -1) {
            String[] split = arg.split("\\*\\{", 2);
            arg = "{" + split[1];
            sign *= MathMan.parseDouble(split[0]);
        }
        if (postMultiply != -1) {
            String[] split = arg.split("\\}\\*", 2);
            arg = split[0] + "}";
            sign *= MathMan.parseDouble(split[1]);
        }

        Type type = new TypeToken<Map<MilitaryUnit, Long>>() {}.getType();
        if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
            arg = "{" + arg + "}";
        }
        Map<MilitaryUnit, Long> result = new Gson().fromJson(arg, type);
        if (result.containsKey(null)) {
            throw new IllegalArgumentException("Invalid resource type specified in map: `" + arg + "`");
        }
        if (sign != 1) {
            for (Map.Entry<MilitaryUnit, Long> entry : result.entrySet()) {
                entry.setValue((long) (entry.getValue() * sign));
            }
        }
        return result;
    }

    public static void test() {
        DBKingdom me = DBKingdom.parse("locutus");
        DBAttack myAttack = me.getLatestOffensive();
        Map<MilitaryUnit, Long> attackerLosses = myAttack.getCost(true);
        Map<MilitaryUnit, Long> defenderLosses = myAttack.getCost(false);
        System.out.println("Attacker losses2: " + attackerLosses);
        System.out.println("Defender losses2: " + defenderLosses);
        DBKingdom attacker = myAttack.getAttacker();
        DBKingdom defender = myAttack.getDefender();

        System.out.println("Attacker " + attacker.getName() + " | " + attacker.getHero());
        System.out.println("Defender " + defender.getName() + " | " + defender.getHero());

        Map.Entry<Map<MilitaryUnit, Long>, Map<MilitaryUnit, Long>> strength = MilitaryUnit.getUnits(attackerLosses, defenderLosses, attacker.getHero(), defender.getHero(), true, myAttack.victory, true);
        System.out.println("Attacker strength " + TrounceUtil.resourcesToString(strength.getKey()));
        System.out.println("Defender strength " + TrounceUtil.resourcesToString(strength.getValue()));
//        System.out.println("Defender strength 2 " + MilitaryUnit.getOpponentStrength(attackerLosses, false, HeroType.MAGICIAN));

//        Map<MilitaryUnit, Long> attUnits = MilitaryUnit.getMyUnits(myAttack.getAttacker().getHero(), myAttack.getDefender().getHero(), attackerLosses, defenderLosses, true);
//        Map<MilitaryUnit, Long> defUnits = MilitaryUnit.getMyUnits(myAttack.getDefender().getHero(), myAttack.getAttacker().getHero(), defenderLosses, attackerLosses, false);
//        System.out.println("Att units: " + attUnits);
//        System.out.println("Att Attack " + MilitaryUnit.getAttack(attUnits, me.getHero()));
//        System.out.println("Att Defense " + MilitaryUnit.getDefense(attUnits, me.getHero()));
//        System.out.println("Def units: " + defUnits);
//        System.out.println("Def Attack " + MilitaryUnit.getAttack(defUnits, me.getHero()));
//        System.out.println("Def Defense " + MilitaryUnit.getDefense(defUnits, me.getHero()));
    }

    public static void test2() throws SQLException {
        int attAtt = 246008;
        int attDef = 192069;

        int defAtt = 88689;
        int defDef = 105038;

        int attSoldier = 160944;
        int attCavalry = 315;
        int attArcher = 1166;
        int attElite = 2673;

        int defSoldier = 44134;
        int defCavalry = 4;
        int defArcher = 1153;
        int defElite = 6869;

        int attSoldierLoss = 3533; // 2.1951734764887165722238791132319
        int attCavalryLoss = 6; // 1.746031746031746031746031746032 - 2.063492063492063492063492063492
        int attArcherLoss = 20; // 1.672384219554030874785591766724 - 1.758147512864493996569468267581
        int attEliteLoss = 53; // 1.964085297418630751964085297419 - 2.0014964459408903853348297792742

        int defSoldierLoss = 5946;
        int defCavalryLoss = 0;
        int defArcherLoss = 143;
        int defEliteLoss = 657;

        TrouncedDB db = new TrouncedDB("trounced2");
        System.out.println("Loaded db");
        Set<DBKingdom> kingdoms = db.getKingdomsMatching(f -> f.getHero() == HeroType.AUTOMATOR);
        Set<Integer> kingdomIds = kingdoms.stream().map(DBKingdom::getId).collect(Collectors.toSet());

        List<DBAttack> allAttacks = db.getAttacks(f -> kingdomIds.contains(f.getDefender_id()));

        Map<DBKingdom, Set<DBAttack>> attacksByDefender = new HashMap<>();
        for (DBAttack attack : allAttacks) {
            attacksByDefender.computeIfAbsent(attack.getDefender(), k -> new HashSet<>()).add(attack);
        }

        System.out.println("num kindgoms " + kingdoms.size());
        System.out.println("num attacks " + allAttacks.size());
        // atacker or defender in kingdom ids
        List<DBSpy> allspies = db.getSpiesMatching(f -> kingdomIds.contains(f.getAttacker_id()) || kingdomIds.contains(f.getDefender_id()));
        System.out.println("Spy ops " + allspies.size());
        Map<DBKingdom, Set<DBSpy>> spiesByKingdom = new HashMap<>();
        for (DBSpy spy : allspies) {
            spiesByKingdom.computeIfAbsent(spy.getAttacker(), k -> new HashSet<>()).add(spy);
            spiesByKingdom.computeIfAbsent(spy.getDefender(), k -> new HashSet<>()).add(spy);
        }

        Map<DBAttack, DBSpy> defSpyOps = new HashMap<>();
        Map<DBAttack, DBSpy> attSpyOps = new HashMap<>();

        for (Map.Entry<DBKingdom, Set<DBAttack>> entry : attacksByDefender.entrySet()) {
            DBKingdom defender = entry.getKey();
            Set<DBAttack> attacks = entry.getValue();
            Set<DBSpy> spies = spiesByKingdom.get(defender);
            if (spies == null) continue;
            System.out.println("Found spies " + spies.size());
            for (DBAttack attack : attacks) {
                for (DBSpy spy : spies) {
                    // if within 3m of each other
                    if (Math.abs(spy.date - attack.date) < TimeUnit.MINUTES.toMillis(5)) {
                        if (spy.getAttacker().equals(defender)) {
                            attSpyOps.put(attack, spy);
                        } else {
                            defSpyOps.put(attack, spy);
                        }
                    }
                }
            }
        }

        System.out.println("att spy " + attSpyOps.size());
        System.out.println("def spy " + defSpyOps.size());
        Map<MilitaryUnit, List<Double>> factorsByUnit = new HashMap<>();

//        if (false)
//        for (Map.Entry<DBAttack, DBSpy> entry : defSpyOps.entrySet())
//        {
//            DBAttack attack = entry.getKey();
//            DBKingdom attacker = attack.getAttacker();
//            if (attacker == null) continue;
//            DBSpy attSpyInfo = attSpyOps.get(attack);
//            if (attSpyInfo == null) continue;
//            DBSpy defSpyInfo = entry.getValue();
//            HeroType attHero = attack.getAttacker().getHero();
//            HeroType defHero = attack.getDefender().getHero();
//
//
//            int defenderDefense = defSpyInfo.defense;
//            int attackerAttack = attSpyInfo.attack;
//
//            // Soldier
//            {
//                int soldierAttack = attSpyInfo.soldiers;
//                if (soldierAttack < 100 || attack.attacker_soldiers < 10) continue;
//                double percentAttackIsSoldiers = (double) soldierAttack / (double) attackerAttack;
//                int soldierLosses = attack.attacker_soldiers;
//                double factor = (double) soldierLosses / (percentAttackIsSoldiers * defenderDefense);
//                factorsByUnit.computeIfAbsent(MilitaryUnit.SOLDIER, k -> new ArrayList<>()).add(factor);
//            }
//
//            // Cavalry
//            {
//                int cavalryAttack = attSpyInfo.cavalry;
//                if (cavalryAttack < 100 || attack.attacker_cavalry < 10) continue;
//                double percentAttackIsCavalry = (double) cavalryAttack / (double) attackerAttack;
//                int cavalryLosses = attack.attacker_cavalry;
//                double factor = (double) cavalryLosses / (percentAttackIsCavalry * defenderDefense);
//                factorsByUnit.computeIfAbsent(MilitaryUnit.CAVALRY, k -> new ArrayList<>()).add(factor);
//            }
//            // archer
//            {
//                int archerAttack = attSpyInfo.archers;
//                if (archerAttack < 100 || attack.attacker_archers < 10) continue;
//                double percentAttackIsArcher = (double) archerAttack / (double) attackerAttack;
//                int archerLosses = attack.attacker_archers;
//                double factor = (double) archerLosses / (percentAttackIsArcher * defenderDefense);
//                factorsByUnit.computeIfAbsent(MilitaryUnit.ARCHER, k -> new ArrayList<>()).add(factor);
//            }
//            // elite
//            {
//                int eliteAttack = attSpyInfo.elites;
//                if (eliteAttack < 100 || attack.attacker_elites < 10) continue;
//                double percentAttackIsElite = (double) eliteAttack / (double) attackerAttack;
//                int eliteLosses = attack.attacker_elites;
//                double factor = (double) eliteLosses / (percentAttackIsElite * defenderDefense);
//                factorsByUnit.computeIfAbsent(MilitaryUnit.ELITE, k -> new ArrayList<>()).add(factor);
//            }
//        }

        for (Map.Entry<DBAttack, DBSpy> entry : defSpyOps.entrySet()) {
            DBAttack attack = entry.getKey();
            DBKingdom attacker = attack.getAttacker();
            if (attacker == null) continue;
            DBSpy attSpyInfo = attSpyOps.get(attack);
            if (attSpyInfo == null) continue;
            DBSpy defSpyInfo = entry.getValue();
            HeroType attHero = attack.getAttacker().getHero();
            HeroType defHero = attack.getDefender().getHero();


            int defenderDefense = defSpyInfo.defense;
            int attackerAttack = attSpyInfo.attack;

            // Soldier Defense
            {
                int soldierDefence = defSpyInfo.soldiers;
                if (soldierDefence < 100 || attack.defender_soldiers < 10) continue;
                int attackerUnits = attSpyInfo.soldiers + attSpyInfo.cavalry + attSpyInfo.archers + attSpyInfo.elites;
                int defenderUnits = defSpyInfo.soldiers + defSpyInfo.cavalry + defSpyInfo.archers + defSpyInfo.elites;
                double percentDefenseIsSoldiers = (double) soldierDefence / (double) defenderDefense;
                int soldierLosses = attack.defender_soldiers;
                double factor = (double) soldierLosses / (percentDefenseIsSoldiers * attackerAttack);
                factorsByUnit.computeIfAbsent(MilitaryUnit.SOLDIERS, k -> new ArrayList<>()).add(factor);
            }

            // Cavalry Defense
            {
                // Elite units have -20% casualties.
                int cavalryDefence = defSpyInfo.cavalry;
                if (cavalryDefence < 100 || attack.defender_cavalry < 10) continue;
                double percentDefenseIsCavalry = (double) cavalryDefence / (double) defenderDefense;
                int cavalryLosses = attack.defender_cavalry;
                double factor = (double) cavalryLosses / (percentDefenseIsCavalry * attackerAttack);
                factorsByUnit.computeIfAbsent(MilitaryUnit.CAVALRY, k -> new ArrayList<>()).add(factor);
            }

            // Archer Defense
            {
                int archerDefence = defSpyInfo.archers;
                if (archerDefence < 100 || attack.defender_archers < 10) continue;
                double percentDefenseIsArcher = (double) archerDefence / (double) defenderDefense;
                int archerLosses = attack.defender_archers;
                double factor = (double) archerLosses / (percentDefenseIsArcher * attackerAttack);
                factorsByUnit.computeIfAbsent(MilitaryUnit.ARCHER, k -> new ArrayList<>()).add(factor);
            }

            // elite defense
            {
                int eliteDefence = defSpyInfo.elites;
                if (eliteDefence < 100 || attack.defender_elites < 10) continue;
                double percentDefenseIsElite = (double) eliteDefence / (double) defenderDefense;
                int eliteLosses = attack.defender_elites;
                double factor = (double) eliteLosses / (percentDefenseIsElite * attackerAttack);
                factorsByUnit.computeIfAbsent(MilitaryUnit.ELITE, k -> new ArrayList<>()).add(factor);
            }
        }

        for (Map.Entry<MilitaryUnit, List<Double>> entry : factorsByUnit.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            List<Double> factors = entry.getValue();
            // sort
            factors.sort(Comparator.naturalOrder());
            // remove infinity and <= 0
            factors = factors.stream().filter(f -> f > 0 && !Double.isInfinite(f)).collect(Collectors.toList());
            // remove when more than 50% from average
            double avg = factors.stream().mapToDouble(f -> f).average().getAsDouble();
            List<Double> filteredFactors = factors;//.stream().filter(f -> Math.abs(f - avg) < 0.5 * avg).collect(Collectors.toList());

            // remove outliers from factors
//            double avg = factors.stream().mapToDouble(f -> f).average().getAsDouble();
//            double stdDev = Math.sqrt(factors.stream().mapToDouble(f -> Math.pow(f - avg, 2)).sum() / factors.size());
//            List<Double> filteredFactors = factors.stream().filter(f -> Math.abs(f - avg) < 2 * stdDev).collect(Collectors.toList());


            double sum = filteredFactors.stream().mapToDouble(f -> f).sum();
            double newAvg = sum / filteredFactors.size();
            System.out.println("# " + unit + " | " + newAvg);
            for (double factor : filteredFactors) {
                System.out.println(factor);
            }
            System.out.println("\n\n");
        }
    }

    public static double getTaxrate(int sender_score, int receiver_score) {
        return Math.min(Math.max(Math.abs((sender_score - receiver_score) / (double) (sender_score + receiver_score)), 0.1), 1);
    }

    public static long applyTax(int sender_score, int receiver_score, long amount) {
        double taxRate = getTaxrate(sender_score, receiver_score);
        return Math.round(amount * taxRate / 100);
    }

    public static DBKingdom getNearestFey(int realmId, int land, int minScore, int maxScore, boolean fetch) {
        List<DBKingdom> result = getNearestFey(realmId, land, minScore, maxScore, fetch, 1);
        return result.isEmpty() ? null : result.get(0);
    }
    public static List<DBKingdom> getNearestFey(int realmId, int land, int minScore, int maxScore, boolean fetch, int amt) {
        if (fetch) {
            try {
                Trocutus.imp().getScraper().fetchFey(PagePriority.FETCH_FEY_FG, realmId, land);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<DBKingdom> inRange = new ArrayList<>(Trocutus.imp().getDB().getFeyMatching(kingdom -> {
            if (kingdom.getRealm_id() != realmId) return false;
            if (kingdom.getTotal_land() < minScore || kingdom.getTotal_land() > maxScore) return false;
            return true;
        }));
        // sort by distance to land
        inRange.sort(Comparator.comparingLong(kingdom -> Math.abs(kingdom.getTotal_land() - land)));
        // return first amt
        return inRange.subList(0, Math.min(amt, inRange.size()));
    }
}
