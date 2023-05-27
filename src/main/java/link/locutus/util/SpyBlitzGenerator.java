package link.locutus.util;

import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.SimpleKingdomList;
import link.locutus.core.db.entities.spells.SpellOp;
import link.locutus.util.builder.SummedMapRankBuilder;
import link.locutus.util.spreadsheet.SpreadSheet;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SpyBlitzGenerator {

    private final Map<DBKingdom, Double> attList;
    private final Map<DBKingdom, Double> defList;
    private final Set<AttackOrSpellType> allowedTypes;
    private final int maxDef;
    private final boolean checkEspionageSlots;
    private final Integer minRequiredSpies;
    private final boolean prioritizeKills;
    private boolean forceUpdate;

    private Map<Integer, Double> allianceWeighting = new HashMap<>();

    public SpyBlitzGenerator setAllianceWeighting(DBAlliance alliance, double weight) {
        allianceWeighting.put(alliance.getAlliance_id(), weight);
        return this;
    }

    public SpyBlitzGenerator(Set<DBKingdom> attackers, Set<DBKingdom> defenders, Set<AttackOrSpellType> allowedTypes, boolean forceUpdate, int maxDef, boolean checkEspionageSlots, Integer minRequiredSpies, boolean prioritizeKills) {
        Set<Integer> attRealms = attackers.stream().map(DBKingdom::getRealm_id).collect(Collectors.toSet());
        Set<Integer> defRealms = defenders.stream().map(DBKingdom::getRealm_id).collect(Collectors.toSet());
        if (attRealms.size() > 1) {
            throw new IllegalArgumentException("Attackers must be from the same realm: " + attRealms);
        }
        if (defRealms.size() > 1) {
            throw new IllegalArgumentException("Defenders must be from the same realm: " + defRealms);
        }

        this.allowedTypes = allowedTypes;
        this.maxDef = maxDef;
        this.checkEspionageSlots = checkEspionageSlots;
        this.minRequiredSpies = minRequiredSpies;
        this.prioritizeKills = prioritizeKills;
        this.forceUpdate = forceUpdate;

        this.attList = sortKingdoms(attackers, true);
        this.defList = sortKingdoms(defenders, false);
    }

    public Map<DBKingdom, List<SpellOp>> assignTargets(BiFunction<DBKingdom, List<SpellOp>, Integer> getNumOps, Predicate<AttackOrSpellType> allowWeaker, boolean unitDamage, boolean landDamage, boolean net) {
        BiFunction<Double, Double, Integer> attRange = TrounceUtil.getIsKingdomsInSpyRange(attList.keySet());
        BiFunction<Double, Double, Integer> defSpyRange = TrounceUtil.getIsKingdomsInSpyRange(defList.keySet());

        BiFunction<Double, Double, Integer> attScoreRange = TrounceUtil.getIsKingdomsInScoreRange(attList.keySet());
        BiFunction<Double, Double, Integer> defScoreRange = TrounceUtil.getIsKingdomsInScoreRange(defList.keySet());

        defList.entrySet().removeIf(n -> attScoreRange.apply(n.getKey().getScore() * 0.75, n.getKey().getScore() / 0.75) == 0);

        BiFunction<Double, Double, Double> attSpyGraph = TrounceUtil.getXInRange(attList.keySet(), n -> Math.pow(attList.get(n), 3));
        BiFunction<Double, Double, Double> defSpyGraph = TrounceUtil.getXInRange(defList.keySet(), n -> Math.pow(defList.get(n), 3));

        if (forceUpdate) {
            forceUpdate = false;
            List<DBKingdom> allKingdoms = new ArrayList<>();
            allKingdoms.addAll(attList.keySet());
            allKingdoms.addAll(defList.keySet());
        }

        List<SpellOp> ops = new ArrayList<>();

        Set<AttackOrSpellType> allowedOpTypes = new HashSet<>(allowedTypes);

        for (Map.Entry<DBKingdom, Double> entry : attList.entrySet()) {
            DBKingdom attacker = entry.getKey();
            int myStrength = attacker.getAttackStrength();

            for (Map.Entry<DBKingdom, Double> entry2 : defList.entrySet()) {
                DBKingdom defender = entry2.getKey();
                if (!attacker.isInSpyRange(defender)) continue;
                int defStrength = defender.getDefenseStrength();
                boolean weaker = myStrength < defStrength;

                AttackOrSpellType bestSpell = null;
                double maxDamage = 0;
                for (AttackOrSpellType operation : allowedOpTypes) {
                    if (weaker && !allowWeaker.test(operation)) continue;
                    double damage = operation.getDefaultDamage(attacker, defender, myStrength, defStrength, unitDamage, landDamage, net);
                    if (damage > maxDamage) {
                        maxDamage = damage;
                        bestSpell = operation;
                    }
                }
                if (bestSpell != null) {
                    ops.add(new SpellOp(bestSpell, attacker, defender, maxDamage));
                }
            }
        }

        Collections.sort(ops, new Comparator<SpellOp>() {
            @Override
            public int compare(SpellOp o1, SpellOp o2) {
                return Double.compare(o2.getDamage(), o1.getDamage());
            }
        });

        Map<DBKingdom, List<SpellOp>> opsAgainstKingdoms = new LinkedHashMap<>();
        Map<DBKingdom, List<SpellOp>> opsByKingdoms = new LinkedHashMap<>();

        for (SpellOp op : ops) {
            List<SpellOp> attOps = opsByKingdoms.computeIfAbsent(op.getAttacker(), f -> new ArrayList<>());
            if (attOps.size() >= getNumOps.apply(op.getAttacker(), attOps)) {
                continue;
            }
            List<SpellOp> defOps = opsAgainstKingdoms.computeIfAbsent(op.getTarget(), f -> new ArrayList<>());
            if (defOps.size() >= maxDef) {
                continue;
            }
            defOps.add(op);
            attOps.add(op);
        }

        return opsAgainstKingdoms;
    }

    public static double estimateValue(DBKingdom nation, boolean isAttacker) {
        if (isAttacker) {
            return nation.getAttackStrength();
        }

        return nation.getDefenseStrength() / (1 + nation.getTurnsInactive());
    }

    private Map<DBKingdom, Double> sortKingdoms(Collection<DBKingdom> nations, boolean isAttacker) {
        List<DBKingdom> list = new ArrayList<>(nations);

        list.removeIf(f -> f.getActive_m() > 1440);
        list.removeIf(f -> f.isVacation());
        list.removeIf(f -> f.getPosition().ordinal() <= Rank.APPLICANT.ordinal());
        if (checkEspionageSlots && !isAttacker) {
            list.removeIf(f -> f.getSpell_alert() != 0);
        }
        Map<DBKingdom, Double> spyValueMap = new LinkedHashMap<>();
        for (DBKingdom nation : list) {
            double perSpyValue = estimateValue(nation, isAttacker);

            spyValueMap.put(nation, perSpyValue);
        }

        return new SummedMapRankBuilder<>(spyValueMap).sort().get();
    }

    public static Map<DBKingdom, Set<SpellOp>> getTargets(SpreadSheet sheet, int headerRow) {
        return getTargets(sheet, headerRow, true);
    }

    public static Map<DBKingdom, Set<SpellOp>> getTargets(SpreadSheet sheet, int headerRow, boolean groupByAttacker) {
        List<List<Object>> rows = sheet.getAll();
        List<Object> header = rows.get(headerRow);

        Integer targetI = null;
        Integer attI = null;
        List<Integer> targetsIndexesRoseFormat = new ArrayList<>();

        boolean isReverse = false;
        for (int i = 0; i < header.size(); i++) {
            Object obj = header.get(i);
            if (obj == null) continue;
            String title = obj.toString();
            if (title.equalsIgnoreCase("nation")) {
                targetI = i;
            }
            if (title.equalsIgnoreCase("att1")) {
                attI = i;
            }
            else if (title.equalsIgnoreCase("def1")) {
                attI = i;
                isReverse = true;
            } else if (title.toLowerCase().startsWith("spy slot ")) {
                targetsIndexesRoseFormat.add(i);
                targetI = 0;
            }
        }

        Map<DBKingdom, Set<SpellOp>> targets = new LinkedHashMap<>();

        for (int i = headerRow + 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.size() <= targetI) {
                continue;
            }

            Object cell = row.get(targetI);
            if (cell == null || cell.toString().isEmpty()) {
                continue;
            }

            String nationStr = cell.toString();
            if (nationStr.contains(" / ")) nationStr = nationStr.split(" / ")[0];
            DBKingdom nation = DBKingdom.parse(nationStr);

            DBKingdom attacker = isReverse ? nation : null;
            DBKingdom defender = !isReverse ? nation : null;

            if (nation == null) {
                continue;
            }

            if (attI != null) {
                for (int j = attI; j < row.size(); j++) {
                    cell = row.get(j);
                    if (cell == null || cell.toString().isEmpty()) continue;

                    String cellStr = cell.toString();
                    String[] split = cellStr.split("\\|");

                    DBKingdom other = DBKingdom.parse(split[0]);
                    if (other == null) continue;
                    if (isReverse) {
                        defender = other;
                    } else {
                        attacker = other;
                    }
                    AttackOrSpellType opType = AttackOrSpellType.valueOf(split[1]);
                    SpellOp op = new SpellOp(opType, attacker, defender, 0);
                    targets.computeIfAbsent(attacker, f -> new LinkedHashSet<>()).add(op);
                }
            } else {
                throw new IllegalArgumentException("No targets found");
            }
        }
        return targets;
    }
}
