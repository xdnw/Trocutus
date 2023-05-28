package link.locutus.core.command;

import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Location;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.BarPlot;
import de.erichseifert.gral.plots.colors.ColorMapper;
import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.Timestamp;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.core.api.alliance.AllianceMetric;
import link.locutus.core.api.game.AttackOrSpell;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.db.entities.WarParser;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomAttributeDouble;
import link.locutus.core.db.entities.kingdom.KingdomOrAlliance;
import link.locutus.core.db.entities.kingdom.SimpleKingdomList;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.TrounceUtil;
import link.locutus.util.builder.GroupedRankBuilder;
import link.locutus.util.builder.NumericGroupRankBuilder;
import link.locutus.util.builder.RankBuilder;
import link.locutus.util.builder.SummedMapRankBuilder;
import link.locutus.util.builder.table.TimeDualNumericTable;
import link.locutus.util.builder.table.TimeNumericTable;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONObject;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatCommands {
    @Command(desc = "Display a graph of the number of attacks by the specified nations per day over a time period")
    public String warAttacksByDay(@Me IMessageIO io, boolean inclueOffensives, boolean includeDefensives,
                                  @Default Set<DBKingdom> nations,
                                  @Arg("Period of time to graph") @Default @Timestamp Long cutoff) throws IOException {
        if (!includeDefensives && !inclueOffensives) {
            throw new IllegalArgumentException("Must include at least one of includeDefensives or inclueOffensives");
        }
        if (cutoff == null) cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        long cutoffFinal = cutoff;
        List<DBAttack> attacks;
        if (nations != null) {
            Set<Integer> nationIds = nations.stream().map(DBKingdom::getId).collect(Collectors.toSet());
            attacks = Trocutus.imp().getDB().getAttacks(f -> f.date > cutoffFinal && (
                    (inclueOffensives && nationIds.contains(f.attacker_id)) ||
                    (includeDefensives && nationIds.contains(f.defender_id))
            ));
        } else {
            attacks = Trocutus.imp().getDB().getAttacks(f -> f.date > cutoffFinal);
        }

        long minDay = TimeUtil.getDay(cutoff);
        long maxDay = TimeUtil.getDay();
        if (maxDay - minDay > 50000) {
            throw new IllegalArgumentException("Too many days.");
        }
        Map<Long, Integer> totalAttacksByDay = new HashMap<>();
        for (DBAttack attack : attacks) {
            long day = TimeUtil.getDay(attack.date);
            totalAttacksByDay.put(day, totalAttacksByDay.getOrDefault(day, 0) + 1);
        }

        List<String> sheet = new ArrayList<>(List.of(
                "Day,Attacks"
        ));
        TimeNumericTable<Void> table = new TimeNumericTable<>("Total attacks by day", "day", "attacks", "") {
            @Override
            public void add(long day, Void ignore) {
                long offset = day - minDay;
                int attacks = totalAttacksByDay.getOrDefault(day, 0);
                add(offset, attacks);
                String dayStr = TimeUtil.YYYY_MM_DD_FORMAT.format(new Date(TimeUtil.getTimeFromDay(day)));
                sheet.add(dayStr + "," + attacks);
            }
        };

        for (long day = minDay; day <= maxDay; day++) {
            table.add(day, (Void) null);
        }

        io.create()
                .file("img.png", table.write(true))
                .file("data.csv", StringMan.join(sheet, "\n"))
                .append("Done!")
                .send();
        return null;
    }

    @Command(desc = "Rank nations by an attribute")
    public void nationRanking(@Me IMessageIO channel, @Me JSONObject command, Set<DBKingdom> nations, KingdomAttributeDouble attribute, @Switch("a") boolean groupByAlliance, @Switch("r") boolean reverseOrder, @Arg("Total value instead of average per nation") @Switch("t") boolean total) {
        Map<DBKingdom, Double> attributeByKingdom = new HashMap<>();
        for (DBKingdom nation : nations) {
            Double value = attribute.apply(nation);
            if (!Double.isFinite(value)) continue;
            attributeByKingdom.put(nation, value);
        }


        String title = (total ? "Total" : groupByAlliance ? "Average" : "Top") + " " + attribute.getName() + " by " + (groupByAlliance ? "alliance" : "nation");

        SummedMapRankBuilder<DBKingdom, Double> builder = new SummedMapRankBuilder<>(attributeByKingdom);

        RankBuilder<String> named;
        if (groupByAlliance) {
            NumericGroupRankBuilder<Integer, Double> grouped = builder.group((entry, builder1) -> builder1.put(entry.getKey().getAlliance_id(), entry.getValue()));
            SummedMapRankBuilder<Integer, ? extends Number> summed;
            if (total) {
                summed = grouped.sum();
            } else {
                summed = grouped.average();
            }
            summed = reverseOrder ? summed.sortAsc() : summed.sort();
            named = summed.nameKeys(f -> TrounceUtil.getName(f, true));
        } else {
            builder = reverseOrder ? builder.sortAsc() : builder.sort();
            named = builder.nameKeys(DBKingdom::getName);
        }
        named.build(channel, command, title, true);
    }

    @Command(desc = "Generate a graph of nation military strength by score between two coalitions", aliases = {"strengthTierGraph"})
    public String strengthTierGraph(@Me GuildDB db, @Me IMessageIO channel,
                                    Set<DBKingdom> coalition1,
                                    Set<DBKingdom> coalition2,
                                    @Switch("i") boolean includeInactives,
                                    @Switch("n") boolean includeApplicants,
                                    @Arg("Use the score/strength of coalition 1 nations at specific military unit levels") @Switch("a") Double col1StrPerScore,
                                    @Arg("Use the score/strength of coalition 2 nations at specific military unit levels") @Switch("b") Double col2StrPerScore) throws IOException {
        Set<DBKingdom> allKingdoms = new HashSet<>();
        coalition1.removeIf(f -> f.isVacation() || (!includeApplicants && f.getPosition().ordinal() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.removeIf(f -> f.isVacation() || (!includeApplicants && f.getPosition().ordinal() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        allKingdoms.addAll(coalition1);
        allKingdoms.addAll(coalition2);
        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBKingdom nation : allKingdoms) {
            maxScore = (int) Math.max(maxScore, nation.getAttackMaxRange());
            minScore = (int) Math.min(minScore, nation.getAttackMinRange());
        }
        double[] coal1Str = new double[(int) TrounceUtil.getMaxScoreRange(maxScore, true)];
        double[] coal2Str = new double[(int) TrounceUtil.getMaxScoreRange(maxScore, true)];

        double[] coal1StrSpread = new double[coal1Str.length];
        double[] coal2StrSpread = new double[coal2Str.length];

        Function<DBKingdom, Double> getStrength1 = new Function<>() {
            @Override
            public Double apply(DBKingdom kingdom) {
                if (col1StrPerScore != null) return col1StrPerScore * kingdom.getScore();
                return (double) kingdom.getAverageStrength();
            }
        };

        Function<DBKingdom, Double> getStrength2 = new Function<>() {
            @Override
            public Double apply(DBKingdom kingdom) {
                if (col2StrPerScore != null) return col2StrPerScore * kingdom.getScore();
                return (double) kingdom.getAverageStrength();
            }
        };

        for (DBKingdom nation : coalition1) {
            coal1Str[(int) TrounceUtil.getMinScoreRange(nation.getScore(), true)] += getStrength1.apply(nation);
        }
        for (DBKingdom nation : coalition2) {
            coal2Str[(int) TrounceUtil.getMinScoreRange(nation.getScore(), true)] += getStrength2.apply(nation);
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal1StrSpread[i] += shaped;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal2StrSpread[i] += shaped;
            }
        }

        double[] buffer = new double[2];
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>("Effective military strength by score range", "score", "strength", "coalition 1", "coalition 2") {
            @Override
            public void add(long score, Void ignore) {
                add(score, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
            }
        };
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            table.add(score, (Void) null);
        }
        table.write(channel, false);
        return null;
    }

    @Command(desc = "Graph the attributes of the nations of two coalitions by city count\n" +
            "e.g. How many nations, soldiers etc. are at each city")
    public String attributeScoreGraph(@Me IMessageIO channel,
                                     KingdomAttributeDouble metric,
                                     Set<DBKingdom> coalition1,
                                     Set<DBKingdom> coalition2,
                                     @Switch("i") boolean includeInactives,
                                     @Switch("a") boolean includeApplicants,
                                     @Arg("Compare the sum of each nation's attribute in the coalition instead of average") @Switch("t") boolean total) throws IOException {
        Set<DBKingdom> allKingdoms = new HashSet<>();
        coalition1.removeIf(f -> f.isVacation() || (!includeApplicants && f.getPosition().ordinal() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.removeIf(f -> f.isVacation() || (!includeApplicants && f.getPosition().ordinal() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        allKingdoms.addAll(coalition1);
        allKingdoms.addAll(coalition2);
        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBKingdom nation : allKingdoms) {
            maxScore = (int) Math.max(maxScore, nation.getAttackMaxRange());
            minScore = (int) Math.min(minScore, nation.getAttackMinRange());
        }
        double[] coal1Str = new double[(int) TrounceUtil.getMaxScoreRange(maxScore, true)];
        double[] coal2Str = new double[(int) TrounceUtil.getMaxScoreRange(maxScore, true)];

        double[] coal1StrSpread = new double[coal1Str.length];
        double[] coal2StrSpread = new double[coal2Str.length];

        for (DBKingdom nation : coalition1) {
            coal1Str[(int) TrounceUtil.getMinScoreRange(nation.getScore(), true)] += metric.apply(nation);
        }
        for (DBKingdom nation : coalition2) {
            coal2Str[(int) TrounceUtil.getMinScoreRange(nation.getScore(), true)] += metric.apply(nation);
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal1StrSpread[i] += shaped;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal2StrSpread[i] += shaped;
            }
        }

        double[] buffer = new double[2];
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>("Effective military strength by score range", "score", "strength", "coalition 1", "coalition 2") {
            @Override
            public void add(long score, Void ignore) {
                add(score, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
            }
        };
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            table.add(score, (Void) null);
        }
        table.write(channel, false);
        return null;
    }

    @Command(desc = "Generate a graph of nation counts by score between two coalitions", aliases = {"scoreTierGraph", "scoreTierSheet"})
    public String scoreTierGraph(@Me GuildDB db, @Me IMessageIO channel, Set<DBKingdom> coalition1, Set<DBKingdom> coalition2, @Switch("i") boolean includeInactives, @Switch("a") boolean includeApplicants) throws IOException {
        Set<DBKingdom> allKingdoms = new HashSet<>();
        coalition1.removeIf(f -> f.isVacation() || (!includeApplicants && f.getPosition().ordinal() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.removeIf(f -> f.isVacation() || (!includeApplicants && f.getPosition().ordinal() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        allKingdoms.addAll(coalition1);
        allKingdoms.addAll(coalition2);

        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBKingdom nation : allKingdoms) {
            maxScore = (int) Math.max(maxScore, nation.getScore());
            minScore = (int) Math.min(minScore, nation.getScore());
        }
        double[] coal1Str = new double[(int) (maxScore * 1.75)];
        double[] coal2Str = new double[(int) (maxScore * 1.75)];

        double[] coal1StrSpread = new double[coal1Str.length];
        double[] coal2StrSpread = new double[coal2Str.length];

        for (DBKingdom nation : coalition1) {
            coal1Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (DBKingdom nation : coalition2) {
            coal2Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = Math.min(coal1StrSpread.length, (int) (1.75 * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal1StrSpread[i] += val;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = Math.min(coal2StrSpread.length, (int) (1.75 * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal2StrSpread[i] += val;
            }
        }

        TimeDualNumericTable<Void> table = new TimeDualNumericTable<>("Kingdoms by score range", "score", "nations", "coalition 1", "coalition 2") {
            @Override
            public void add(long score, Void ignore) {
                add(score, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
            }
        };
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            table.add(score, (Void) null);
        }
        table.write(channel, false);
        return null;
    }

    @Command(desc = "Graph an alliance metric over time for a coalition")
    public String allianceMetricsCompareByTurn(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<DBAlliance> alliances,
                                               @Arg("Date to start from")
                                               @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        Set<DBAlliance>[] coalitions = alliances.stream().map(Collections::singleton).toList().toArray(new Set[0]);
        List<String> coalitionNames = alliances.stream().map(DBAlliance::getName).collect(Collectors.toList());
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, coalitionNames, coalitions);
        table.write(channel, true);
        return "Done!";
    }

    @Command(desc = "Graph an alliance metric over time for two coalitions")
    public String allianceMetricsAB(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<DBAlliance> coalition1, Set<DBAlliance> coalition2,
                                    @Arg("Date to start from")
                                    @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, null, coalition1, coalition2);
        table.write(channel, true);
        return "Done!";
    }

    @Command(desc = "Rank alliances by a metric")
    public void allianceRanking(@Me IMessageIO channel, @Me JSONObject command, Set<DBAlliance> alliances, AllianceMetric metric, @Switch("r") boolean reverseOrder, @Switch("f") boolean uploadFile) {
        long turn = TimeUtil.getTurn();
        Set<Integer> aaIds = alliances.stream().map(DBAlliance::getId).collect(Collectors.toSet());

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metrics = Trocutus.imp().getDB().getMetrics(aaIds, metric, turn);

        Map<DBAlliance, Double> metricsDiff = new LinkedHashMap<>();
        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metrics.entrySet()) {
            DBAlliance alliance = entry.getKey();
            double diff = entry.getValue().get(metric).values().iterator().next();
            metricsDiff.put(alliance, diff);
        }
        displayAllianceRanking(channel, command, metric, metricsDiff, reverseOrder, uploadFile);
    }

    public void displayAllianceRanking(IMessageIO channel, JSONObject command, AllianceMetric metric, Map<DBAlliance, Double> metricsDiff, boolean reverseOrder, boolean uploadFile) {

        SummedMapRankBuilder<DBAlliance, Double> builder = new SummedMapRankBuilder<>(metricsDiff);
        if (reverseOrder) {
            builder = builder.sortAsc();
        } else {
            builder = builder.sort();
        }
        String title = "Top " + metric + " by alliance";

        RankBuilder<String> named = builder.nameKeys(DBAlliance::getName);
        named.build(channel, command, title, uploadFile);
    }

    @Command(desc = "Rank alliances by a metric over a specified time period")
    public void allianceRankingTime(@Me IMessageIO channel, @Me JSONObject command, Set<DBAlliance> alliances, AllianceMetric metric, @Timestamp long timeStart, @Timestamp long timeEnd, @Switch("r") boolean reverseOrder, @Switch("f") boolean uploadFile) {
        long turnStart = TimeUtil.getTurn(timeStart);
        long turnEnd = TimeUtil.getTurn(timeEnd);
        Set<Integer> aaIds = alliances.stream().map(DBAlliance::getId).collect(Collectors.toSet());

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricsStart = Trocutus.imp().getDB().getMetrics(aaIds, metric, turnStart);
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricsEnd = Trocutus.imp().getDB().getMetrics(aaIds, metric, turnEnd);

        Map<DBAlliance, Double> metricsDiff = new LinkedHashMap<>();
        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metricsEnd.entrySet()) {
            DBAlliance alliance = entry.getKey();
            if (!metricsStart.containsKey(alliance)) continue;
            double dataStart = metricsStart.get(alliance).get(metric).values().iterator().next();
            double dataEnd = entry.getValue().get(metric).values().iterator().next();
            metricsDiff.put(alliance, dataEnd - dataStart);
        }
        displayAllianceRanking(channel, command, metric, metricsDiff, reverseOrder, uploadFile);
    }

    @Command(desc = "Graph an alliance metric over time for a coalition")
    public String allianceMetricsByTurn(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<DBAlliance> coalition,
                                        @Arg("Date to start from")
                                        @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        List<String> coalitionNames = List.of(metric.name());
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, coalitionNames, coalition);
        table.write(channel, true);
        return "Done!";
    }

    @Command(desc = "War costs between two coalitions over a time period")
    public String warsCost(@Me IMessageIO channel,
                           Set<KingdomOrAlliance> coalition1, Set<KingdomOrAlliance> coalition2,
                           @Timestamp long timeStart,
                           @Default @Timestamp Long timeEnd,
//                           @Switch("u") boolean ignoreUnits,
//                           @Switch("i") boolean ignoreLand,
//                           @Switch("c") boolean ignoreConsumption,
//                           @Switch("l") boolean ignoreLoot,
                           @Switch("l") boolean listWarIds,
                           @Switch("t") boolean showWarTypes,

                           @Switch("w") Set<AttackOrSpellType> allowedWarTypes) {
        if (timeEnd == null) timeEnd = Long.MAX_VALUE;


        WarParser parser = WarParser.of("Coalition 1", "Coalition 2", coalition1, coalition2, timeStart, timeEnd)
                .allowedWarTypes(allowedWarTypes);
        WarParser cost = parser.toWarCost();

        IMessageBuilder msg = channel.create();
        msg.append(cost.toCostString());
        if (listWarIds) {
            msg.file(cost.getCount() + "_attackspells.txt", "- " + StringMan.join(cost.getIds(), "\n- "));
        }
        if (showWarTypes) {
            Map<AttackOrSpellType, Integer> byType = new HashMap<>();
            for (AttackOrSpell entry : parser.getAttackOrSpells()) {
                byType.put(entry.getType(), byType.getOrDefault(entry.getType(), 0) + 1);
            }
            StringBuilder response = new StringBuilder();
            for (Map.Entry<AttackOrSpellType, Integer> entry : byType.entrySet()) {
                response.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            msg.embed("War Types", response.toString());
        }
        msg.send();
        return null;
    }

    @Command(desc = "Rank the number of wars between two coalitions by nation or alliance\n" +
            "Defaults to alliance ranking")
    public String warRanking(@Me JSONObject command, @Me IMessageIO channel, @Timestamp long time, Set<KingdomOrAlliance> attackers, Set<KingdomOrAlliance> defenders,
                             @Arg("Only include offensive wars in the ranking")
                             @Switch("o") boolean onlyOffensives,
                             @Arg("Only include defensive wars in the ranking")
                             @Switch("d") boolean onlyDefensives,
                             @Arg("Rank the average wars per alliance member")
                             @Switch("n") boolean normalizePerMember,
                             @Arg("Ignore inactive nations when determining alliance member counts")
                             @Switch("i") boolean ignore2dInactives,
                             @Arg("Rank by nation instead of alliance")
                             @Switch("a") boolean rankByKingdom,
                             @Arg("Only rank wars with these statuses")
                             @Switch("s") Set<AttackOrSpellType> allowedTypes) {
        WarParser parser = WarParser.of("coalition 1", "Coalition 2", attackers, defenders, time, Long.MAX_VALUE);
        if (allowedTypes != null) parser.allowedWarTypes(allowedTypes);
        List<AttackOrSpell> ops = parser.getAttackOrSpells();

        SummedMapRankBuilder<Integer, Double> ranksUnsorted = new RankBuilder<>(ops).group(new BiConsumer<AttackOrSpell, GroupedRankBuilder<Integer, AttackOrSpell>>() {
            @Override
            public void accept(AttackOrSpell dbWar, GroupedRankBuilder<Integer, AttackOrSpell> builder) {
                if (!rankByKingdom) {
                    if (dbWar.getAttacker_aa() != 0 && !onlyDefensives) builder.put(dbWar.getAttacker_aa(), dbWar);
                    if (dbWar.getDefender_aa() != 0 && !onlyOffensives) builder.put(dbWar.getDefender_aa(), dbWar);
                } else {
                    if (!onlyDefensives) builder.put(dbWar.getAttacker_id(), dbWar);
                    if (!onlyOffensives) builder.put(dbWar.getDefender_id(), dbWar);
                }
            }
        }).sumValues(f -> 1d);
        if (normalizePerMember && !rankByKingdom) {
            ranksUnsorted = ranksUnsorted.adapt((aaId, numWars) -> {
                DBAlliance aa = DBAlliance.get(aaId);
                if (aa == null) return 0d;
                int num = aa.getKingdoms(true, ignore2dInactives ? 2440 : Integer.MAX_VALUE, true).size();
                if (num == 0) return 0d;
                return numWars / (double) num;
            });
        }

        RankBuilder<String> ranks = ranksUnsorted.sort().nameKeys(i -> TrounceUtil.getName(i, !rankByKingdom));
        String offOrDef ="";
        if (onlyOffensives != onlyDefensives) {
            if (!onlyDefensives) offOrDef = "offensive ";
            else offOrDef = "defensive ";
        }

        String title = "Most " + offOrDef + "wars (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - time) +")";
        if (normalizePerMember) title += "(per " + (ignore2dInactives ? "active " : "") + "nation)";

        ranks.build(channel, command, title, true);

        return null;
    }
}
