package link.locutus.core.api.alliance;

import link.locutus.Trocutus;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.WarParser;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomOrAlliance;
import link.locutus.util.TimeUtil;
import link.locutus.util.builder.table.TimeNumericTable;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public enum AllianceMetric {
    LAND(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            Set<DBKingdom> nations = alliance.getKingdoms(true, 0, true);
            double totalLand = 0;
            for (DBKingdom nation : nations) {
                totalLand += nation.getTotal_land();
            }
            return totalLand;
        }
    },
    LAND_AVG(true) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            Set<DBKingdom> nations = alliance.getKingdoms(true, 0, true);
            double totalLand = 0;
            int num = 0;
            for (DBKingdom nation : nations) {
                totalLand += nation.getTotal_land();
                num += 1;
            }
            return totalLand / num;
        }
    },
    MEMBERS(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms(true, 0, true).size();
        }
    },
    MEMBERS_ACTIVE_1W(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms(true, 1440 * 7, true).size();
        }
    },
    VM(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms().stream().filter(f -> f.isVacation() && f.getPosition().ordinal() > Rank.APPLICANT.ordinal()).count();
        }
    },
    INACTIVE_1W(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms().stream().filter(f -> f.isVacation() == false && f.getPosition().ordinal() > Rank.APPLICANT.ordinal() && f.getActive_m() > 1440 * 7).count();
        }
    },
    VM_PCT(true) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            Set<DBKingdom> nations = alliance.getKingdoms();
            return nations.stream().filter(f -> f.isVacation() && f.getPosition().ordinal() > Rank.APPLICANT.ordinal()).count() / (double) nations.size();
        }
    },
    INACTIVE_PCT(true) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            Set<DBKingdom> nations = alliance.getKingdoms();
            return nations.stream().filter(f -> f.getActive_m() > 1440 * 7 && f.getPosition().ordinal() > Rank.APPLICANT.ordinal()).count() / (double) nations.size();
        }
    },

    ATTACK(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms().stream().mapToLong(DBKingdom::getAttackStrength).sum();
        }
    },

    ATTACK_AVG(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms().stream().mapToLong(DBKingdom::getAttackStrength).average().orElse(0);
        }
    },

    DEFENSE(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms().stream().mapToLong(DBKingdom::getDefenseStrength).sum();
        }
    },

    DEFENSE_AVG(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            return alliance.getKingdoms().stream().mapToLong(DBKingdom::getDefenseStrength).average().orElse(0);
        }
    },

    DAILY_STRENGTH_KILLS(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            long start = TimeUtil.getTimeFromTurn(turn - 24);
            long end = TimeUtil.getTimeFromTurn( + 1);
            WarParser parser = WarParser.of(null, null, (Set) alliance.getKingdoms(), null, start, end);
            return parser.getStrengthLosses(false);
        }
    },

    DAILY_STRENGTH_LOSSES(false) {
        @Override
        public double apply(DBAlliance alliance, long turn) {
            long start = TimeUtil.getTimeFromTurn(turn - 24);
            long end = TimeUtil.getTimeFromTurn( + 1);
            WarParser parser = WarParser.of(null, null, (Set) alliance.getKingdoms(), null, start, end);
            return parser.getStrengthLosses(true);
        }
    },

    DAILY_STRENGTH_NET(false) {
        public double apply(DBAlliance alliance, long turn) {
            long start = TimeUtil.getTimeFromTurn(turn - 24);
            long end = TimeUtil.getTimeFromTurn( + 1);
            WarParser parser = WarParser.of(null, null, (Set) alliance.getKingdoms(), null, start, end);
            return parser.getStrengthLosses(false) - parser.getStrengthLosses(true);
        }
    },

    DAILY_LAND_GAIN_ABS(false) {
        public double apply(DBAlliance alliance, long turn) {
            long start = TimeUtil.getTimeFromTurn(turn - 24);
            long end = TimeUtil.getTimeFromTurn( + 1);
            WarParser parser = WarParser.of(null, null, (Set) alliance.getKingdoms(), null, start, end);
            return parser.getLandGain(true, true, false);
        }
    },

    DAILY_LAND_LOSS_ABS(false) {
        public double apply(DBAlliance alliance, long turn) {
            long start = TimeUtil.getTimeFromTurn(turn - 24);
            long end = TimeUtil.getTimeFromTurn( + 1);
            WarParser parser = WarParser.of(null, null, (Set) alliance.getKingdoms(), null, start, end);
            return parser.getLandGain(true, false, true);
        }
    },

    DAILY_LAND_NET(false) {
        public double apply(DBAlliance alliance, long turn) {
            long start = TimeUtil.getTimeFromTurn(turn - 24);
            long end = TimeUtil.getTimeFromTurn( + 1);
            WarParser parser = WarParser.of(null, null, (Set) alliance.getKingdoms(), null, start, end);
            return parser.getLandGain(true, true, true);
        }
    },

    ;

    private static Map.Entry<Integer, double[]> aaRevenueCache;

    private final boolean average;

    AllianceMetric(boolean averageInAggregate) {
        this.average = averageInAggregate;
    }

    public boolean shouldAverage() {
        return average;
    }

    public static AllianceMetric[] values = AllianceMetric.values();

    public static synchronized void update() {
        long turn = TimeUtil.getTurn();
        Set<DBAlliance> alliances = Trocutus.imp().getDB().getAlliances();
        for (DBAlliance alliance : alliances) {
            for (AllianceMetric metric : values) {
                double value = metric.apply(alliance, turn);
                Trocutus.imp().getDB().addMetric(alliance, metric, turn, value);
            }
        }
    }

    public static synchronized void updateLegacy() {
        long currentTurn = TimeUtil.getTurn();
        long startTurn = currentTurn - 400;
        List<AllianceMetric> toUpdateLegacy = Arrays.asList(
                ATTACK, DEFENSE, DAILY_STRENGTH_KILLS, DAILY_STRENGTH_LOSSES, DAILY_STRENGTH_NET, DAILY_LAND_GAIN_ABS, DAILY_LAND_LOSS_ABS, DAILY_LAND_NET
        );
        for (long turn = startTurn; turn < currentTurn; turn++) {
            for (AllianceMetric metric : toUpdateLegacy) {
                for (DBAlliance alliance : Trocutus.imp().getDB().getAlliances()) {
                    System.out.println("Updating " + alliance.getName() + " " + metric + " " + turn + " " + TimeUtil.getTurn());
                    double value = metric.apply(alliance, turn);
                    Trocutus.imp().getDB().addMetric(alliance, metric, turn, value);
                }
            }
        }
    }

    public abstract double apply(DBAlliance alliance, long turn);

    public static TimeNumericTable generateTable(AllianceMetric metric, long cutoffTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        return generateTable(metric, cutoffTurn, TimeUtil.getTurn(), coalitionNames, coalitions);
    }

    public static TimeNumericTable generateTable(Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, coalition);
        return generateTable(metricMap, metrics, startTurn, endTurn, coalitionName, coalition);
    }

    public static TimeNumericTable generateTable(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        double[][] valuesByTurnByMetric = AllianceMetric.toValueByTurnByMetric(metricMap, metrics, startTurn, endTurn, coalition);

        AllianceMetric[] metricsArr = metrics.toArray(new AllianceMetric[0]);
        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (double[] valuesByTurn : valuesByTurnByMetric) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        double[] buffer =  new double[metrics.size()];

        String[] labels = new String[metrics.size()];
        {
            int i = 0;
            for (AllianceMetric metric : metrics) {
                labels[i++] = metric.name();
            }
        }
        String title = coalitionName + " metrics";
        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "turn", "value", labels) {
            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - startTurn);
                for (int i = 0; i < metricsArr.length; i++) {
                    double value = valuesByTurnByMetric[i][turnRelative];
                    if (value != Double.MAX_VALUE && Double.isFinite(value)) {
                        buffer[i] = value;
                    }
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = minTurn; turn <= maxTurn; turn++) {
            table.add(turn, (Void) null);
        }

        return table;
    }

    public static TimeNumericTable generateTable(AllianceMetric metric, long startTurn, long endTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(Collections.singleton(metric), startTurn, endTurn, coalitions);
        double[][] valuesByTurnByCoalition = AllianceMetric.toValueByTurnByCoalition(metricMap, metric, startTurn, endTurn, coalitions);

        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (double[] valuesByTurn : valuesByTurnByCoalition) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        double[] buffer =  new double[coalitions.length];

        String[] labels;
        if (coalitionNames == null || coalitionNames.size() != coalitions.length) {
            labels = new String[coalitions.length];
            for (int i = 0; i < labels.length; i++) labels[i] = "coalition " + (i + 1);
        } else {
            labels = coalitionNames.toArray(new String[0]);
        }
        String title = metric + " by turn";
        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "turn", metric.name(), labels) {

            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - startTurn);
                for (int i = 0; i < coalitions.length; i++) {
                    double value = valuesByTurnByCoalition[i][turnRelative];
                    if (value != Double.MAX_VALUE && Double.isFinite(value)) {
                        buffer[i] = value;
                    }
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = minTurn; turn <= maxTurn; turn++) {
            table.add(turn, (Void) null);
        }

        return table;
    }

    public static double[][] toValueByTurnByMetric(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Collection<AllianceMetric> metrics, long cutoffTurn, long maxTurn, Set<DBAlliance> coalition) {
        AllianceMetric[] metricsArr = metrics.toArray(new AllianceMetric[0]);
        double[][] totalValuesByTurnByMetric = new double[metricsArr.length][(int) (maxTurn - cutoffTurn + 1)];
        for (double[] doubles : totalValuesByTurnByMetric) Arrays.fill(doubles, Double.MAX_VALUE);

        boolean[] shouldAverageArr = new boolean[metricsArr.length];
        double[][] totalMembersByTurnByMetric = new double[metricsArr.length][];
        for (int i = 0; i < shouldAverageArr.length; i++) {
            if (metricsArr[i].shouldAverage() && coalition.size() > 1) {
                shouldAverageArr[i] = true;
                totalMembersByTurnByMetric[i] = new double[(int) (maxTurn - cutoffTurn + 1)];
            }
        }

        for (int k = 0; k < metricsArr.length; k++) {
            AllianceMetric metric = metricsArr[k];
            boolean shouldAverage = shouldAverageArr[k];

            for (DBAlliance alliance : coalition) {
                Map<AllianceMetric, Map<Long, Double>> metricData = metricMap.get(alliance);
                if (metricData == null) continue;
                Map<Long, Double> membersByTurn = null;
                if (shouldAverage) {
                    membersByTurn = metricData.get(AllianceMetric.MEMBERS);
                    if (membersByTurn == null) {
                        continue;
                    }
                }
                Map<Long, Double> metricByTurn = metricData.get(metric);
                if (metricByTurn == null) {
                    continue;
                }

                for (Map.Entry<Long, Double> turnMetric : metricByTurn.entrySet()) {
                    long turn = turnMetric.getKey();
                    Double value = turnMetric.getValue();
                    if (value == null) {
                        continue;
                    }

                    int turnRelative = (int) (turn - cutoffTurn);

                    Double members = null;
                    if (shouldAverage) {
                        members = membersByTurn.get(turn);
                        if (members == null) {
                            continue;
                        }
                        value *= members;
                    }

                    if (members != null) {
                        double[] totalMembersByTurn = totalMembersByTurnByMetric[k];
                        totalMembersByTurn[turnRelative] += members;
                    }
                    double[] totalValuesByTurn = totalValuesByTurnByMetric[k];
                    if (totalValuesByTurn[turnRelative] == Double.MAX_VALUE) {
                        totalValuesByTurn[turnRelative] = value;
                    } else {
                        totalValuesByTurn[turnRelative] += value;
                    }
                }
            }
        }
        for (int i = 0; i < totalMembersByTurnByMetric.length; i++) {
            boolean shouldAverage = shouldAverageArr[i];
            if (shouldAverage) {
                double[] totalValuesByTurn = totalValuesByTurnByMetric[i];
                double[] totalMembersByTurn = totalMembersByTurnByMetric[i];
                for (int j = 0; j < totalMembersByTurn.length; j++) {
                    if (totalValuesByTurn[j] != Double.MAX_VALUE) {
                        totalValuesByTurn[j] /= totalMembersByTurn[j];
                    }
                }
            }
        }

        return totalValuesByTurnByMetric;
    }

    public static double[][] toValueByTurnByCoalition(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, AllianceMetric metric, long cutoffTurn, long maxTurn, Set<DBAlliance>... coalitions) {

        double[][] totalValuesByTurnByCoal = new double[coalitions.length][(int) (maxTurn - cutoffTurn + 1)];
        for (double[] doubles : totalValuesByTurnByCoal) Arrays.fill(doubles, Double.MAX_VALUE);

        boolean shouldAverage = metric.shouldAverage() && metricMap.size() > 1;

        double[][] totalMembersByTurnByCoal = shouldAverage ? new double[coalitions.length][(int) (maxTurn - cutoffTurn + 1)] : null;

        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry1 : metricMap.entrySet()) {
            DBAlliance alliance = entry1.getKey();
            Map<Long, Double> membersByTurn = null;
            if (shouldAverage) {
                membersByTurn = entry1.getValue().get(AllianceMetric.MEMBERS);
                if (membersByTurn == null) {
                    continue;
                }
            }
            Map<Long, Double> metricByTurn = entry1.getValue().get(metric);
            if (metricByTurn == null) {
                continue;
            }

            for (Map.Entry<Long, Double> turnMetric : metricByTurn.entrySet()) {
                long turn = turnMetric.getKey();
                Double value = turnMetric.getValue();
                if (value == null) {
                    continue;
                }

                int turnRelative = (int) (turn - cutoffTurn);

                Double members = null;
                if (shouldAverage) {
                    members = membersByTurn.get(turn);
                    if (members == null) {
                        continue;
                    }
                    value *= members;
                }
                for (int i = 0; i < coalitions.length; i++) {
                    Set<DBAlliance> coalition = coalitions[i];
                    if (coalition.contains(alliance)) {
                        if (members != null) {
                            double[] totalMembersByTurn = totalMembersByTurnByCoal[i];
                            totalMembersByTurn[turnRelative] += members;
                        }
                        double[] totalValuesByTurn = totalValuesByTurnByCoal[i];
                        if (totalValuesByTurn[turnRelative] == Double.MAX_VALUE) {
                            totalValuesByTurn[turnRelative] = value;
                        } else {
                            totalValuesByTurn[turnRelative] += value;
                        }
                    }
                }
            }
        }
        if (shouldAverage) {
            for (int i = 0; i < totalMembersByTurnByCoal.length; i++) {
                double[] totalValuesByTurn = totalValuesByTurnByCoal[i];
                double[] totalMembersByTurn = totalMembersByTurnByCoal[i];
                for (int j = 0; j < totalMembersByTurn.length; j++) {
                    if (totalValuesByTurn[j] != Double.MAX_VALUE) {
                        totalValuesByTurn[j] /= totalMembersByTurn[j];
                    }
                }
            }
        }

        return totalValuesByTurnByCoal;
    }

    public static Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Collection<AllianceMetric> metrics, long minTurn, Set<DBAlliance>... coalitions) {
        return getMetrics(metrics, minTurn, TimeUtil.getTurn(), coalitions);
    }

    public static Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Collection<AllianceMetric> metrics, long minTurn, long maxTurn, Set<DBAlliance>... coalitions) {
        if (minTurn < maxTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (maxTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");
        Set<DBAlliance> allAlliances = new HashSet<>();
        for (Set<DBAlliance> coalition : coalitions) allAlliances.addAll(coalition);
        Set<Integer> aaIds = allAlliances.stream().map(f -> f.getId()).collect(Collectors.toSet());
        Set<AllianceMetric> finalMetrics = new HashSet<>(metrics);
        if (aaIds.size() > 1) {
            for (AllianceMetric metric : metrics) {
                if (metric.shouldAverage()) {
                    finalMetrics.add(AllianceMetric.MEMBERS);
                    break;
                }
            }
        }

        return Trocutus.imp().getDB().getMetrics(aaIds, finalMetrics, minTurn, maxTurn);
    }
}
