package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.util.ArrayUtil;
import link.locutus.util.MathMan;
import link.locutus.util.PagePriority;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.TrounceUtil;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
                "WarRange: " + MathMan.format(TrounceUtil.getMinScoreRange(score, true)) + "- " + TrounceUtil.getMaxScoreRange(score, true) + "\n" +
                "Can be Attacked By: " + MathMan.format(TrounceUtil.getMinScoreRange(score, false)) + "- " + TrounceUtil.getMaxScoreRange(score, false);
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
    public String fey_optimal(DBKingdom kingdom, @Default Integer my_score, @Default Integer my_attack, @Switch("u") boolean updateFey) throws IOException {
        Trocutus.imp().getScraper().fetchAllianceMemberStrength(PagePriority.FETCH_SELF, Trocutus.imp().rootAuth(), kingdom.getRealm_id());

        if (my_score == null) my_score = kingdom.getScore();
        if (my_attack == null) my_attack = kingdom.getAttackStrength();

        double optimalBothFey = 0;
        double optimalBothValue = 0;

        double optimalLandFey = 0;
        double optimalLandValue = 0;
        int minScore = TrounceUtil.getMinScoreRange(my_score, true);
        int maxScore = TrounceUtil.getMaxScoreRange(my_score, true);
        boolean notStrong = false;

        for (int land = minScore; land <= maxScore; land++) {
            double dpa = TrounceUtil.getFeyDPA(land);
            double strength = dpa * land;
            if (strength > my_attack) {
                notStrong = true;
                maxScore = land - 1;
                break; // can't beat it :(
            }

            double losses = strength * 3;
            double loot = 88 * land * 0.2;
            double landLoot = TrounceUtil.landLoot(my_score, land, true);
            double landValue = 2000 * landLoot;
            double netBoth = loot + landLoot * landValue  - losses;
            if (netBoth > optimalBothValue) {
                optimalBothValue = netBoth;
                optimalBothFey = land;
            }
            if (landLoot > optimalLandValue) {
                optimalLandValue = landLoot;
                optimalLandFey = land;
            }
        }

        StringBuilder response = new StringBuilder();
        response.append("Your Land: " + MathMan.format(my_score) + " | Your Attack: " + MathMan.format(my_attack) + "\n");
        response.append("Optimal fey targets:\n");
        response.append("- land+gold: " + MathMan.format(optimalBothFey) + " land -> net $" + MathMan.format((long) optimalBothValue) + "\n");
        response.append("- land: " + MathMan.format(optimalLandFey) + " land -> net " + MathMan.format((long) optimalLandValue) + "\n");
        if (optimalLandValue < 1000 && notStrong) {
            response.append("`note: Your army is too weak to attack the top fey. See: `" + CM.fey.top.cmd.toSlashMention() + "\n");
        }

        List<DBKingdom> feyFound = new ArrayList<>();
        DBKingdom bothFey = TrounceUtil.getNearestFey(kingdom.getRealm_id(), (int) optimalBothFey, minScore, maxScore, updateFey);
        if (bothFey != null) {
            feyFound.add(bothFey);
        }
        if (Math.round(optimalLandFey) != Math.round(optimalBothFey)) {
            DBKingdom landFey = TrounceUtil.getNearestFey(kingdom.getRealm_id(), (int) optimalLandFey, minScore, maxScore, false);
            if (landFey != null) {
                feyFound.add(landFey);
            }
        }
        if (!feyFound.isEmpty()) {
            response.append("\n**Found the following fey:**\n");
            for (DBKingdom fey : feyFound) {
                response.append("<" + fey.getUrl(kingdom.getSlug()) + ">\n");
                long strength = (long) (TrounceUtil.getFeyDPA(fey.getTotal_land()) * fey.getTotal_land());
                response.append("- " + fey.getTotal_land() + " land | " + MathMan.format(strength) + " strength (approx.)\n");
            }
        }

        return response.toString();
    }

    @Command
    public String fey_top(DBKingdom attacker, @Default Integer my_score, @Switch("u") boolean updateFey) throws IOException {
        Trocutus.imp().getScraper().updateKingdom(PagePriority.FETCH_SELF, attacker.getRealm_id(), attacker.getSlug());
        if (my_score == null) my_score = attacker.getScore();
        int minScore = TrounceUtil.getMinScoreRange(my_score, true);
        int maxScore = TrounceUtil.getMaxScoreRange(my_score, true);
        int maxLand = 0;
        int maxFeyScore = 0;
        for (int score = minScore; score <= maxScore; score++) {
            int land = TrounceUtil.landLoot(my_score, score, true);
            if (land > maxLand) {
                maxLand = land;
                maxFeyScore = score;
            }
        }

        double dpa = TrounceUtil.getFeyDPA(maxScore);
        int defense = (int) (dpa * maxScore);
        StringBuilder response = new StringBuilder();
        response.append("Your Land: " + MathMan.format(my_score) + "\n");
        response.append("Max Fey:\n");
        response.append("- land: " + MathMan.format(maxScore) + " land -> " + MathMan.format(defense) + " defense\n");
        if (maxScore > maxFeyScore) {
            response.append("Max land loot fey @" + MathMan.format(maxFeyScore) + " score. -> Strength: " + MathMan.format(TrounceUtil.getFeyDPA(maxFeyScore) * maxFeyScore));
        }

        DBKingdom fey = TrounceUtil.getNearestFey(attacker.getRealm_id(), maxFeyScore, minScore, maxScore, updateFey);
        if (fey != null) {
            response.append("\n**Found the following fey:**\n");
            response.append("<" + fey.getUrl(attacker.getSlug()) + ">\n");
            long strength = (long) (TrounceUtil.getFeyDPA(fey.getTotal_land()) * fey.getTotal_land());
            response.append("- " + fey.getTotal_land() + " land | " + MathMan.format(strength) + " strength (approx.)\n");
        }


        return response.toString();
    }

    @Command
    public String fey_land(int attack) {
        double land = TrounceUtil.getFeyLand(attack);
        return "Your army is on par with fey of approx. ~" + MathMan.format(land) + " land";
    }

    @Command
    public String fey_strength(int land) {
        double dpa = TrounceUtil.getFeyDPA(land) * land;
        return "Fey with " + MathMan.format(land) + " land has strength of ~" + MathMan.format(dpa) + " attack";
    }

    @Command
    public String findAidPath(DBKingdom sender, DBKingdom receiver, Set<DBKingdom> intermediaries) {
        boolean reverse = false;
        if (sender.getScore() > receiver.getScore()) {
            // swap
            DBKingdom temp = sender;
            sender = receiver;
            receiver = temp;
            reverse = true;
        }
        int senderScore = sender.getScore();
        int receiverScore = receiver.getScore();
        double direct = TrounceUtil.getTaxrate(senderScore, receiverScore);
        if (direct <= 0.1) {
            return "Direct send is optimal: @" + MathMan.format(direct * 100) + "% taxrate";
        }

        List<DBKingdom> intermediariesList = new ArrayList<>(intermediaries);
        // sort by score
        intermediariesList.sort(Comparator.comparingInt(DBKingdom::getScore));

        intermediariesList.removeIf(kingdom -> kingdom.getScore() <= senderScore || kingdom.getScore() >= receiverScore);

        if (intermediariesList.isEmpty()) {
            return "No intermediaries between sender and receiver provided.\n" +
                    "Direct send is optimal: @" + MathMan.format(direct * 100) + "% taxrate";
        }

        ArrayDeque<SendPathNode> queue = new ArrayDeque<>();
        SendPathNode root = new SendPathNode(null, 1, sender, 0);
        queue.add(root);

        SendPathNode optimal = null;
        double maxRemaining = 0;
        while (!queue.isEmpty()) {
            if (queue.size() % 1000 == 0) {
                System.out.println("queue size: " + queue.size());
            }
            SendPathNode current = queue.poll();
            if (optimal != null && current.remainingPct < maxRemaining) {
                continue;
            }
            for (int i = current.intermediaryIndex; i < intermediariesList.size(); i++) {
                DBKingdom next = intermediariesList.get(i);
                if (next.getTotal_land() <= current.kingdom.getTotal_land()) continue;
                double taxRate = TrounceUtil.getTaxrate(current.kingdom.getTotal_land(), next.getTotal_land());
                double remaining = current.remainingPct * (1 - taxRate);

                SendPathNode nextNode = new SendPathNode(current, remaining, next, i + 1);
                queue.add(nextNode);
            }

            // goal
            {
                double taxRate = TrounceUtil.getTaxrate(current.kingdom.getTotal_land(), receiver.getTotal_land());
                double remaining = current.remainingPct * (1 - taxRate);
                if (remaining > maxRemaining) {
                    maxRemaining = remaining;
                    optimal = current;
                }
            }
        }
        double directRemaining = 1-direct;
        if (maxRemaining <= directRemaining) {
            return "Direct send is optimal: @" + MathMan.format(direct * 100) + "% taxrate";
        }

        List<DBKingdom> bestPath = new ArrayList<>(Arrays.asList(receiver));
        // add all nodes in optimal to bestPath
        SendPathNode current = optimal;
        while (current != null) {
            bestPath.add(current.kingdom);
            current = current.parent;
        }
        // if reverse, reverse bestPath
        if (!reverse) {
            Collections.reverse(bestPath);
        }

        StringBuilder result = new StringBuilder();
        result.append("Optimal send path:\n");
        double remaining = 1;
        for (int i = 0; i < bestPath.size(); i++) {
            DBKingdom intermediary = bestPath.get(i);
            DBKingdom previous = i == 0 ? null : bestPath.get(i - 1);

            result.append(i + ". **" + intermediary.getName() + "**: `" + MathMan.format(intermediary.getScore()) + "` score");
            if (previous != null) {
                double taxRate = TrounceUtil.getTaxrate(previous.getTotal_land(), intermediary.getTotal_land());
                remaining *= (1 - taxRate);
                result.append(" | `" + MathMan.format(taxRate * 100) + "%` taxrate | `" + MathMan.format(remaining * 100) + "%` remaining");
            }
            result.append("\n");
        }
        result.append("Remaining: " + MathMan.format(maxRemaining * 100) + "%\n");
        double ratio = maxRemaining / directRemaining;
        result.append("Direct Remaining: " + MathMan.format(directRemaining * 100) + "% (" + MathMan.format(ratio) + "x less)\n");
        return result.toString();
    }

    private static record SendPathNode(SendPathNode parent, double remainingPct, DBKingdom kingdom, int intermediaryIndex) { }

    @Command
    public String landCost(int from, @Default Integer to) {
        StringBuilder result = new StringBuilder();
        long costPerAcre = TrounceUtil.getLandCost(from);

        result.append("$" + MathMan.format(costPerAcre) + " per acre\n");
        if (to != null) {
            long total = TrounceUtil.getLandCost(from, to);
            result.append("$" + MathMan.format(total) + " for " + MathMan.format(to) + " acres\n");
        }
        return result.toString();
    }
}
