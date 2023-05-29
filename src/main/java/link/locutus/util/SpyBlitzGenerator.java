package link.locutus.util;

import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.spells.SpellOp;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.util.builder.SummedMapRankBuilder;
import link.locutus.util.spreadsheet.SpreadSheet;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SpyBlitzGenerator {

    private final Map<DBKingdom, Double> attList;
    private final Map<DBKingdom, Double> defList;
    private final Set<AttackOrSpellType> allowedTypes;
    private final int maxDefAttacks;
    private final int maxDefSpells;
    private final boolean skipCheckAlert;
    private final boolean skipCheckSpellAlert;
    private final boolean unitDamage;
    private final boolean landDamage;
    private final boolean useNet;

    private Map<Integer, Double> allianceWeighting = new HashMap<>();

    public static void generateSpySheet(SpreadSheet sheet, Map<DBKingdom, List<SpellOp>> opsAgainstKingdoms) {
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "score",
                "attack",
                "defense",
                "active_m",
                "att1",
                "att2",
                "att3"
        ));

        sheet.setHeader(header);

        boolean multipleAAs = false;
        DBKingdom prevAttacker = null;
        for (List<SpellOp> spyOpList : opsAgainstKingdoms.values()) {
            for (SpellOp spyop : spyOpList) {
                DBKingdom attacker = spyop.getAttacker();
                if (prevAttacker != null && prevAttacker.getAlliance_id() != attacker.getAlliance_id()) {
                    multipleAAs = true;
                }
                prevAttacker = attacker;
            }
        }

        for (Map.Entry<DBKingdom, List<SpellOp>> entry : opsAgainstKingdoms.entrySet()) {
            DBKingdom nation = entry.getKey();

            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(nation.getName(), TrounceUtil.getUrl(nation.getId(), false)));
            row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), TrounceUtil.getUrl(nation.getAlliance_id(), true)));
            row.add(nation.getScore());
            row.add("" + nation.getAttackStrength());
            row.add("" + nation.getDefenseStrength());
            row.add("" + nation.getActive_m());

            for (SpellOp spyop : entry.getValue()) {
                DBKingdom attacker = spyop.getAttacker();
                String attStr = MarkupUtil.sheetUrl(attacker.getName(), TrounceUtil.getUrl(attacker.getId(), false));

                if (multipleAAs) {
                    attStr += "& \"|" + spyop.getType().name() + "|" + attacker.getAllianceName();
                } else {
                    attStr += "& \"|" + spyop.getType().name();
                }
                if (spyop.isMaxUnit()) {
                    attStr += " |max";
                } else if (spyop.getType().isAttackAlert()) {
                    attStr += " |min";
                }
                attStr += "\"";

                row.add(attStr);
            }

            sheet.addRow(row);
        }
    }

    public SpyBlitzGenerator setAllianceWeighting(DBAlliance alliance, double weight) {
        allianceWeighting.put(alliance.getAlliance_id(), weight);
        return this;
    }

    public SpyBlitzGenerator(Set<DBKingdom> attackers, Set<DBKingdom> defenders, Set<AttackOrSpellType> allowedTypes, int maxDefSpells, int maxDefAttacks, boolean skipCheckAlert, boolean skipCheckSpellAlert, boolean unitDamage, boolean landDamage, boolean net) {
        Set<Integer> attRealms = attackers.stream().map(DBKingdom::getRealm_id).collect(Collectors.toSet());
        Set<Integer> defRealms = defenders.stream().map(DBKingdom::getRealm_id).collect(Collectors.toSet());
        if (attRealms.size() > 1) {
            throw new IllegalArgumentException("Attackers must be from the same realm: " + attRealms);
        }
        if (defRealms.size() > 1) {
            throw new IllegalArgumentException("Defenders must be from the same realm: " + defRealms);
        }

        this.allowedTypes = allowedTypes;
        this.maxDefSpells = maxDefSpells;
        this.maxDefAttacks = maxDefAttacks;

        this.skipCheckAlert = skipCheckAlert;
        this.skipCheckSpellAlert = skipCheckSpellAlert;
        this.unitDamage = unitDamage;
        this.landDamage = landDamage;
        this.useNet = net;


        this.attList = sortKingdoms(attackers, true);
        this.defList = sortKingdoms(defenders, false);
    }

    public Map<DBKingdom, List<SpellOp>> assignTargets(Function<DBKingdom, Map<MilitaryUnit, Long>> getBpAndMana, Predicate<AttackOrSpellType> allowWeaker, boolean unitDamage, boolean landDamage, boolean net, boolean skipNegativeNet) {
        BiFunction<Double, Double, Integer> attRange = TrounceUtil.getIsKingdomsInSpyRange(attList.keySet());
        BiFunction<Double, Double, Integer> defSpyRange = TrounceUtil.getIsKingdomsInSpyRange(defList.keySet());

        BiFunction<Double, Double, Integer> attScoreRange = TrounceUtil.getIsKingdomsInScoreRange(attList.keySet());
        BiFunction<Double, Double, Integer> defScoreRange = TrounceUtil.getIsKingdomsInScoreRange(defList.keySet());

        defList.entrySet().removeIf(n -> attScoreRange.apply(n.getKey().getScore() * 0.5, n.getKey().getScore() * 1.5) == 0);

        BiFunction<Double, Double, Double> attSpyGraph = TrounceUtil.getXInRange(attList.keySet(), n -> Math.pow(attList.get(n), 3));
        BiFunction<Double, Double, Double> defSpyGraph = TrounceUtil.getXInRange(defList.keySet(), n -> Math.pow(defList.get(n), 3));

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
                boolean useMax = myStrength > defStrength;
                int useStrength = useMax ? myStrength : (int) (myStrength * 0.2);

                AttackOrSpellType bestSpell = null;
                double maxDamage = Double.MIN_VALUE;
                for (AttackOrSpellType operation : allowedOpTypes) {
                    if (weaker && !allowWeaker.test(operation)) continue;
                    double damage = operation.getDefaultDamage(attacker, defender, useStrength, defStrength, unitDamage, landDamage, net);
                    double netDamage = damage;
                    if (!net) {
                        netDamage = operation.getDefaultDamage(attacker, defender, useStrength, defStrength, unitDamage, landDamage, true);
                    }
                    if (skipNegativeNet && netDamage < 0) {
                        continue;
                    }
                    if (damage > maxDamage) {
                        maxDamage = damage;
                        bestSpell = operation;
                    }
                }
                if (bestSpell != null) {
                    ops.add(new SpellOp(bestSpell, attacker, defender, useMax && bestSpell.isAttackAlert(), maxDamage));
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

            Map<MilitaryUnit, Long> bpMana = getBpAndMana.apply(op.getAttacker());
            int bp = bpMana.getOrDefault(MilitaryUnit.BATTLE_POINTS, 0L).intValue();
            int mana = bpMana.getOrDefault(MilitaryUnit.MANA, 0L).intValue();
            // subtract bp/mana
            for (SpellOp attOp : attOps) {
                bp -= attOp.getType().getBattlePoints();
                mana -= attOp.getType().getMana();
            }

            if (bp < op.getType().getBattlePoints()) continue;
            if (mana < op.getType().getMana()) continue;

            List<SpellOp> defOps = opsAgainstKingdoms.computeIfAbsent(op.getTarget(), f -> new ArrayList<>());
            int remainingDefSpells = maxDefSpells;
            int remainingDefAttacks = maxDefAttacks;
            for (SpellOp defOp : defOps) {
                if (defOp.getType().isAttackAlert()) {
                    remainingDefAttacks--;
                }
                if (defOp.getType().isSpellAlert()) {
                    remainingDefSpells--;
                }
            }

            if (remainingDefAttacks <= 0 && op.getType().isAttackAlert()) continue;
            if (remainingDefSpells <= 0 && op.getType().isSpellAlert()) continue;


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
        if (!isAttacker) {
            if (!skipCheckAlert) {
                list.removeIf(f -> f.getAlert_level() > 0);
            }
            if (!skipCheckSpellAlert) {
                list.removeIf(f -> f.getSpell_alert() > 0);
            }
        }
        Map<DBKingdom, Double> spyValueMap = new LinkedHashMap<>();
        for (DBKingdom nation : list) {
            double perSpyValue = estimateValue(nation, isAttacker);

            spyValueMap.put(nation, perSpyValue);
        }

        return new SummedMapRankBuilder<>(spyValueMap).sort().get();
    }

    public static Map<DBKingdom, Set<SpellOp>> getTargets(SpreadSheet sheet, int headerRow, BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String> invalidOut) {
        List<List<Object>> rows = sheet.getAll();
        List<Object> header = rows.get(headerRow);

        Integer targetI = null;
        Integer attI = null;
        Integer allianceI = null;
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
            } else if (title.equalsIgnoreCase("alliance") && allianceI == null) {
                allianceI = i;
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
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), ("`" + cell.toString() + "` is an invalid nation\n"));
                continue;
            }

            if (allianceI != null && rows.size() > allianceI) {
                Object aaCell = row.get(allianceI);
                if (aaCell != null) {
                    String allianceStr = aaCell.toString();
                    DBAlliance alliance = DBAlliance.parse(allianceStr);
                    if (alliance != null && nation.getAlliance_id() != alliance.getAlliance_id()) {
                        String response = ("Kingdom: `" + nationStr + "` is no longer in alliance: `" + allianceStr + "`\n");
                        invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
                    }
                }
            }

            if (attI != null) {
                for (int j = attI; j < row.size(); j++) {
                    cell = row.get(j);
                    if (cell == null || cell.toString().isEmpty()) continue;

                    String cellStr = cell.toString();
                    String[] split = cellStr.split("\\|");

                    DBKingdom other = DBKingdom.parse(split[0]);
                    if (other == null) {
                        String response = ("`" + cell.toString() + "` is an invalid nation\n");
                        invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
                        continue;
                    }
                    if (isReverse) {
                        defender = other;
                    } else {
                        attacker = other;
                    }
                    AttackOrSpellType opType = AttackOrSpellType.valueOf(split[1]);
                    boolean maxUnits = split.length >= 3 && split[2].toLowerCase().contains("max");
                    SpellOp op = new SpellOp(opType, attacker, defender, maxUnits, 0);
                    targets.computeIfAbsent(attacker, f -> new LinkedHashSet<>()).add(op);
                }
            } else {
                throw new IllegalArgumentException("No targets found");
            }
        }
        return targets;
    }

    public static void validateTargets(Map<DBKingdom, Set<SpellOp>> targets,
                                                            Function<DBKingdom, Map<MilitaryUnit, Long>> getBpMana,
                                                            double minScoreMultiplier, double maxScoreMultiplier,
                                                            boolean checkUpdeclare,
                                                            boolean checkAttackSlots,
                                                            boolean checkSpellSlots,
                                                            int maxDefAttacks,
                                                            int maxDefSpells,
                                                            Function<DBKingdom, Boolean> isValidTarget,
                                                            BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String> invalidOut) {

        Set<DBKingdom> allAttackers = new HashSet<>();
        Set<DBKingdom> allDefenders = new HashSet<>();

        Map<DBKingdom, Set<SpellOp>> opsByAttacker = new LinkedHashMap<>();
        Map<DBKingdom, Set<SpellOp>> opsByDefender = new LinkedHashMap<>();

        List<SpellOp> allOps = new ArrayList<>();
        for (Map.Entry<DBKingdom, Set<SpellOp>> entry : targets.entrySet()) {
            for (SpellOp op : entry.getValue()) {
                allAttackers.add(op.getAttacker());
                allDefenders.add(op.getTarget());

                opsByAttacker.computeIfAbsent(op.getAttacker(), f -> new LinkedHashSet<>()).add(op);
                opsByDefender.computeIfAbsent(op.getTarget(), f -> new LinkedHashSet<>()).add(op);
            }
            allOps.addAll(entry.getValue());
        }

        for (SpellOp op : allOps) {
            process(op.getAttacker(),
                    opsByAttacker.get(op.getAttacker()),
                    op.getTarget(),
                    opsByDefender.get(op.getTarget()),
                    getBpMana,
                    minScoreMultiplier,
                    maxScoreMultiplier,
                    checkUpdeclare,
                    checkAttackSlots,
                    checkSpellSlots,
                    maxDefAttacks,
                    maxDefSpells,
                    invalidOut
            );
        }



        for (DBKingdom attacker : allAttackers) {
            if (attacker.getActive_m() > 4880) {
                String response = ("Attacker: `" + attacker.getName() + "` is inactive");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(null, attacker), response);
            } else if (attacker.isVacation()) {
                String response = ("Attacker: `" + attacker.getName() + "` is in VM " + attacker.isVacation());
                invalidOut.accept(new AbstractMap.SimpleEntry<>(null, attacker), response);
            }
        }

        for (DBKingdom defender : allDefenders) {
            if (!isValidTarget.apply(defender)) {
                String response = ("Defender: `" + defender.getName() + "` is not an enemy");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            } else if (defender.getActive_m() > TimeUnit.DAYS.toMinutes(8)) {
                String response = ("Defender: `" + defender.getName() + "` is inactive");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            } else if (defender.isVacation()) {
                String response = ("Defender: `" + defender.getName() + "` is in VM " + defender.isVacation() + "");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            }
        }
    }

    private static void process(DBKingdom attacker,
                                Set<SpellOp> attOps,
                                DBKingdom defender,
                                Set<SpellOp> defOps,
                                Function<DBKingdom, Map<MilitaryUnit, Long>> getBpAndMana,
                                double minScoreMultiplier,
                                double maxScoreMultiplier,
                                boolean checkUpdeclare,
                                boolean checkAttackSlots,
                                boolean checkSpellSlots,
                                int maxDefAttacks,
                                int maxDefSpells,
                                BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String> invalidOut) {
        double minScore = attacker.getScore() * minScoreMultiplier;
        double maxScore = attacker.getScore() * maxScoreMultiplier;



        if (defender.getScore() < minScore) {
            double diff = Math.round((minScore - defender.getScore()) * 100) / 100d;
            String response = ("`" + defender.getName() + "` is " + MathMan.format(diff) + "ns below " + "`" + attacker.getName() + "`");
            invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
            return;
        }
        if (defender.getScore() > maxScore) {
            double diff = Math.round((defender.getScore() - maxScore) * 100) / 100d;
            String response = ("`" + defender.getName() + "` is " + MathMan.format(diff) + "ns above " + "`" + attacker.getName() + "`");
            invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
        }

        if (checkUpdeclare) {
            int attStr = attacker.getAttackStrength();
            int defStr = defender.getDefenseStrength();
            if (defStr > attStr) {
                double ratio = (double) defStr / attStr;
                String response = ("`" + defender.getName() + "` is " + MathMan.format(ratio) + "x stronger than " + "`" + attacker.getName() + "`");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, attacker), response);
                return;
            }
        }

        {
            Map<MilitaryUnit, Long> bpMana = getBpAndMana.apply(attacker);
            int bp = bpMana.getOrDefault(MilitaryUnit.BATTLE_POINTS, 0L).intValue();
            int mana = bpMana.getOrDefault(MilitaryUnit.MANA, 0L).intValue();
            int originalBp = bp;
            int originalMana = mana;
            // subtract bp/mana
            for (SpellOp attOp : attOps) {
                bp -= attOp.getType().getBattlePoints();
                mana -= attOp.getType().getMana();
            }

            if (bp < 0) {
                String response = ("Attacker: `" + attacker.getName() + "` is assigned more ops than they have BP for (" + originalBp + " -> " + bp + ")");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(null, attacker), response);
                return;
            }
            if (mana < 0) {
                String response = ("Attacker: `" + attacker.getName() + "` is assigned more ops than they have Mana for (" + originalMana + " -> " + mana + ")");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(null, attacker), response);
                return;
            }

            int remainingDefSpells = maxDefSpells;
            int remainingDefAttacks = maxDefAttacks;
            for (SpellOp defOp : defOps) {
                if (defOp.getType().isAttackAlert()) {
                    remainingDefAttacks--;
                }
                if (defOp.getType().isSpellAlert()) {
                    remainingDefSpells--;
                }
            }

            if (remainingDefAttacks < 0 && checkAttackSlots) {
                String response = ("Defender: `" + defender.getName() + "` has more than " + maxDefAttacks + " war ops assigned");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
                return;
            }
            if (remainingDefSpells < 0 && checkSpellSlots) {
                String response = ("Defender: `" + defender.getName() + "` has more than " + maxDefSpells + " spell ops assigned");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
                return;
            }

            if (checkAttackSlots && defender.getAlert_level() > 0 && remainingDefAttacks < maxDefAttacks) {
                String response = ("Defender: `" + defender.getName() + "` is alert " + defender.getAlert_level() + "");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            }
            if (checkSpellSlots && defender.getSpell_alert() > 0 && remainingDefSpells < maxDefSpells) {
                String response = ("Defender: `" + defender.getName() + "` is spell alerted " + defender.getSpell_alert() + "");
                invalidOut.accept(new AbstractMap.SimpleEntry<>(defender, null), response);
            }
        }
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
}
