package link.locutus.core.db.entities;

import link.locutus.Trocutus;
import link.locutus.core.api.game.AttackOrSpell;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomOrAlliance;
import link.locutus.util.ArrayUtil;
import link.locutus.util.TrounceUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WarParser {
    private final Collection<AttackOrSpell> attackOrSpells;
    private final Predicate<AttackOrSpell> isCol1;
    private final Predicate<AttackOrSpell> isCol2;
    private final String col1, col2;

    public static WarParser of(String col1, String col2, Collection<KingdomOrAlliance> attackers, Collection<KingdomOrAlliance> defenders, long start, long end) {
        List<AttackOrSpell> spells = Trocutus.imp().getDB().getAttackOrSpells(attackers, defenders, start, end);

        Set<Integer> col1KingdomIds = attackers == null ? Collections.emptySet() : attackers.stream().filter(f -> f.isKingdom()).map(KingdomOrAlliance::getId).collect(Collectors.toSet());
        Set<Integer> col1AllianceIds = attackers == null ? Collections.emptySet() : attackers.stream().filter(f -> f.isAlliance()).map(KingdomOrAlliance::getId).collect(Collectors.toSet());
        Set<Integer> col2KingdomIds = defenders == null ? Collections.emptySet() : defenders.stream().filter(f -> f.isKingdom()).map(KingdomOrAlliance::getId).collect(Collectors.toSet());
        Set<Integer> col2AllianceIds = defenders == null ? Collections.emptySet() : defenders.stream().filter(f -> f.isAlliance()).map(KingdomOrAlliance::getId).collect(Collectors.toSet());

        Predicate<AttackOrSpell> isCol1 = f -> attackers == null || (col1KingdomIds.contains(f.getAttacker_id()) || col1AllianceIds.contains(f.getAttacker_aa()));
        Predicate<AttackOrSpell> isCol2 = f -> defenders == null || (col2KingdomIds.contains(f.getAttacker_id()) || col2AllianceIds.contains(f.getAttacker_aa()));

        return new WarParser(col1, col2, spells, isCol1, isCol2);
    }

    public WarParser(String col1, String col2, Collection<AttackOrSpell> attackOrSpells, Predicate<AttackOrSpell> isCol1, Predicate<AttackOrSpell> isCol2) {
        this.attackOrSpells = attackOrSpells;
        this.col1 = col1;
        this.col2 = col2;
        this.isCol1 = isCol1;
        this.isCol2 = isCol2;
    }

    public WarParser allowedWarTypes(Set<AttackOrSpellType> allowedWarTypes) {
        if (allowedWarTypes != null) {
            attackOrSpells.removeIf(f -> !allowedWarTypes.contains(f.getType()));
        }
        return this;
    }

    public WarParser toWarCost() {
        return this;
    }

    public int getCount() {
        return attackOrSpells.size();
    }

    public List<Integer> getIds() {
        return attackOrSpells.stream().map(AttackOrSpell::getId).toList();
    }

    public int getCount(boolean isCol1Flag) {
        if (isCol1Flag) {
            return (int) attackOrSpells.stream().filter(isCol1).count();
        } else {
            return (int) attackOrSpells.stream().filter(f -> isCol2.test(f) && !isCol1.test(f)).count();
        }
    }

    public String toCostString() {
        int att1Count = getCount(true);
        Map<MilitaryUnit, Long> att1Total = getTotalCost(true);

        StringBuilder result = new StringBuilder();
        result.append("## **" + col1 + " Cost** (cast: " + att1Count + " spells/attacks)\n");
        result.append("**Raw cost:**\n");
        result.append("```" + TrounceUtil.resourcesToString(att1Total) + "```\n");
        result.append("**Converted cost:**\n");
        result.append("```" + TrounceUtil.resourcesToString(TrounceUtil.toConvertedCost(att1Total)) + "```\n");

        int att2Count = getCount(false);
        Map<MilitaryUnit, Long> att2Total = getTotalCost(false);

        result.append("## **" + col2 + " Cost** (cast: " + att2Count + " spells/attacks)\n");
        result.append("**Raw cost:**\n");
        result.append("```" + TrounceUtil.resourcesToString(att2Total) + "```\n");
        result.append("**Converted cost:**\n");
        result.append("```" + TrounceUtil.resourcesToString(TrounceUtil.toConvertedCost(att2Total)) + "```\n");

        return result.toString();
    }

    public long getStrengthLosses(boolean isCol1Flag) {
        long total = 0;
        for (AttackOrSpell attackOrSpell : attackOrSpells) {
            boolean isAttacker;
            if (isCol1Flag) {
                isAttacker = isCol1.test(attackOrSpell);
            } else {
                isAttacker = isCol2.test(attackOrSpell) && !isCol1.test(attackOrSpell);
            }
            Map<MilitaryUnit, Long> cost = attackOrSpell.getCost(isAttacker);
            int kingdomId = isAttacker ? attackOrSpell.getAttacker_id() : attackOrSpell.getDefender_id();
            DBKingdom kingdom = DBKingdom.get(kingdomId);
            HeroType hero = kingdom == null ? HeroType.NECROMANCER : kingdom.getHero();
            total += isAttacker ? MilitaryUnit.getAttack(cost, hero) : MilitaryUnit.getDefense(cost, hero);
        }
        return total;
    }

    public Map<MilitaryUnit, Long> getTotalCost(boolean isCol1Flag) {
        Map<MilitaryUnit, Long> total = new EnumMap<>(MilitaryUnit.class);
        for (AttackOrSpell attackOrSpell : attackOrSpells) {
            boolean isAttacker;
            if (isCol1Flag) {
                isAttacker = isCol1.test(attackOrSpell);
            } else {
                isAttacker = isCol2.test(attackOrSpell) && !isCol1.test(attackOrSpell);
            }
            total = ArrayUtil.add(total, attackOrSpell.getCost(isAttacker));
        }
        return total;

    }

    public List<AttackOrSpell> getAttackOrSpells() {
        return new ArrayList<>(attackOrSpells);
    }

    public long getLandGain(boolean isCol1Flag, boolean includePositive, boolean includeLosses) {
        long total = 0;
        for (AttackOrSpell attackOrSpell : attackOrSpells) {
            boolean isAttacker;
            if (isCol1Flag) {
                isAttacker = isCol1.test(attackOrSpell);
            } else {
                isAttacker = isCol2.test(attackOrSpell) && !isCol1.test(attackOrSpell);
            }
            Map<MilitaryUnit, Long> cost = attackOrSpell.getCost(isAttacker);
            Long land = cost.get(MilitaryUnit.LAND);
            if (land == null) continue;;
            if (land > 0 && includeLosses) {
                total -= land;
            } else if (land < 0 && includePositive) {
                total -= land;
            }
        }
        return total;
    }
}
