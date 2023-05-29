package link.locutus.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

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
        return (int) (score * 1.5);
    }

    public static int getMinScoreRange(int score, boolean declare) {
        return (int) (score * 0.5);
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

    public static int landLoot(int attackerAcres, int defenderAcres) {
        double defenderRatio = (double) defenderAcres / attackerAcres;
        double loot = 0.1 * attackerAcres * Math.pow(defenderRatio, 2.2);
        return (int) Math.round(loot);
    }

    public static double getFeyLand(int myLand, int attack) {
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
        // 40 soldiers per acre

        // 20 per 2 turns
        // 40 soldiers per acre
        // 88 * land * 0.2
        // x = 1 / 40

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
}
