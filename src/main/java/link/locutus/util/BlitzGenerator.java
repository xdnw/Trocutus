package link.locutus.util;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.Activity;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.util.spreadsheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class BlitzGenerator {
    private static final double AIR_FACTOR = 2;

    private final boolean indefinate;
    private final double attActiveThreshold;
    private final double defActiveThreshold;
    private final Set<Guild> guilds;
    private final int turn;
    private final int maxAttacksPerKingdom;
    private final double sameAAPriority;
    private final double activityMatchupPriority;
    private final boolean parseExisting;

    private Function<DBKingdom, Activity> weeklyCache;
    Set<DBKingdom> colA = new HashSet<>();
    Set<DBKingdom> colB = new HashSet<>();

    private boolean assignEasy;

    public BlitzGenerator(int turn, int maxAttacksPerKingdom, double sameAAPriority, double activityMatchupPriority, double attActiveThreshold, double defActiveThreshold, Set<Long> guildsIds, boolean parseExisting) {
        this.sameAAPriority = sameAAPriority;
        this.activityMatchupPriority = activityMatchupPriority;
        this.maxAttacksPerKingdom = maxAttacksPerKingdom;
        this.weeklyCache = Activity.createCache(14 * 12);
        this.indefinate = turn == -1;
        this.turn = turn;
        this.attActiveThreshold = attActiveThreshold;
        this.defActiveThreshold = defActiveThreshold;
        this.parseExisting = parseExisting;

        this.guilds = new HashSet<>();

        for (Long discordId : guildsIds) {
            Guild guild = Trocutus.imp().getDiscordApi().getGuildById(discordId);
            guilds.add(guild);
        }
    }

    private void setEasy() {
        this.assignEasy = true;
    }

    public static Map<DBKingdom, Set<DBKingdom>> reverse(Map<DBKingdom, Set<DBKingdom>> targets) {
        Map<DBKingdom, Set<DBKingdom>> reversed = new LinkedHashMap<>();
        for (Map.Entry<DBKingdom, Set<DBKingdom>> entry : targets.entrySet()) {
            DBKingdom defender = entry.getKey();
            for (DBKingdom attacker : entry.getValue()) {
                reversed.computeIfAbsent(attacker, f -> new LinkedHashSet<>()).add(defender);
            }
        }
        return reversed;
    }

    private static void process(DBKingdom attacker, DBKingdom defender, double minScoreMultiplier, double maxScoreMultiplier, boolean checkUpdeclare, boolean checkWarSlots, boolean checkSpySlots, BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String> invalidOut) {
        double minScore = attacker.getScore() * minScoreMultiplier;
        double maxScore = attacker.getScore() * maxScoreMultiplier;

        if (defender.getScore() < minScore) {
            double diff = Math.round((minScore - defender.getScore()) * 100) / 100d;
            String response = ("`" + defender.getKingdom() + "` is " + MathMan.format(diff) + "ns below " + "`" + attacker.getKingdom() + "`");
            invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
        }
        else if (defender.getScore() > maxScore) {
            double diff = Math.round((defender.getScore() - maxScore) * 100) / 100d;
            String response = ("`" + defender.getKingdom() + "` is " + MathMan.format(diff) + "ns above " + "`" + attacker.getKingdom() + "`");
            invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
        } else if (checkUpdeclare && getAirStrength(defender, false) > getAirStrength(attacker, true) * 1.33) {
            double ratio = getAirStrength(defender, false) / getAirStrength(attacker, true);
            String response = ("`" + defender.getKingdom() + "` is " + MathMan.format(ratio) + "x stronger than " + "`" + attacker.getKingdom() + "`");
            invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
        } else if (checkWarSlots && defender.getDef() == 3) {
            String response = ("`" + defender.getKingdom() + "` is slotted");
            invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
        } else if (checkSpySlots && !defender.isEspionageAvailable()) {
            String response = ("`" + defender.getKingdom() + "` is spy slotted");
            invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
        }
    }

    public static Map<DBKingdom, Set<DBKingdom>> getTargets(SpreadSheet sheet, int headerRow) {
        return getTargets(sheet, headerRow, f -> Integer.MAX_VALUE, 0, Integer.MAX_VALUE, false, false, false, f -> true, (a, b) -> {});
    }

    public static Map<DBKingdom, Set<DBKingdom>> getTargets(SpreadSheet sheet, int headerRow, Function<DBKingdom, Integer> maxWars, double minScoreMultiplier, double maxScoreMultiplier, boolean checkUpdeclare, boolean checkWarSlotted, boolean checkSpySlotted, Function<DBKingdom, Boolean> isValidTarget, BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String> invalidOut) {
        List<List<Object>> rows = sheet.getAll();
        List<Object> header = rows.get(headerRow);

        Integer targetI = null;
        Integer allianceI = null;
        Integer attI = null;
        Integer att2 = null;
        Integer att3 = null;
        List<Integer> targetsIndexesRoseFormat = new ArrayList<>();

        boolean isReverse = false;
        for (int i = 0; i < header.size(); i++) {
            Object obj = header.get(i);
            if (obj == null) continue;
            String title = obj.toString();
            if (title.equalsIgnoreCase("alliance") && allianceI == null) {
                allianceI = i;
            }
            if (title.equalsIgnoreCase("nation") || title.equalsIgnoreCase("nation name") || (title.equalsIgnoreCase("link") && targetI == null)) {
                targetI = i;
            }
            if (title.equalsIgnoreCase("att1") || title.equalsIgnoreCase("Fighter #1") || title.equalsIgnoreCase("Attacker 1")) {
                attI = i;
            }
            if (title.equalsIgnoreCase("att2") || title.equalsIgnoreCase("Fighter #2") || title.equalsIgnoreCase("Attacker 2")) {
                att2 = i;
            }
            if (title.equalsIgnoreCase("att3") || title.equalsIgnoreCase("Fighter #3") || title.equalsIgnoreCase("Attacker 3")) {
                att3 = i;
            }
            else if (title.equalsIgnoreCase("def1")) {
                attI = i;
                isReverse = true;
            } else if (title.toLowerCase().startsWith("spy slot ")) {
                targetsIndexesRoseFormat.add(i);
                targetI = 0;
            }
        }
        Set<DBKingdom> allAttackers = new LinkedHashSet<>();
        Set<DBKingdom> allDefenders = new LinkedHashSet<>();
        Map<DBKingdom, Set<DBKingdom>> targets = new LinkedHashMap<>();
        Map<DBKingdom, Set<DBKingdom>> offensiveWars = new LinkedHashMap<>();

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row == null) continue;
            if (row.isEmpty() || row.size() <= targetI) continue;

            Object cell = row.get(targetI);
            if (cell == null || cell.toString().isEmpty()) continue;
            String nationStr = cell.toString();
            if (nationStr.contains(" / ")) nationStr = nationStr.split(" / ")[0];
            DBKingdom nation = DiscordUtil.parseKingdom(nationStr);

            DBKingdom attacker = isReverse ? nation : null;
            DBKingdom defender = !isReverse ? nation : null;

            if (nation == null) {
                String response = ("`" + cell.toString() + "` is an invalid nation\n");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
                continue;
            }

            if (allianceI != null && rows.size() > allianceI) {
                Object aaCell = row.get(allianceI);
                if (aaCell != null) {
                    String allianceStr = aaCell.toString();
                    DBAlliance alliance = Locutus.imp().getKingdomDB().getAllianceByName(allianceStr);
                    if (alliance != null && nation.getAlliance_id() != alliance.getAlliance_id()) {
                        String response = ("Kingdom: `" + nationStr + "` is no longer in alliance: `" + allianceStr + "`\n");
                        invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
                    }
                }
            }

            if (attI != null) {
                boolean finalIsReverse = isReverse;
                DBKingdom finalDefender = defender;
                DBKingdom finalAttacker = attacker;
                Consumer<Integer> onRow = new Consumer<>() {
                    @Override
                    public void accept(Integer j) {
                        if (j >= row.size()) return;
                        DBKingdom defenderMutable = finalDefender;
                        DBKingdom attackerMutable = finalAttacker;
                        Object cell = row.get(j);
                        if (cell == null || cell.toString().isEmpty()) return;
                        DBKingdom other = DiscordUtil.parseKingdom(cell.toString().split("\\|")[0]);

                        if (finalIsReverse) {
                            defenderMutable = other;
                        } else {
                            attackerMutable = other;
                        }

                        if (other == null) {
                            String response = ("`" + cell.toString() + "` is an invalid nation\n");
                            invalidOut.accept(new AbstractMap.SimpleEntry<>(defenderMutable, attackerMutable), response);
                            return;
                        }

                        process(attackerMutable, defenderMutable, minScoreMultiplier, maxScoreMultiplier, checkUpdeclare, checkWarSlotted, checkSpySlotted, invalidOut);

                        allAttackers.add(attackerMutable);
                        allDefenders.add(defenderMutable);

                        targets.computeIfAbsent(defenderMutable, f -> new LinkedHashSet<>()).add(attackerMutable);
                        offensiveWars.computeIfAbsent(attackerMutable, f -> new LinkedHashSet<>()).add(defenderMutable);
                    }
                };
                if (att2 == null && att3 == null) {
                    for (int j = attI; j < row.size(); j++) {
                        onRow.accept(j);
                    }
                } else {
                    onRow.accept(attI);
                    onRow.accept(att2);
                    onRow.accept(att3);
                }

            } else if (!targetsIndexesRoseFormat.isEmpty()) {
                for (Integer j : targetsIndexesRoseFormat) {
                    cell = row.get(j);
                    if (cell == null || cell.toString().isEmpty()) continue;
                    DBKingdom other = DiscordUtil.parseKingdom(cell.toString().split(" / ")[0]);
                    if (isReverse) {
                        defender = other;
                    } else {
                        attacker = other;
                    }
                    if (other == null) {
                        String response = ("`" + cell.toString() + "` is an invalid nation\n");
                        invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
                        continue;
                    }
                    DBKingdom tmp = attacker;
                    attacker = defender;
                    defender = tmp;

                    allAttackers.add(attacker);
                    allDefenders.add(defender);

                    targets.computeIfAbsent(defender, f -> new LinkedHashSet<>()).add(attacker);
                    offensiveWars.computeIfAbsent(attacker, f -> new LinkedHashSet<>()).add(defender);
                }

            } else {
                throw new IllegalArgumentException("No targets found");
            }
        }

        for (Map.Entry<DBKingdom, Set<DBKingdom>> entry : offensiveWars.entrySet()) {
            DBKingdom attacker = entry.getKey();
            Set<DBKingdom> defenders = entry.getValue();
            if (defenders.size() > maxWars.apply(attacker)) {
                String response = ("`" + attacker.getKingdom() + "` has " + entry.getValue().size() + " targets");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(null, attacker), response);
            }
        }

        for (DBKingdom attacker : allAttackers) {
            if (attacker.getActive_m() > 4880) {
                String response = ("Attacker: `" + attacker.getKingdom() + "` is inactive");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(null, attacker), response);
            } else if (attacker.getVm_turns() > 1) {
                String response = ("Attacker: `" + attacker.getKingdom() + "` is in VM for " + attacker.getVm_turns() + " turns");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(null, attacker), response);
            }
        }

        for (DBKingdom defender : allDefenders) {
            if (!isValidTarget.apply(defender)) {
                String response = ("Defender: `" + defender.getKingdom() + "` is not an enemy");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            } else if (defender.getActive_m() > TimeUnit.DAYS.toMinutes(8)) {
                String response = ("Defender: `" + defender.getKingdom() + "` is inactive");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            } else if (defender.getVm_turns() > 1) {
                String response = ("Defender: `" + defender.getKingdom() + "` is in VM for " + defender.getVm_turns() + " turns");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            } else if (defender.isBeige()) {
                String response = ("Defender: `" + defender.getKingdom() + "` is beige");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            }
        }


        return targets;
    }

    public void addAlliances(Set<Integer> allianceIds, boolean attacker) {
        Set<DBKingdom> nations = Locutus.imp().getKingdomDB().getKingdoms(allianceIds);
        addKingdoms(nations, attacker);
    }

    public Set<DBKingdom> getKingdoms(boolean attacker) {
        return attacker ? colA : colB;
    }

    public void addKingdoms(Collection<DBKingdom> nations, boolean attacker) {
        getKingdoms(attacker).addAll(nations);
    }

    public void removeInactive(int minutes) {
        colA.removeIf(n -> n.getActive_m() > minutes);
        colB.removeIf(n -> n.getActive_m() > minutes);
    }

    public void removeSlotted() {
        colB.removeIf(n -> n.getDef() >= 3 || n.isBeige() || n.getVm_turns() > 0);
    }

    BiFunction<Double, Double, Integer> attScores;
    BiFunction<Double, Double, Integer> defScores;

    BiFunction<Double, Double, Double> attPlanes;
    BiFunction<Double, Double, Double> defPlanes;

    Function<Double, Double> enemyPlaneRatio;

    private void init() {
        colA.removeIf(n -> n.hasUnsetMil());
        colB.removeIf(n -> n.hasUnsetMil());
        attScores = TrounceUtil.getIsKingdomsInScoreRange(colA);
        defScores = TrounceUtil.getIsKingdomsInScoreRange(colB);

        attPlanes = TrounceUtil.getXInRange(colA, n -> Math.pow(getAirStrength(n, true), AIR_FACTOR));
        defPlanes = TrounceUtil.getXInRange(colB, n -> Math.pow(getAirStrength(n, false), AIR_FACTOR));

        enemyPlaneRatio = new Function<Double, Double>() {
            @Override
            public Double apply(Double scoreAttacker) {
                double attAmt = attPlanes.apply(scoreAttacker * 0.75, scoreAttacker * 1.25);
                double defAmt = defPlanes.apply(scoreAttacker * 0.75, scoreAttacker * 1.25);
                return defAmt / attAmt;
            }
        };
        // remove defenders that can't counter

        Iterator<DBKingdom> defIter = colB.iterator();
        while (defIter.hasNext()) {
            DBKingdom defender = defIter.next();
            double minScore = defender.getScore() * 0.75;
            double maxScore = defender.getScore() / 0.75;
            if (attScores.apply(minScore, maxScore) <= 0) {
                defIter.remove();
            }
        }
    }

    public Map<DBKingdom, List<DBKingdom>> assignEasyTargets() {
        return assignEasyTargets(1.8, 1, 1);
    }

    public Map<DBKingdom, List<DBKingdom>> assignEasyTargets(double maxCityRatio, double maxGroundRatio, double maxAirRatio) {
        init();
//        int airCap = Buildings.HANGAR.perDay() * Buildings.HANGAR.cap(f -> false);
//        colA.removeIf(n -> n.getAircraft() < airCap * n.getCities() * 0.8);

        Map<DBKingdom, List<DBKingdom>> attPool = new HashMap<>(); // Pool of nations that could be used as targets
        Map<DBKingdom, List<DBKingdom>> defPool = new HashMap<>(); // Pool of nations that could be used as targets

        for (DBKingdom attacker : colA) {
            double attActive = activityFactor(attacker, true);
            if (attActive <= attActiveThreshold) {
                continue;
            }

            for (DBKingdom defender : colB) {
                if (defender.getCities() > attacker.getCities() * maxCityRatio) {
                    continue;
                }
                if (defender.getAircraft() > attacker.getAircraft() * maxAirRatio) {
                    continue;
                }
                if (defender.getGroundStrength(true, defender.getAircraft() > attacker.getAircraft() * 0.66) > attacker.getGroundStrength(true, false) * maxGroundRatio) {
                    continue;
                }

                double defActive = activityFactor(defender, false);

                if (defActive <= defActiveThreshold) {
                    continue;
                }

                double minScore = attacker.getScore() * 0.75;
                double maxScore = attacker.getScore() * 1.75;

//                if (enemyPlaneRatio.apply(defender.getScore()) > 1) {
//                    maxScore /= 0.75;
//                }

                if (defender.getScore() >= minScore && defender.getScore() <= maxScore) {
                    attPool.computeIfAbsent(attacker, f -> new ArrayList<>()).add(defender);
                    defPool.computeIfAbsent(defender, f -> new ArrayList<>()).add(attacker);
                }
            }
        }

        ArrayList<DBKingdom> colAList = new ArrayList<>(colA);
        Collections.sort(colAList, new Comparator<DBKingdom>() {
            @Override
            public int compare(DBKingdom o1, DBKingdom o2) {
                return Double.compare(o2.getStrength(), o1.getStrength());
            }
        });

        // The actual targets
        Map<DBKingdom, List<DBKingdom>> attTargets = new LinkedHashMap<>(); // Final assigned targets
        Map<DBKingdom, List<DBKingdom>> defTargets = new LinkedHashMap<>(); // Final assigned targets

        Set<DBWar> warsToRemove = new HashSet<>();

        if (parseExisting) {
            Set<Integer> natIds = new HashSet<>();
            for (DBKingdom nation : attPool.keySet()) natIds.add(nation.getKingdom_id());
            for (DBKingdom nation : defPool.keySet()) natIds.add(nation.getKingdom_id());
            Map<Integer, List<DBWar>> wars = Locutus.imp().getWarDb().getActiveWarsByAttacker(natIds, natIds, WarStatus.ACTIVE, WarStatus.DEFENDER_OFFERED_PEACE, WarStatus.ATTACKER_OFFERED_PEACE);
            for (Map.Entry<Integer, List<DBWar>> entry : wars.entrySet()) {
                for (DBWar war : entry.getValue()) {
                    DBKingdom attacker = Locutus.imp().getKingdomDB().getKingdom(war.attacker_id);
                    DBKingdom defender = Locutus.imp().getKingdomDB().getKingdom(war.defender_id);
                    if (attacker == null || defender == null) continue;
                    if (!war.isActive()) continue;

                    if (colA.contains(defender) || colB.contains(attacker)) {
                        DBKingdom tmp = defender;
                        defender = attacker;
                        attacker = tmp;
                    }
                    attTargets.computeIfAbsent(attacker, f -> new ArrayList<>()).add(defender);
                    defTargets.computeIfAbsent(defender, f -> new ArrayList<>()).add(attacker);

                    warsToRemove.add(war);
                }
                removeSlotted();
            }

            removeSlotted();
        }

        for (DBKingdom attacker : colAList) {
            List<DBKingdom> defenders = attPool.getOrDefault(attacker, Collections.emptyList());
            Collections.sort(defenders, new Comparator<DBKingdom>() {
                @Override
                public int compare(DBKingdom o1, DBKingdom o2) {
                    return Double.compare(o1.getStrength(), o2.getStrength());
                }
            });

            int maxAttacks = attacker.getNumWars();
            for (DBKingdom defender : defenders) {
                List<DBKingdom> existing = defTargets.getOrDefault(defender, Collections.emptyList());
                if (existing.size() >= 3) {
                    continue;
                }
                if (maxAttacks++ >= maxAttacksPerKingdom) break;
//                if (defender.getStrength() >= attacker.getStrength() * (maxGroundRatio + maxAirRatio)) continue;

                attTargets.computeIfAbsent(attacker, f -> new ArrayList<>()).add(defender);
                defTargets.computeIfAbsent(defender, f -> new ArrayList<>()).add(attacker);
            }
        }

        if (!warsToRemove.isEmpty()) {
            for (DBWar war : warsToRemove) {
                DBKingdom attacker = Locutus.imp().getKingdomDB().getKingdom(war.attacker_id);
                DBKingdom defender = Locutus.imp().getKingdomDB().getKingdom(war.defender_id);
                defTargets.getOrDefault(defender, Collections.emptyList()).remove(attacker);
                defTargets.getOrDefault(attacker, Collections.emptyList()).remove(defender);
            }
            defTargets.entrySet().removeIf(e -> e.getValue().isEmpty());
        }

        return defTargets;

    }

    public Map<DBKingdom, List<DBKingdom>> assignTargets() {
        init();

        Map<DBKingdom, List<DBKingdom>> attPool = new HashMap<>(); // Pool of nations that could be used as targets
        Map<DBKingdom, List<DBKingdom>> defPool = new HashMap<>(); // Pool of nations that could be used as targets

        for (DBKingdom attacker : colA) {
            double attActive = activityFactor(attacker, true);
            if (attActive <= attActiveThreshold) continue;

            int num = 0;
            for (DBKingdom defender : colB) {
                double defActive = activityFactor(defender, false);

                if (defActive <= defActiveThreshold) continue;

                double minScore = attacker.getScore() * 0.75;
                double maxScore = attacker.getScore() * 1.75;

//                if (enemyPlaneRatio.apply(defender.getScore()) > 1) {
//                    maxScore /= 0.75;
//                }

                if (defender.getScore() >= minScore && defender.getScore() <= maxScore) {
                    attPool.computeIfAbsent(attacker, f -> new ArrayList<>()).add(defender);
                    defPool.computeIfAbsent(defender, f -> new ArrayList<>()).add(attacker);
                }
            }
        }

        List<Map.Entry<DBKingdom, Double>> targetPriority = new ArrayList<>();

        for (Map.Entry<DBKingdom, List<DBKingdom>> entry : defPool.entrySet()) {
            DBKingdom defender = entry.getKey();
            List<DBKingdom> attackers = entry.getValue();
            if (attackers.size() == 0) {
                continue;
            }

            double magicStrength = getValue(defender, false, attackers);

            targetPriority.add(new AbstractMap.SimpleEntry<>(defender, magicStrength));
        }

        targetPriority.sort((o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));


        // The actual targets
        Map<DBKingdom, List<DBKingdom>> attTargets = new LinkedHashMap<>(); // Final assigned targets
        Map<DBKingdom, List<DBKingdom>> defTargets = new LinkedHashMap<>(); // Final assigned targets

        Set<DBWar> warsToRemove = new HashSet<>();

        if (parseExisting) {
            Set<Integer> natIds = new HashSet<>();
            for (DBKingdom nation : attPool.keySet()) natIds.add(nation.getKingdom_id());
            for (DBKingdom nation : defPool.keySet()) natIds.add(nation.getKingdom_id());
            Map<Integer, List<DBWar>> wars = Locutus.imp().getWarDb().getActiveWarsByAttacker(natIds, natIds, WarStatus.ACTIVE, WarStatus.DEFENDER_OFFERED_PEACE, WarStatus.ATTACKER_OFFERED_PEACE);
            for (Map.Entry<Integer, List<DBWar>> entry : wars.entrySet()) {
                for (DBWar war : entry.getValue()) {
                    DBKingdom attacker = Locutus.imp().getKingdomDB().getKingdom(war.attacker_id);
                    DBKingdom defender = Locutus.imp().getKingdomDB().getKingdom(war.defender_id);
                    if (attacker == null || defender == null) continue;
                    if (!war.isActive()) continue;

                    if (colA.contains(defender) || colB.contains(attacker)) {
                        DBKingdom tmp = defender;
                        defender = attacker;
                        attacker = tmp;
                    }
                    attTargets.computeIfAbsent(attacker, f -> new ArrayList<>()).add(defender);
                    defTargets.computeIfAbsent(defender, f -> new ArrayList<>()).add(attacker);

                    warsToRemove.add(war);
                }
                removeSlotted();
            }

            removeSlotted();
        }
//
//        // round 1, assign any
//        while (true) {
//            int assigned = 0;
//
//            outer:
//            for (Map.Entry<DBKingdom, Double> entry : targetPriority) {
//                DBKingdom defender = entry.getKey();
//                List<DBKingdom> existing = defTargets.computeIfAbsent(defender, f -> new ArrayList<>());
//
//                if (existing.size() > 3) {
//                    continue;
//                }
//
//                List<DBKingdom> attackers = defPool.get(defender);
//
//                if (attackers.isEmpty()) continue;
//
//                attackers = new ArrayList<>(attackers);
//                attackers.removeIf(n -> attTargets.containsKey(n));
//
//                if (attackers.isEmpty()) continue;
//
//                boolean sameAA = existing.size() <= 1 || existing.get(0).getAlliance_id() == existing.get(1).getAlliance_id();
//                if (existing.size() > 0 && sameAA) {
//                    int aaId = existing.get(0).getAlliance_id();
//                    for (int i = attackers.size() - 1; i >= 0; i--) {
//                        DBKingdom attacker = attackers.get(i);
//                        if (attacker.getAlliance_id() == aaId && getAirStrength(attacker, true) > getAirStrength(defender, true)) {
//                            assigned++;
//                            defPool.get(defender).remove(i);
//                            attTargets.computeIfAbsent(attacker, f -> new ArrayList<>()).add(defender);
//                            defTargets.computeIfAbsent(defender, f -> new ArrayList<>()).add(attacker);
//                            continue outer;
//                        }
//                    }
//                }
//
//                Map<Integer, Integer> aaAll = new HashMap<>();
//                Map<Integer, Long> aaAirAll = new HashMap<>();
//
//                double maxPlanes = 0;
//                Set<DBKingdom> maxPlanesKingdoms = new HashSet<>();
//                for (DBKingdom attacker : attackers) {
//
//                    if (getAirStrength(attacker, true) > maxPlanes) {
//                        maxPlanes = getAirStrength(attacker, true);
//                        maxPlanesKingdoms.clear();
//                    }
//                    if (getAirStrength(attacker, true) == maxPlanes) {
//                        maxPlanesKingdoms.add(attacker);
//                    }
//
//                    int id = attacker.getAlliance_id();
//
//                    aaAll.put(id, aaAll.getOrDefault(id, 0) + 1);
//
//                    aaAirAll.put(id, (long) (aaAirAll.getOrDefault(id, 0L) + Math.pow(getAirStrength(attacker, true), AIR_FACTOR)));
//                }
//
//
//                double max = 0;
//                DBKingdom attacker = null;
//                for (DBKingdom nation : maxPlanesKingdoms) {
//                    double numAlliesInRange = aaAirAll.get(nation.getAlliance_id());
//                    if (numAlliesInRange >= max) {
//                        max = numAlliesInRange;
//                        attacker = nation;
//                    }
//                }
//
//                if (attacker == null) {
//                    continue;
//                }
//                assigned++;
//
//                defPool.get(defender).remove(attacker);
//                attTargets.computeIfAbsent(attacker, f -> new ArrayList<>()).add(defender);
//                defTargets.computeIfAbsent(defender, f -> new ArrayList<>()).add(attacker);
//            }
//
//            if (assigned == 0) break;
//        }

        outer:
        while (true) {
            targetPriority.clear();
            HashMap<DBKingdom, Double> attackerPriorityCache = new HashMap<>();

            // TODO move this to a function to clean up the code
            // get the priority
            Function<DBKingdom, Double> attackerPriority = new Function<DBKingdom, Double>() {
                @Override
                public Double apply(DBKingdom attacker) {
                    if (attackerPriorityCache.containsKey(attacker)) {
                        return attackerPriorityCache.get(attacker);
                    }
                    if (attTargets.getOrDefault(attacker, Collections.emptyList()).size() >= maxAttacksPerKingdom) {
                        attackerPriorityCache.put(attacker, Double.NEGATIVE_INFINITY);
                    };
                    double attStr = getAirStrength(attacker, true);

                    List<DBKingdom> defenders = attPool.getOrDefault(attacker, Collections.emptyList());
                    // subtract 1/3 strength of defender
                    for (DBKingdom defender : defenders) {
                        // TODO calculate strength based on current military if not indefinite
                        // TODO use simulator to calculate strength
                        // AND OTHER PLACE WITH SAME FORMULA
                        double defStr = getAirStrength(defender, false);
                        defStr -= defStr * 0.25 * defTargets.getOrDefault(defender, Collections.emptyList()).size();
                        attStr -= defStr * 0.15;
                    }

                    attackerPriorityCache.put(attacker, attStr);
                    return attStr;
                }
            };

            for (Map.Entry<DBKingdom, List<DBKingdom>> entry : defPool.entrySet()) {
                DBKingdom defender = entry.getKey();
                List<DBKingdom> existing = defTargets.getOrDefault(defender, Collections.emptyList());

                if (existing.size() >= 3) {
                    continue;
                }

                List<DBKingdom> attackers = defPool.getOrDefault(defender, Collections.emptyList());

                if (attackers.size() == 0) continue;
                attackers.removeIf(n -> attTargets.getOrDefault(n, Collections.emptyList()).size() >= maxAttacksPerKingdom);
                if (attackers.size() == 0) continue;
                attackers.removeIf(n -> n.getAircraft() < defender.getAircraft() * 0.88);

                // TODO calculate strength based on current military if not indefinite
                // TODO use simulator to calculate strength
                // AND OTHER PLACE WITH SAME FORMULA
                double defStr = getValue(defender, false, attackers);
                defStr -= defStr * 0.25 * defTargets.getOrDefault(defender, Collections.emptyList()).size();

                int freeSlots = 3 - attackers.size();

                if (freeSlots >= attackers.size()) {
                    defStr *= 20 * (1 + freeSlots - attackers.size());
                }

                targetPriority.add(new AbstractMap.SimpleEntry<>(defender, defStr));
            }

            targetPriority.sort((o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));

            for (Map.Entry<DBKingdom, Double> entry : targetPriority) {
                DBKingdom defender = entry.getKey();

                List<DBKingdom> attackers = defPool.get(defender);

                if (attackers.isEmpty()) continue;

                List<DBKingdom> existing = defTargets.getOrDefault(defender, Collections.emptyList());

                double totalPlanes = -1;
                Map<Integer, Double> planesByAA = null;

                // TODO limit to same alliance
                double best = Double.NEGATIVE_INFINITY;
                DBKingdom bestAttacker = null;
                for (DBKingdom attacker : attackers) {
                    Double attPriority = attackerPriority.apply(attacker);
                    if (attPriority == null) continue;

                    if (sameAAPriority != 0) {
                        boolean sameAA = false;
                        if (!existing.isEmpty()) {
                            for (DBKingdom nation : existing) {
                                if (nation.getAlliance_id() == attacker.getAlliance_id()) {
                                    sameAA = true;
                                    break;
                                }
                            }
                            if (!sameAA) {
                                attPriority = attPriority * (1 - sameAAPriority);
                            }
                        } else {
                            if (totalPlanes == -1) {
                                planesByAA = new HashMap<>();
                                totalPlanes = 0;
                                for (DBKingdom other : attackers) {
                                    int id = other.getAlliance_id();
                                    double str = getAirStrength(other, true);
                                    totalPlanes += str;
                                    planesByAA.put(id, planesByAA.getOrDefault(id, 0d) + str);
                                }
                            }
                            Double aaPlanes = planesByAA.get(attacker.getAlliance_id());
                            attPriority = attPriority * (1 - sameAAPriority) + attPriority * (aaPlanes / totalPlanes) * (sameAAPriority);
                        }
                    }

                    if (activityMatchupPriority != 0) {
                        if (!existing.isEmpty()) {
                            double[] attActive = weeklyCache.apply(attacker).getByDayTurn().clone();
                            for (DBKingdom other : existing) {
                                double[] otherActive = weeklyCache.apply(other).getByDayTurn();
                                for (int i = 0; i < attActive.length; i++) {
                                    attActive[i] *= (otherActive[i] + 0.1);
                                }
                            }
                            double activityMatch = 0;
                            for (double v : attActive) activityMatch += v;

                            attPriority = attPriority * (1 - activityMatchupPriority) + attPriority * activityMatch * activityMatchupPriority;
                        }
                    }

                    if (attPriority > best) {
                        best = attPriority;
                        bestAttacker = attacker;
                    }
                }

                if (bestAttacker != null) {
                    attackers.remove(bestAttacker);
                    attTargets.computeIfAbsent(bestAttacker, f -> new ArrayList<>()).add(defender);
                    defTargets.computeIfAbsent(defender, f -> new ArrayList<>()).add(bestAttacker);
                    continue outer;
                }

            }

            break;
        }

        ArrayList<Map.Entry<DBKingdom, List<DBKingdom>>> attPoolSorted = new ArrayList<>(attPool.entrySet());
        Collections.sort(attPoolSorted, (o1, o2) -> Double.compare(getAirStrength(o2.getKey(), true), getAirStrength(o1.getKey(), true)));

        outer:
        for (Map.Entry<DBKingdom, List<DBKingdom>> entry : attPoolSorted) {
            DBKingdom attacker = entry.getKey();
            List<DBKingdom> targets = attTargets.getOrDefault(attacker, Collections.emptyList());

            if (!targets.isEmpty()) continue;

            List<DBKingdom> options = new ArrayList<>(entry.getValue());
            Collections.sort(options, (o1, o2) -> Double.compare(getAirStrength(o1, true), getAirStrength(o2, true)));

            double minGround = Double.MAX_VALUE;
            double minAir = Double.MAX_VALUE;
            DBKingdom minGroundKingdom = null;
            DBKingdom minAirKingdom = null;
            for (DBKingdom defender : options) {
                double airStr = getAirStrength(defender, false);
                double groundStr = getGroundStrength(defender, false);

                int numAttackers = Math.max(0, defTargets.getOrDefault(defender, Collections.emptyList()).size() - 3);

                if (numAttackers > 6) continue;

                if (airStr < minAir) {
                    minAir = airStr;
                    minAirKingdom = defender;
                }

                if (groundStr < minGround) {
                    minGround = groundStr;
                    minGroundKingdom = defender;
                }
            }

            if (minAirKingdom != null && getAirStrength(attacker, true) > minAir) {
                attTargets.computeIfAbsent(attacker, f -> new ArrayList<>()).add(minAirKingdom);
                defTargets.computeIfAbsent(minAirKingdom, f -> new ArrayList<>()).add(attacker);
                continue;
            } else if (minGroundKingdom != null && minGround < getGroundStrength(attacker, true)) {
                attTargets.computeIfAbsent(attacker, f -> new ArrayList<>()).add(minGroundKingdom);
                defTargets.computeIfAbsent(minGroundKingdom, f -> new ArrayList<>()).add(attacker);
                continue;
            }
        }


        if (!warsToRemove.isEmpty()) {
            for (DBWar war : warsToRemove) {
                DBKingdom attacker = Locutus.imp().getKingdomDB().getKingdom(war.attacker_id);
                DBKingdom defender = Locutus.imp().getKingdomDB().getKingdom(war.defender_id);
                defTargets.getOrDefault(defender, Collections.emptyList()).remove(attacker);
                defTargets.getOrDefault(attacker, Collections.emptyList()).remove(defender);
            }
            defTargets.entrySet().removeIf(e -> e.getValue().isEmpty());
        }


//
//        while (true) {
//            targetPriority.clear();
//            Map<DBKingdom, Double> attackerPriority = new HashMap<>();
//
//            for (Map.Entry<DBKingdom, List<DBKingdom>> entry : attPool.entrySet()) {
//
//            }
//        }
//
//        // assign attackers
//        for (Map.Entry<DBKingdom, List<DBKingdom>> entry : attPool.entrySet()) {
//            DBKingdom attacker = entry.getKey();
//            List<DBKingdom> targets = attTargets.get(attacker);
//            if (targets == null || targets.isEmpty()) continue;
//
//            targets = new ArrayList<>();
//            for (DBKingdom enemy : colB) {
//                // check if in score range
//                // check that they have 2 free defensive slots
//            }
//            // todo enemy with no ground or no ships
//        }


        return defTargets;
    }

    public static double getAirStrength(DBKingdom nation, boolean isAttacker, boolean countWars) {
        double str = getAirStrength(nation, isAttacker);
        str -= str * nation.getDef() * (1/3d);
        str -= str * nation.getOff() * (1/5d);
        return str;
    }

    public static double getBaseStrength(int cities) {
        int max = Buildings.HANGAR.cap(f -> false) * Buildings.HANGAR.max() * cities;
        return max / 2d;
    }

    public static double getAirStrength(DBKingdom nation, boolean isAttacker) {
        return getAirStrength(nation, nation.getAircraft(), nation.getTanks());
    }

    public static double getAirStrength(DBKingdom nation, MMRDouble mmrOverride) {
        double aircraft;
        double tanks;
        if (mmrOverride != null) {
            aircraft = (mmrOverride.get(MilitaryUnit.AIRCRAFT) / 5d) * Buildings.HANGAR.cap(f -> false) * Buildings.HANGAR.max() * nation.getCities();
            tanks = (mmrOverride.get(MilitaryUnit.TANK) / 5d) * Buildings.FACTORY.cap(f -> false) * Buildings.FACTORY.max() * nation.getCities();
        } else {
            aircraft = nation.getAircraft();
            tanks = nation.getTanks();
        }
        return getAirStrength(nation, aircraft, tanks);
    }

    public static double getAirStrength(DBKingdom nation, double aircraft, double tanks) {
        int max = Buildings.HANGAR.cap(f -> false) * Buildings.HANGAR.max() * nation.getCities();
        double str = aircraft + max / 2d;
        str += tanks / 32d;

        return str;
    }

    public double getGroundStrength(DBKingdom nation, boolean isAttacker) {
        int max = Buildings.BARRACKS.cap(nation::hasProject) * Buildings.BARRACKS.max() * nation.getCities();
        double str = nation.getSoldiers() + max / 2d;
        str -= nation.getTanks() * 20;
        return str;
    }

    public double getValue(DBKingdom defender, boolean isAttacker, List<DBKingdom> attackers) {
        int airRebuyPerCity = Buildings.HANGAR.cap(defender::hasProject) * Buildings.HANGAR.perDay();
        int maxAirPerCity = Buildings.HANGAR.cap(defender::hasProject) * Buildings.HANGAR.max();

//        double scoreRatio = enemyPlaneRatio.apply(defender.getScore());
//        double activity = activityFactor(defender, false);

        double avgCity = 0;
        int avgAir = 0;
        if (attackers.size() > 0) {
            for (DBKingdom attacker : attackers) {
                int rebuy = airRebuyPerCity * attacker.getCities();

                // TODO change to sim instead of air strength
                avgAir += getAirStrength(attacker, true);
                avgCity += attacker.getCities();
            }
            avgAir /= attackers.size();
            avgCity /= attackers.size();
        }

        int airRebuy = airRebuyPerCity * defender.getCities();
        double airRatio = Math.min(2, getAirStrength(defender, false) / (1d + avgAir)) / 2d;

//        double magicStrength = airRatio + scoreRatio * 0.25;
//        return magicStrength;
        return defender.getStrength();
    }

    private Map<DBKingdom, Double> activityFactorAtt = new HashMap<>();
    private Map<DBKingdom, Double> activityFactorDef = new HashMap<>();

    public double activityFactor(DBKingdom nation, boolean isAttacker) {
        if ((!isAttacker && nation.getPosition() > 2) || nation.getOff() > 0) return 1;

        Map<DBKingdom, Double> cache = isAttacker ? activityFactorAtt : activityFactorDef;
        Double cacheValue = cache.get(nation);
        if (cacheValue != null) return cacheValue;

        GuildDB db = Locutus.imp().getGuildDBByAA(nation.getAlliance_id());
        if (db != null) {
            guilds.add(db.getGuild());
        }

        User user = nation.getUser();
        if (user != null) {
            for (Guild guild : guilds) {
                if (Roles.BLITZ_PARTICIPANT.has(user, guild)) {
                    return 1;
                }

                if (Roles.BLITZ_PARTICIPANT_OPT_OUT.has(user, guild)) {
                    return 0;
                }
            }
        }

        Activity activity = weeklyCache.apply(nation);

        double min = 0;
        if (indefinate) {
            if (!isAttacker) {
                // Use average for defender
                for (double v : activity.getByDay()) {
                    min += v;
                }
                min /= 7;
            } else {
                // Use min for attacker
                min = 1;
                for (double v : activity.getByDay()) {
                    min = Math.min(v, min);
                }
            }
        } else {
            if (isAttacker) {
                min = activity.loginChance(turn, 1, true);
            } else {
                min = activity.loginChance(24, true);
                if (min < 0.8 && nation.getOff() > 0) {
                    min = 0.2 * min + 0.8;
                }
            }
        }
        cache.put(nation, min);
        return min;
    }


}
