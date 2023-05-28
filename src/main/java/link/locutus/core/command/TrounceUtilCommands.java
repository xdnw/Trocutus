package link.locutus.core.command;

import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.util.ArrayUtil;
import link.locutus.util.MathMan;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.TrounceUtil;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TrounceUtilCommands {
    @Command
    public String landLoot(int attackerAcres, int defenderAcres) {
        double defenderRatio = (double) defenderAcres / attackerAcres;
        double loot = 0.1 * attackerAcres * Math.pow(defenderRatio, 2.2);
        String formula = "Formula: `0.1 * attackerAcres * ((defenderAcres / attackerAcres) ^ 2.2)`";
        return formula + "\n = " + MathMan.format(Math.round(loot));
    }

    @Command
    public String score(int score) {
        return "Score: " + MathMan.format(score) + "\n" +
                "WarRange: " + MathMan.format(score * 0.5) + "- " + MathMan.format(score * 1.5) + "\n" +
                "Can be Attacked By: " + MathMan.format(score / 1.5) + "- " + MathMan.format(score / 0.5);
    }

    @Command(desc = "Get the cost of military units and their upkeep")
    public String unitCost(Map<MilitaryUnit, Long> units, double costFactor) {
        StringBuilder response = new StringBuilder();

        response.append("**Units " + StringMan.getString(units) + "**:\n");
        Map<MilitaryUnit, Long> convertedCost = TrounceUtil.toConvertedCost(units);
        Map<MilitaryUnit, Long> upkeep = new EnumMap<>(MilitaryUnit.class);

        for (Map.Entry<MilitaryUnit, Long> entry : units.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            Long amt = entry.getValue();
            upkeep = ArrayUtil.add(upkeep, Collections.singletonMap(MilitaryUnit.GOLD, (long) unit.getUpkeep() * amt));
        }
        response.append("Input:\n```" + TrounceUtil.resourcesToString(units) + "``` ");
        if (!Objects.equals(convertedCost, units) && !convertedCost.isEmpty()) {
            response.append("Purchase Cost:\n```" + TrounceUtil.resourcesToString(convertedCost) + "``` ");
        }
        if (!Objects.equals(upkeep, units) && !upkeep.isEmpty()) {
            response.append("Upkeep:\n```" + TrounceUtil.resourcesToString(upkeep) + "``` ");
        }
        return response.toString();
    }

    @Command
    public String fey_optimal(int my_score, int my_attack) {
        double optimalBothFey = 0;
        double optimalBothValue = 0;

        double optimalGoldFey = 0;
        double optimalGoldValue = 0;

        double optimalLandFey = 0;
        double optimalLandValue = 0;
        int minScore = TrounceUtil.getMinScoreRange(my_score, true);
        int maxScore = TrounceUtil.getMaxScoreRange(my_score, true);

        for (int land = minScore; land <= maxScore; land++) {
            double dpa = TrounceUtil.getFeyDPA(land);
            double strength = dpa * land;
            if (strength > my_attack) break; // can't beat it :(

            double losses = strength * 3;
            double loot = 88 * land * 0.2;
            double landLoot = TrounceUtil.landLoot(my_score, land);
            double netBoth = loot + landLoot * 500 - losses;
            double netLand = landLoot * 500 - losses;
            double netGold = loot - losses;
            if (netBoth > optimalBothValue) {
                optimalBothValue = netBoth;
                optimalBothFey = land;
            }
            if (netLand > optimalLandValue) {
                optimalLandValue = netLand;
                optimalLandFey = land;
            }
            if (netGold > optimalGoldValue) {
                optimalGoldValue = netGold;
                optimalGoldFey = land;
            }
        }

        double fey = TrounceUtil.getFeyLand(my_score, my_attack);

        StringBuilder response = new StringBuilder();
        response.append("Your Land: " + MathMan.format(my_score) + " | Your Attack: " + MathMan.format(my_attack) + "\n");
        response.append("Your army is on par with fey of approx. ~" + MathMan.format(fey) + " land\n");
        response.append("Optimal fey targets:\n");
        // both
        response.append("- land+gold: " + MathMan.format(optimalBothFey) + " land -> net $" + MathMan.format((long) optimalBothValue) + "\n");
        response.append("- land: " + MathMan.format(optimalLandFey) + " land -> net $" + MathMan.format((long) optimalLandValue) + "\n");
        response.append("- gold: " + MathMan.format(optimalGoldFey) + " land -> net $" + MathMan.format((long) optimalGoldValue) + "\n");
        return response.toString();
    }

    @Command
    public String fey_land(int myLand, int attack) {
        double land = TrounceUtil.getFeyLand(myLand, attack);
        return "Your army is on par with fey of approx. ~" + MathMan.format(land) + " land";
    }
}
