package link.locutus.core.db.guild.interview;

import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomFilter;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MathMan;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IACheckup {
    public static Map<IACheckup.AuditType, Map.Entry<Object, String>> simplify(Map<IACheckup.AuditType, Map.Entry<Object, String>> auditFinal) {
        Map<AuditType, Map.Entry<Object, String>> audit = new LinkedHashMap<>(auditFinal);
        audit.entrySet().removeIf(f -> {
            Map.Entry<Object, String> value = f.getValue();
            return value == null || value.getValue() == null;
        });

        Map<IACheckup.AuditType, Map.Entry<Object, String>> tmp = new LinkedHashMap<>(audit);
        tmp.entrySet().removeIf(f -> {
            IACheckup.AuditType required = f.getKey().required;
            while (required != null) {
                if (audit.containsKey(required)) return true;
                required = required.required;
            }
            return false;
        });
        return tmp;
    }

    public static void createEmbed(MessageChannel channel, Message message, String command, DBKingdom nation, Map<IACheckup.AuditType, Map.Entry<Object, String>> auditFinal, Integer page) {
        Map<AuditType, Map.Entry<Object, String>> audit = simplify(auditFinal);
        int failed = audit.size();

        boolean newPage = page == null;
        if (page == null) page = 0;
        String title = (page + 1) + "/" + failed + " tips for " + nation.getName();

        List<String> pages = new ArrayList<>();
        for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : audit.entrySet()) {
            IACheckup.AuditType type = entry.getKey();
            Map.Entry<Object, String> info = entry.getValue();
            if (info == null || info.getValue() == null) continue;

            StringBuilder body = new StringBuilder();

            body.append("**" + type.name() + "**: ").append(type.emoji).append(" ");
            body.append(info.getValue());
            pages.add(body.toString());
        }
        DiscordUtil.paginate(channel, newPage ? null : message, title, command, page, 1, pages, "", true);
    }

    private final GuildDB db;

    private final AllianceList alliance;

    public IACheckup(GuildDB db, AllianceList alliance, boolean useCache) throws IOException {
        if (db == null) throw new IllegalStateException("No database found");
        if (alliance == null || alliance.isEmpty()) throw new IllegalStateException("No alliance found");
        this.db = db;
        this.alliance = alliance;
    }

    public Map<DBKingdom, Map<AuditType, Map.Entry<Object, String>>> checkup(Consumer<DBKingdom> onEach, boolean fast) throws InterruptedException, ExecutionException, IOException {
        List<DBKingdom> nations = new ArrayList<>(alliance.getKingdoms(f -> f.getPosition().ordinal() > Rank.APPLICANT.ordinal()));
        return checkup(nations, onEach, fast);
    }

    public Map<DBKingdom, Map<AuditType, Map.Entry<Object, String>>> checkup(Collection<DBKingdom> nations, Consumer<DBKingdom> onEach, boolean fast) throws InterruptedException, ExecutionException, IOException {
        Map<DBKingdom, Map<AuditType, Map.Entry<Object, String>>> result = new LinkedHashMap<>();
        for (DBKingdom nation : nations) {
            if (nation.isVacation() || nation.getActive_m() > 10000) continue;

            if (onEach != null) onEach.accept(nation);

            Map<AuditType, Map.Entry<Object, String>> nationMap = checkup(nation, AuditType.values(), fast, fast);
            result.put(nation, nationMap);
        }
        return result;
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBKingdom nation) throws InterruptedException, ExecutionException, IOException {
        return checkup(nation, AuditType.values());
    }

    public Map<AuditType, Map.Entry<Object, String>> checkupSafe(DBKingdom nation, boolean individual, boolean fast) {
        try {
            Map<AuditType, Map.Entry<Object, String>> result = checkup(nation, individual, fast);
            return result;
        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBKingdom nation, boolean individual, boolean fast) throws InterruptedException, ExecutionException, IOException {
        return checkup(nation, AuditType.values(), individual, fast);
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBKingdom nation, AuditType[] audits) throws InterruptedException, ExecutionException, IOException {
        return checkup(nation, audits, true, false);
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBKingdom nation, AuditType[] audits, boolean individual, boolean fast) throws InterruptedException, ExecutionException, IOException {
        int days = 120;


        Map<AuditType, Map.Entry<Object, String>> results = new LinkedHashMap<>();
        for (AuditType type : audits) {
            long start2 = System.currentTimeMillis();
            audit(type, nation, results, individual, fast);
            long diff = System.currentTimeMillis() - start2;
            if (diff > 10) {
                System.out.println("remove:||Checkup Diff " + type + " | " + diff + " ms");
            }
        }

        long bitMask = 0;
        for (AuditType audit : audits) {
            Map.Entry<Object, String> result = results.get(audit);
            boolean passed = result == null || result.getValue() == null;
            bitMask |= (passed ? 1 : 0) << audit.ordinal();
        }

//        ByteBuffer lastCheckupBuffer = nation.getMeta(KingdomMeta.CHECKUPS_PASSED);
//        if (lastCheckupBuffer != null) {
//            long lastCheckup = lastCheckupBuffer.getLong();
//        }

//        nation.setMeta(KingdomMeta.CHECKUPS_PASSED, bitMask);

        results.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);

        results.keySet().stream().filter(f -> f.severity == AuditSeverity.DANGER).count();

        return results;
    }

    private void audit(AuditType type, DBKingdom nation, Map<AuditType, Map.Entry<Object, String>> results, boolean individual, boolean fast) throws InterruptedException, ExecutionException, IOException {
        if (results.containsKey(type)) {
            return;
        }
        if (type.required != null) {
            if (!results.containsKey(type.required)) {
                audit(type.required, nation, results, individual, fast);
            }
            Map.Entry<Object, String> requiredResult = results.get(type.required);
            if (requiredResult != null) {
                results.put(type, null);
                return;
            }
        }
        Map.Entry<Object, String> value = checkup(type, nation);
        results.put(type, value);
    }

    public enum AuditSeverity {
        INFO,
        WARNING,
        DANGER,
    }

    public enum AuditType {
        CHECK_RANK("\uD83E\uDD47", AuditSeverity.WARNING),
        INACTIVE("\uD83D\uDCA4", AuditSeverity.DANGER),
        FINISH_OBJECTIVES("\uD83D\uDC69\u200D\uD83C\uDFEB", AuditSeverity.WARNING),
        FIX_COLOR(FINISH_OBJECTIVES, "\uD83C\uDFA8", AuditSeverity.WARNING),
        CHANGE_CONTINENT(FINISH_OBJECTIVES, "\uD83C\uDF0D", AuditSeverity.WARNING),
        FIX_WAR_POLICY(FINISH_OBJECTIVES, "\uD83D\uDCDC", AuditSeverity.WARNING),
        RAID(FINISH_OBJECTIVES, "\uD83D\uDD2A", AuditSeverity.WARNING),
        UNUSED_MAP(RAID, "\uD83D\uDE34", AuditSeverity.WARNING),
        BARRACKS(UNUSED_MAP, "\uD83C\uDFD5", AuditSeverity.WARNING),
        INCORRECT_MMR(UNUSED_MAP, "\uD83C\uDFE2", AuditSeverity.WARNING),
        BUY_SOLDIERS(BARRACKS, "\uD83D\uDC82", AuditSeverity.WARNING),
        BUY_HANGARS(BUY_SOLDIERS, "\u2708\uFE0F", AuditSeverity.WARNING),
        BUY_PLANES(BUY_HANGARS, "\u2708\uFE0F", AuditSeverity.WARNING),
        BUY_SHIPS(BUY_PLANES, "\uD83D\uDEA2", AuditSeverity.WARNING),
        BEIGE_LOOT(BUY_SOLDIERS, "\uD83D\uDC82", AuditSeverity.INFO),
        RAID_TURN_CHANGE(BEIGE_LOOT, "\uD83C\uDFAF", AuditSeverity.INFO),
        BUY_SPIES(FINISH_OBJECTIVES, "\uD83D\uDD75", AuditSeverity.WARNING),
        GATHER_INTEL(BUY_SPIES, "\uD83D\uDD0E", AuditSeverity.INFO),
        SPY_COMMAND(GATHER_INTEL, "\uD83D\uDCE1", AuditSeverity.INFO),
        LOOT_COMMAND(SPY_COMMAND, "", AuditSeverity.INFO),
        DAILY_SPYOPS(LOOT_COMMAND, "\uD83D\uDEF0", AuditSeverity.INFO),
        DEPOSIT_RESOURCES("\uD83C\uDFE6", AuditSeverity.WARNING),
        CHECK_DEPOSITS(DEPOSIT_RESOURCES, "", AuditSeverity.INFO),
        WITHDRAW_DEPOSITS(CHECK_DEPOSITS, "\uD83C\uDFE7", AuditSeverity.INFO),
        OBTAIN_RESOURCES(WITHDRAW_DEPOSITS, "\uD83C\uDFE7", AuditSeverity.DANGER),
        SAFEKEEP(OBTAIN_RESOURCES, "\uD83C\uDFE6", AuditSeverity.WARNING),
        OBTAIN_WARCHEST(OBTAIN_RESOURCES, "\uD83C\uDFE7", AuditSeverity.INFO),
        BUY_CITY(FINISH_OBJECTIVES, "\uD83C\uDFD9", AuditSeverity.INFO),
        BUY_PROJECT(FINISH_OBJECTIVES, "\uD83D\uDE80", AuditSeverity.INFO),
        BUY_RESOURCE_PRODUCTION_CENTER(BUY_PROJECT, "\uD83D\uDE80", AuditSeverity.INFO),
        BUY_INFRA(FINISH_OBJECTIVES, "\uD83C\uDFD7", AuditSeverity.INFO),
        BUY_LAND(FINISH_OBJECTIVES, "\uD83C\uDFDE", AuditSeverity.INFO),
        UNPOWERED(FINISH_OBJECTIVES, "\uD83D\uDD0C", AuditSeverity.DANGER),
        OVERPOWERED(UNPOWERED, "\u26A1", AuditSeverity.DANGER),
        NOT_NUCLEAR(OVERPOWERED, "\u2622", AuditSeverity.WARNING),
        FREE_SLOTS(NOT_NUCLEAR, "\uD83D\uDEA7", AuditSeverity.DANGER),
        NEGATIVE_REVENUE(FREE_SLOTS, "\uD83E\uDD7A", AuditSeverity.DANGER),
        MISSING_PRODUCTION_BONUS(FREE_SLOTS, "\uD83D\uDCC8", AuditSeverity.WARNING),
        EXCESS_HOSPITAL(MISSING_PRODUCTION_BONUS, "\uD83C\uDFE5", AuditSeverity.WARNING),
        EXCESS_POLICE(EXCESS_HOSPITAL, "\uD83D\uDE93", AuditSeverity.WARNING),
        EXCESS_RECYCLING(EXCESS_POLICE, "\u267B", AuditSeverity.WARNING),
        GENERATE_CITY_BUILDS(MISSING_PRODUCTION_BONUS, "", AuditSeverity.INFO),
        ROI(GENERATE_CITY_BUILDS, "", AuditSeverity.INFO),
        BLOCKADED("\uD83D\uDEA2", AuditSeverity.INFO),
//        LOSE_A_WAR(RAID_TURN_CHANGE, "", AuditSeverity.INFO),
//        PLAN_A_RAID_WITH_FRIENDS(LOSE_A_WAR, "", AuditSeverity.INFO),
//        CREATE_A_WAR_ROOM(PLAN_A_RAID_WITH_FRIENDS, "", AuditSeverity.INFO),
        ;

        public final AuditType required;
        public final String emoji;
        public final AuditSeverity severity;

        AuditType(String emoji) {
            this(null, emoji);
        }

        AuditType(String emoji, AuditSeverity severity) {
            this(null, emoji, severity);
        }

        AuditType(AuditType required, String emoji) {
            this(required, emoji, AuditSeverity.INFO);
        }

        AuditType(AuditType required, String emoji, AuditSeverity severity) {
            this.required = required;
            this.emoji = emoji;
            this.severity = severity;
        }
    }

    private Map.Entry<Object, String> checkup(AuditType type, DBKingdom nation) throws InterruptedException, ExecutionException, IOException {
        boolean updateKingdom = false;

        switch (type) {
            case CHECK_RANK: {
                Set<Integer> aaIds = db.getAllianceIds();
                if (aaIds.isEmpty()) return null;

                if (!aaIds.contains(nation.getAlliance_id())) {
                    int id = aaIds.iterator().next();
                    return new AbstractMap.SimpleEntry<>("APPLY", "Please apply to the alliance ingame: https://politicsandwar.com/alliance/join/id=" + id);
                }
                if (nation.getPosition().ordinal() <= 1) {
                    return new AbstractMap.SimpleEntry<>("MEMBER", "Please discuss with your mentor about becoming a member");
                }
                return null;
            }
            case INACTIVE:
                return testIfCacheFails(() -> checkInactive(nation), updateKingdom);
        }
        throw new IllegalArgumentException("Unsupported: " + type);
    }

    private Map.Entry<Object, String> testIfCacheFails(Supplier<Map.Entry<Object, String>> supplier, boolean test) {
        return supplier.get();
    }

    private Map.Entry<Object, String> checkInactive(DBKingdom nation) {
        long daysInactive = TimeUnit.MINUTES.toDays(nation.getActive_m());
        if (daysInactive > 1) {
            String message = "Hasn't logged in for " + daysInactive + " days.";
            return new AbstractMap.SimpleEntry<>(daysInactive, message);
        }
        return null;
    }
}
