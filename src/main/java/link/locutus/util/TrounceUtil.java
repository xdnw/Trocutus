package link.locutus.util;

import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public double getFeyLand(int myLand, int attack) {
        if (myLand < 20000) {
            return Math.sqrt(20000 * attack / 20d);
        } else if (myLand < 50000) {
            return Math.sqrt(20000 * 20 + 30000 * (attack - 20) / 20d);
        } else {
            return attack / 40d;
        }
    }

    public double getFeyDPA(int land) {
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
}
