package link.locutus.core.command;

import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.RowData;
import link.locutus.Trocutus;
import link.locutus.command.binding.Key;
import link.locutus.command.binding.LocalValueStore;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Range;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.Timestamp;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.command.impl.discord.permission.WhitelistPermission;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.AttackOrSpellType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.Activity;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomAttribute;
import link.locutus.core.db.entities.kingdom.KingdomList;
import link.locutus.core.db.entities.kingdom.KingdomPlaceholders;
import link.locutus.core.db.entities.kingdom.SimpleKingdomList;
import link.locutus.core.db.entities.spells.BPManaEntry;
import link.locutus.core.db.entities.spells.SpellOp;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.SheetKeys;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.util.ArrayUtil;
import link.locutus.util.CounterGenerator;
import link.locutus.util.MarkupUtil;
import link.locutus.util.MathMan;
import link.locutus.util.SpyBlitzGenerator;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.TrounceUtil;
import link.locutus.util.builder.RankBuilder;
import link.locutus.util.spreadsheet.SheetUtil;
import link.locutus.util.spreadsheet.SpreadSheet;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.ClassUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SheetCommands {
    @RolePermission(value = {Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS}, any = true)
    @Command(desc = "Create a google sheet of nations, grouped by alliance, with the specified columns\n" +
            "Prefix a column with `avg:` to force an average\n" +
            "Prefix a column with `total:` to force a total")
    public String allianceKingdomsSheet(KingdomPlaceholders placeholders, ValueStore store, @Me IMessageIO channel, @Me User author, @Me Guild guild, @Me GuildDB db,
                                       Set<DBKingdom> nations,
                                       @Arg("The columns to have. See: <https://github.com/xdnw/trocutus/wiki/Kingdom-Filters>") List<String> columns,
                                       @Switch("s") SpreadSheet sheet,
                                       @Arg("Use the sum of each nation's attributes instead of the average")
                                       @Switch("t") boolean useTotal, @Switch("i") boolean includeInactives, @Switch("a") boolean includeApplicants) throws IOException, GeneralSecurityException, IllegalAccessException, InvocationTargetException {
        nations.removeIf(f -> f.isVacation() || (!includeApplicants && f.getPosition().ordinal() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        Map<Integer, Set<DBKingdom>> natByAA = new HashMap<>();
        for (DBKingdom nation : nations) {
            natByAA.computeIfAbsent(nation.getAlliance_id(), f -> new LinkedHashSet<>()).add(nation);
        }
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.ALLIANCES_SHEET);
        }
        List<String> header = (columns.stream().map(f -> f.replace("{", "").replace("}", "").replace("=", "")).collect(Collectors.toList()));
        sheet.setHeader(header);

        // pattern compile for {some string here}
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        // matcher

        for (Map.Entry<Integer, Set<DBKingdom>> entry : natByAA.entrySet()) {
            Integer aaId = entry.getKey();

            DBAlliance alliance = DBAlliance.get(aaId);
            if (alliance == null) continue;

            SimpleKingdomList list = new SimpleKingdomList(entry.getValue());

            for (int i = 0; i < columns.size(); i++) {
                String arg = columns.get(i);
                if (arg.equalsIgnoreCase("{nations}")) {
                    arg = list.getKingdoms().size() + "";
                } else if (arg.equalsIgnoreCase("{alliance}")) {
                    arg = alliance.getName() + "";
                } else {
                    if (arg.contains("{") && arg.contains("}")) {
                        for (Method method : DBAlliance.class.getDeclaredMethods()) {
                            if (method.getParameters().length != 0) continue;
                            Class type = method.getReturnType();
                            if (type == String.class || ClassUtils.isPrimitiveOrWrapper(type)) {
                                String placeholder = "{" + method.getName().toLowerCase() + "}";
                                if (arg.contains(placeholder)) {
                                    method.setAccessible(true);
                                    arg = arg.replace(placeholder, method.invoke(alliance) + "");
                                }
                            }
                        }
                    }
                    if (arg.contains("{") && arg.contains("}")) {
                        // matcher
                        Map<String, String> replacements = new HashMap<>();
                        Matcher m = pattern.matcher(arg);
                        // loop over matcher matches
                        while (m.find()) {
                            String match = m.group();
                            // substring to remove { and }

                            String inner = match.substring(1, match.length() - 1);
                            boolean average = false;
                            if (inner.startsWith("avg:")) {
                                inner = inner.substring(4);
                                average = true;
                            } else if (inner.startsWith("total:")) {
                                inner = inner.substring(6);
                            }
                            KingdomAttribute attr = placeholders.getMetric(store, inner, false);
                            double totalValue = 0;
                            String otherValues = null;
                            for (DBKingdom kingdom : list.getKingdoms()) {

                                Object value = attr.apply(kingdom);
                                if (value instanceof Number num) {
                                    totalValue += num.doubleValue();
                                } else if (value != null) {
                                    otherValues = value.toString();
                                    break;
                                }
                            }
                            if (average && otherValues == null) {
                                totalValue /= list.getKingdoms().size();
                            }
                            if (otherValues != null) {
                                replacements.put(match, otherValues);
                            } else {
                                replacements.put(match, totalValue + "");
                            }
                        }
                        // replace all
                        for (Map.Entry<String, String> entry1 : replacements.entrySet()) {
                            arg = arg.replace(entry1.getKey(), entry1.getValue());
                        }
                    }
                }

                header.set(i, arg);
            }

            sheet.addRow(header);
        }


        sheet.clear("A:ZZ");
        sheet.set(0, 0);

        sheet.attach(channel.create()).send();
        return null;
    }
    @RolePermission(value = {Roles.MILCOM, Roles.ECON, Roles.INTERNAL_AFFAIRS}, any=true)
    @Command(desc = "A sheet of nations stats with customizable columns\n" +
            "See <https://github.com/xdnw/trocutus/wiki/Kingdom-Filters> for a list of placeholders")
    public static void KingdomSheet(ValueStore store, KingdomPlaceholders placeholders, @Me IMessageIO channel, @Me GuildDB db, Set<DBKingdom> nations,
                                   @Arg("A space separated list of columns to use in the sheet\n" +
                                           "Can include KingdomAttribute as placeholders in columns\n" +
                                           "All KingdomAttribute placeholders must be surrounded by {} e.g. {nation}")
                                   List<String> columns,
                                   @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.NATION_SHEET);
        }
        List<String> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        sheet.setHeader(header);

        LocalValueStore locals = new LocalValueStore(store);
        for (DBKingdom nation : nations) {
            locals.addProvider(Key.of(DBKingdom.class, Me.class), nation);
            locals.addProvider(Key.of(User.class, Me.class), nation.getUser());
            for (int i = 0; i < columns.size(); i++) {
                String arg = columns.get(i);
                String formatted = placeholders.format(locals, arg);

                header.set(i, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.clear("A:ZZ");
        sheet.set(0, 0);

        sheet.attach(channel.create()).send();
    }

    @Command(desc = "Generate a list of raidable targets to gather intel on\n" +
            "`<time>`- filters out nations we have loot intel on in that period\n" +
            "`<attackers>`- The nations to assign to do the ops (i.e. your alliance link)\n" +
            "`<ignore-topX>`- filter out top X alliances (e.g. due to DNR), in addition to the set `dnr` coalition\n\n" +
            "Add `-l` to remove targets with loot history\n" +
            "Add `-d` to list targets currently on the dnr\n\n" +
            "e.g. `{prefix}sheets_milcom intelopsheet time:10d attacker:Rose dnrtopx:25`")
    @RolePermission(Roles.MILCOM)
    public String IntelOpSheet(@Me IMessageIO io, @Me GuildDB db, @Timestamp long time, Set<DBKingdom> attackers,
                               @Arg("Exclude nations in the top X alliances (or direct allies)")
                               @Default() Integer dnrTopX,
                               @Arg("If nations with loot history are ignored")
                               @Switch("l") boolean ignoreWithLootHistory,
                               @Arg("If the alliance Do Not Raid settings are checked")
                               @Switch("d") boolean ignoreDNR,
                               @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {

        Function<DBKingdom, Integer> numOpsFunc = f -> 1; // TODO

        if (ignoreWithLootHistory) time = 0;
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.SPYOP_SHEET);
        }
        int maxOps = 2;

        attackers.removeIf(f -> f.getPosition().ordinal() <= 1 || f.getActive_m() > 1440 || f.isVacation() == true);
        if (dnrTopX == null) dnrTopX = db.getOrNull(GuildKey.DO_NOT_RAID_TOP_X);
        if (dnrTopX == null) dnrTopX = 0;

        List<DBKingdom> enemies = new ArrayList<>(Trocutus.imp().getDB().getKingdomsMatching(f -> true));


        Set<Integer> allies = db.getAllies(true);
        if (!ignoreDNR) {
            Function<DBKingdom, Boolean> canRaid = db.getCanRaid(dnrTopX, true);
            enemies.removeIf(f -> !canRaid.apply(f));
        }
        enemies.removeIf(f -> allies.contains(f.getAlliance_id()));
        enemies.removeIf(f -> f.getActive_m() < 4320);
        enemies.removeIf(f -> f.isVacation() == true);

        Map<DBKingdom, Double> opValueMap = new HashMap<>();

        Iterator<DBKingdom> iter = enemies.iterator();
        while (iter.hasNext()) {
            DBKingdom nation = iter.next();
            Map.Entry<Double, Boolean> opValue = nation.getIntelOpValue(time);
            if (opValue == null) {
                iter.remove();
                continue;
            }
            opValueMap.put(nation, opValue.getKey());
        }

        Collections.sort(enemies, new Comparator<DBKingdom>() {
            @Override
            public int compare(DBKingdom o1, DBKingdom o2) {
                double revenueTime1 = opValueMap.get(o1);
                double revenueTime2 = opValueMap.get(o2);
                return Double.compare(revenueTime2, revenueTime1);
            }
        });

        enemies.addAll(new ArrayList<>(enemies));

        // nations with big trades

        Map<DBKingdom, List<SpellOp>> targets = new HashMap<>();

        ArrayList<DBKingdom> attackersList = new ArrayList<>(attackers);
        Collections.shuffle(attackersList);

        for (DBKingdom attacker : attackersList) {
            int numOps = numOpsFunc.apply(attacker);
            numOps = Math.min(numOps, maxOps);

            outer:
            for (int i = 0; i < numOps; i++) {
                iter = enemies.iterator();
                while (iter.hasNext()) {
                    DBKingdom enemy = iter.next();
                    if (!attacker.isInSpyRange(enemy)) continue;
                    List<SpellOp> currentOps = targets.computeIfAbsent(enemy, f -> new ArrayList<>());
                    if (currentOps.size() > 1) continue;
                    if (currentOps.size() == 1 && currentOps.get(0).getAttacker() == attacker) continue;
                    SpellOp op = new SpellOp(AttackOrSpellType.SPY, attacker, enemy, 0);
                    currentOps.add(op);
                    iter.remove();
                    continue outer;
                }
                break;
            }
        }

        sheet.clearAll();
        SpyBlitzGenerator.generateSpySheet(sheet, targets);
        sheet.set(0, 0);

        sheet.attach(io.create()).send();
        return null;
    }

    @RolePermission(value = {Roles.MILCOM, Roles.INTERNAL_AFFAIRS,Roles.ECON}, any=true)
    @Command(desc = "Generate a sheet of nation login activity from a nation id over a timeframe\n" +
            "Days represent the % of that day a nation logs in (UTC)\n" +
            "Numbers represent the % of that turn a nation logs in")
    public String ActivitySheet(@Me IMessageIO io, @Me GuildDB db, Set<DBKingdom> nations,
                                @Arg("Date to start from")
                                @Default("2w") @Timestamp long trackTime, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.ACTIVITY_SHEET);
        }
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "score",
                "Mo",
                "Tu",
                "We",
                "Th",
                "Fr",
                "Sa",
                "Su"
        ));
        for (int i = 0; i < 12; i++) {
            header.add((i + 1) + "");
        }

        sheet.setHeader(header);

        for (DBKingdom nation : nations) {

            header.set(0, MarkupUtil.sheetUrl(nation.getName(), TrounceUtil.getUrl(nation.getId(), false)));
            header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), TrounceUtil.getUrl(nation.getAlliance_id(), true)));
            header.set(3, nation.getTotal_land());

            Activity activity;
            if (trackTime == 0) {
                System.out.println("Track time = 0");
                activity = nation.getActivity();
            } else {
                long diff = System.currentTimeMillis() - trackTime;
                System.out.println("Check turns " + diff + " | " + TimeUnit.MILLISECONDS.toHours(diff) / 2 + " | " + trackTime);
                activity = nation.getActivity(TimeUnit.MILLISECONDS.toHours(diff) / 2);
            }
            double[] byDay = activity.getByDay();
            double[] byDayTurn = activity.getByDayTurn();

            for (int i = 0; i < byDay.length; i++) {
                header.set(5 + i, byDay[i] * 100);
            }

            for (int i = 0; i < byDayTurn.length; i++) {
                header.set(5 + byDay.length + i, byDayTurn[i] * 100);
            }

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        sheet.attach(io.create()).send();
        return null;
    }

    @Command
    public String setBpMana(@Me Map<DBRealm, DBKingdom> me, DBRealm realm, int bp, int mana) {
        DBKingdom myKingdom = me.get(realm);
        if (myKingdom == null) {
            // register
            throw new IllegalArgumentException("You are not registered in " + realm.getName());
        }
        BPManaEntry.setBpMana(myKingdom.getId(), bp, mana);
        return "Set BATTLE POINTS to " + bp + " and MANA to " + mana + " (not: this will expire this turn)";
    }

    @Command
    @RolePermission(Roles.MILCOM)
    public String listBpMana(@Me GuildDB db, Set<DBKingdom> kingdoms) {
        StringBuilder response = new StringBuilder();
        Set<Integer> aaIds = db.getAllianceIds();

        int size = kingdoms.size();
        kingdoms.removeIf(k -> !aaIds.contains(k.getAlliance_id()));
        if (kingdoms.size() < size) {
            response.append("Only showing kingdoms in an alliance (" + (size - kingdoms.size()) + " removed)\n");
        }

        for (DBKingdom kingdom : kingdoms) {
            BPManaEntry entry = BPManaEntry.getEntry(kingdom.getId());
            if (entry == null) {
                response.append(kingdom.getName()).append(" has no entry!\n");
            } else {
                response.append(kingdom.getName()).append(" has ").append(entry.bp).append(" BP and ").append(entry.mana).append(" mana\n");
            }
        }

        return response.toString();
    }

    @RolePermission(Roles.MILCOM)
    @Command(desc = "Run checks on a spy blitz sheet.\n" +
            "Checks that all nations are in range of their spy blitz targets and that they have no more than the provided number of offensive operations.\n" +
            "Add `true` for the day-change argument to double the offensive op limit")
    public String validateSpyBlitzSheet(@Me GuildDB db, @Default SpreadSheet sheet,

                                        @Switch("u") boolean allowUpdeclares,
                                        @Switch("w") boolean allowWarAlerted,
                                        @Switch("s") boolean allowSpellAlerted,
                                        @Switch("a") @Default("1") int maxAttacks,
                                        @Switch("c") @Default("1") int maxSpellCasts,


                                        @Arg("Only allow attacking these nations")
                                        @Default("*") Set<DBKingdom> filter) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            db.getOrThrow(SheetKeys.SPYOP_SHEET);
            sheet = SpreadSheet.create(db, SheetKeys.SPYOP_SHEET);
        }
        StringBuilder response = new StringBuilder();

        Function<DBKingdom, Boolean> isValidTarget = n -> filter.contains(n);

        BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String> output = new BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String>() {
            @Override
            public void accept(Map.Entry<DBKingdom, DBKingdom> dbKingdomDBKingdomEntry, String msg) {
                response.append(msg + "\n");
            }
        };

        Function<DBKingdom, Map<MilitaryUnit, Long>> getBpMana = new Function<DBKingdom, Map<MilitaryUnit, Long>>() {
            @Override
            public Map<MilitaryUnit, Long> apply(DBKingdom kingdom) {
                Map<MilitaryUnit, Long> result = new EnumMap<>(MilitaryUnit.class);
                int bp = BPManaEntry.getBp(kingdom.getId());
                int mana = BPManaEntry.getMana(kingdom.getId());
                if (bp != 0) {
                    result.put(MilitaryUnit.BATTLE_POINTS, (long) bp);
                }
                if (mana != 0) {
                    result.put(MilitaryUnit.MANA, (long) mana);
                }
                return result;
            }
        };

        Map<DBKingdom, Set<SpellOp>> targets = SpyBlitzGenerator.getTargets(sheet, 0, output);
        SpyBlitzGenerator.validateTargets(
                targets,
                getBpMana,
                0.5,
                1.5,
                !allowUpdeclares,
                !allowWarAlerted,
                !allowSpellAlerted,
                maxAttacks,
                maxSpellCasts,
                isValidTarget,
                output

        );

        if (response.length() <= 1) return "All checks passed";

        return response.toString();
    }

    @RolePermission(Roles.MILCOM)
    @Command(desc = "Send spy or war blitz sheets to individual nations")
    public String mailTargets(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me User author, @Me IMessageIO channel, @Me Map<DBRealm, DBKingdom> me,
                              @Arg("Url of the spy sheet to send")
                              @Default SpreadSheet spySheet,
                              @Arg("What nations to send to")
                              @Default("*") Set<DBKingdom> allowedKingdoms,
                              @Arg("Text to prepend to the target instructions being sent")
                              @Default("") String header,

                              @Arg("The api key to use to send the mail") @Switch("a") String apiKey,
                              @Arg("Hide the default blurb from the message")
                              @Switch("b") boolean hideDefaultBlurb,
                              @Switch("f") boolean force,
                              @Arg("Send instructions as direct message on discord")
                              @Switch("d") boolean dm) throws IOException, GeneralSecurityException {

        Auth auth = db.getMailAuth();
        if (auth == null){
            return "No api key found. Please use" + "CM.credentials.login.cmd.toSlashMention()";
        }

        if (header != null && !header.isEmpty() && !Roles.MAIL.has(author, guild)) {
            return "You need the MAIL role on discord (see " + "CM.role.setAlias.cmd.toSlashMention()" + ") to add the custom message: `" + header + "`";
        }
        Map<DBKingdom, Set<DBKingdom>> warDefAttMap = new HashMap<>();
        Map<DBKingdom, Set<DBKingdom>> spyDefAttMap = new HashMap<>();
        Map<DBKingdom, Set<SpellOp>> spyOps = new HashMap<>();

        if (dm && !Roles.MAIL.hasOnRoot(author)) return "You do not have permission to dm users";

        StringBuilder errors = new StringBuilder();
        BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String> invalidOut = new BiConsumer<Map.Entry<DBKingdom, DBKingdom>, String>() {
            @Override
            public void accept(Map.Entry<DBKingdom, DBKingdom> entry, String s) {
                errors.append(s + "\n");
            }
        };

        if (spySheet != null) {
            try {
                spyOps = SpyBlitzGenerator.getTargets(spySheet, 0, invalidOut);
            } catch (NullPointerException e) {
                spyOps = SpyBlitzGenerator.getTargets(spySheet, 4, invalidOut);
            }
        }

        Map<DBKingdom, Set<DBKingdom>> warAttDefMap = SpyBlitzGenerator.reverse(warDefAttMap);
        Map<DBKingdom, Set<DBKingdom>> spyAttDefMap = SpyBlitzGenerator.reverse(spyDefAttMap);
        Set<DBKingdom> allAttackers = new LinkedHashSet<>();
        allAttackers.addAll(warAttDefMap.keySet());
        allAttackers.addAll(spyAttDefMap.keySet());

        String date = TimeUtil.YYYY_MM_DD.format(ZonedDateTime.now());
        String subject = "Targets-" + date + "/" + channel.getIdLong();

        String blurb = "BE ACTIVE ON DISCORD. Additional attack instructions may be in your war room\n" +
                "\n" +
                "This is an alliance war, not a counter. The goal is battlefield control:\n" +
                "1. Try to raid wars just before turn change\n" +
                "2. Avoid attacking enemies stronger than you\n" +
                "3. Avoid attacking enemies that are alerted (wait for it to tick down)";

        long start = System.currentTimeMillis();

        Map<DBKingdom, Map.Entry<String, String>> mailTargets = new HashMap<>();
        int totalSpyTargets = 0;
        int totalWarTargets = 0;

        int sent = 0;
        for (DBKingdom attacker : allAttackers) {
            if (!allowedKingdoms.contains(attacker)) continue;

            List<DBKingdom> myAttackOps = new ArrayList<>(warAttDefMap.getOrDefault(attacker, Collections.emptySet()));
            List<SpellOp> mySpyOps = new ArrayList<>(spyOps.getOrDefault(attacker, Collections.emptySet()));
            if (myAttackOps.isEmpty() && mySpyOps.isEmpty()) continue;

            sent++;

            StringBuilder mail = new StringBuilder();
            mail.append(header).append("\n");

            if (!myAttackOps.isEmpty()) {
                if (!hideDefaultBlurb) {
                    mail.append(blurb + "\n");
                }
                mail.append("\n");

                mail.append("Your nation:\n");
                mail.append(getStrengthInfo(attacker) + "\n");
                mail.append("\n");

                for (int i = 0; i < myAttackOps.size(); i++) {
                    totalWarTargets++;
                    DBKingdom defender = myAttackOps.get(i);
                    mail.append((i + 1) + ". War Target: " + MarkupUtil.htmlUrl(defender.getName(), defender.getUrl(attacker.getSlug())) + "\n");
                    mail.append(getStrengthInfo(defender) + "\n"); // todo

                    Set<DBKingdom> others = new LinkedHashSet<>(warDefAttMap.get(defender));
                    others.remove(attacker);
                    if (!others.isEmpty()) {
                        Set<String> allies = new LinkedHashSet<>();
                        for (DBKingdom other : others) {
                            allies.add(other.getName());
                        }
                        mail.append("Joining you: " + StringMan.join(allies, ",") + "\n");
                    }
                    mail.append("\n");
                }
            }

            if (!mySpyOps.isEmpty()) {
                int intelOps = 0;
                int spelOps = 0;
                int attackops = 0;
                Map<MilitaryUnit, Long> cost = new HashMap<>();

                for (SpellOp op : mySpyOps) {
                    if (op.getType() == AttackOrSpellType.SPY) intelOps++;
                    else if (op.getType() == AttackOrSpellType.ATTACK) attackops++;
                    else spelOps++;

                    cost = ArrayUtil.add(cost, op.getType().getDefaultCost());
                }

                mail.append("\n");
                mail.append("Espionage targets: (costs: " + TrounceUtil.resourcesToString(cost) + ")\n");

                if (intelOps == 0) {
                    mail.append("- These are NOT gather intelligence ops.\n");
                }

                if (intelOps != myAttackOps.size()) {
                    mail.append("- Results may be outdated when you read it, so check they still have units to spell!\n");
                }


                for (int i = 0; i < mySpyOps.size(); i++) {
                    totalSpyTargets++;
                    SpellOp spyop = mySpyOps.get(i);

                    String name = spyop.getTarget().getName() + " | " + spyop.getTarget().getAllianceName();
                    String nationUrl = MarkupUtil.htmlUrl(name, spyop.getTarget().getUrl(attacker.getSlug()));

                    mail.append((i + 1) + ". " + nationUrl + " | ");
                    mail.append(spyop.getType());

                    mail.append("\n");
                }
            }

            String body = mail.toString().replace("\n","<br>");

            mailTargets.put(attacker, new AbstractMap.SimpleEntry<>(subject, body));
        }

        if (!force) {
            String title = totalWarTargets + " wars & " + totalSpyTargets + " spyops";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBKingdom nation : mailTargets.keySet()) alliances.add(nation.getAlliance_id());
            String embedTitle = title + " to " + mailTargets.size() + " nations";
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

            StringBuilder body = new StringBuilder();
            body.append("subject: " + subject + "\n");

            String confirmCommand = command.put("force", "true").toString();

            channel.create().confirmation(embedTitle, body.toString(), command)
                    .append(author.getAsMention())
                    .send();
            return null;
        }

        Map<DBKingdom, String> mailErrors = new LinkedHashMap<>();
        Map<DBKingdom, String> dmErrors = new LinkedHashMap<>();
        CompletableFuture<IMessageBuilder> msgFuture = channel.send("Sending messages...");
        for (Map.Entry<DBKingdom, Map.Entry<String, String>> entry : mailTargets.entrySet()) {
            DBKingdom attacker = entry.getKey();
            subject = entry.getValue().getKey();
            String body = entry.getValue().getValue();

            try {
                DBKingdom sender = auth.getKingdom(attacker.getRealm_id());
                if (sender == null) {
                    throw new IllegalStateException("Could not find sender for realm " + attacker.getRealm_id());
                }
                auth.sendMail(sender.getId(), attacker, subject, body);
            } catch (Throwable e) {
                mailErrors.put(attacker, (e.getMessage() + " ").split("\n")[0]);
                continue;
            }
            if (dm) {
                String markup = MarkupUtil.htmlToMarkdown(body);
                try {
                    attacker.sendDM("**" + subject + "**:\n" + markup);
                } catch (Throwable e) {
                    dmErrors.put(attacker, (e.getMessage() + " ").split("\n")[0]);
                }
            }

            if (System.currentTimeMillis() - start > 10000) {
                start = System.currentTimeMillis();
                if (msgFuture != null) {
                    IMessageBuilder tmp = msgFuture.getNow(null);
                    if (tmp != null) msgFuture = tmp.clear().append("Sending to " + attacker.getName()).send();
                }
            }
        }

        StringBuilder errorMsg = new StringBuilder();
        if (!mailErrors.isEmpty()) {
            errorMsg.append("Mail errors: ");
            errorMsg.append(
                    mailErrors.keySet()
                            .stream()
                            .map(f -> f.getId() + "")
                            .collect(Collectors.joining(","))
            );
            for (Map.Entry<DBKingdom, String> entry : mailErrors.entrySet()) {
                errorMsg.append("- " + entry.getKey().getId() + ": " + entry.getValue() + "\n");
            }
        }

        if (!dmErrors.isEmpty()) {
            errorMsg.append("DM errors: ");
            errorMsg.append(
                    dmErrors.keySet()
                            .stream()
                            .map(f -> f.getId() + "")
                            .collect(Collectors.joining(","))
            );
            for (Map.Entry<DBKingdom, String> entry : dmErrors.entrySet()) {
                errorMsg.append("- " + entry.getKey().getId() + ": " + entry.getValue() + "\n");
            }
        }

        IMessageBuilder msg = channel.create();
        if (!errorMsg.isEmpty()) {
            msg = msg.file("Errors.txt", errorMsg.toString());
        }
        msg.append("Done, sent " + sent + " messages").send();
        if (!errors.isEmpty()) {
            msg.append("\n\n**Errors:**\n>>> " + errors);
        }
        return null;
    }

    private String getStrengthInfo(DBKingdom nation) {
        String msg = "Attack:" + (int) nation.getAttackStrength() + ", Defense: " + nation.getDefenseStrength() + ", Land:" + nation.getTotal_land();

        if (nation.getActive_m() > 10000) msg += " (inactive)";

        return msg;
    }

    private String getAttackerNote(DBKingdom nation) {
        StringBuilder note = new StringBuilder();

        double score = nation.getScore();
        double minScore = Math.ceil(nation.getScore() * 0.5);
        double maxScore = Math.floor(nation.getScore() * 1.5);
        note.append("War Range: " + MathMan.format(minScore) + "-" + MathMan.format(maxScore) + " (" + score + ")").append("\n");
        note.append("ID: " + nation.getId()).append("\n");
        note.append("Alliance: " + nation.getAllianceName()).append("\n");
        note.append("Score: " + nation.getScore()).append("\n");
        note.append("Attack: " + nation.getAttackStrength()).append("\n");
        note.append("Defense: " + nation.getDefenseStrength()).append("\n");
        note.append("Abundance: " + nation.getResource_level()).append("\n");
        note.append("Alert: " + nation.getAlert_level()).append("\n");
        note.append("SpellAlert: " + nation.getSpell_alert()).append("\n");
        return note.toString();
    }

    @RolePermission(value = Roles.MILCOM)
    @Command(desc="Get a list of nations to counter an enemy\n" +
            "Add `-o` to ignore nations with 5 offensive slots\n" +
            "Add `-w` to filter out weak attackers\n" +
            "Add `-a` to only list active nations (past hour)")
    public String counter(@Me Map<DBRealm, DBKingdom> me, @Me GuildDB db, DBKingdom target,
                          @Arg("Kingdoms to counter with\n" +
                                  "Default: This guild's alliance nations")
                          @Default Set<DBKingdom> counterWith,
                          @Arg("Allow countering nations weaker than the enemy")
                          @Switch("w") boolean allowWeak,
                          @Arg("Remove countering nations that are inactive (2 days)")
                          @Switch("a") boolean onlyActive,
                          @Arg("Remove countering nations NOT registered with Trocutus")
                          @Switch("d") boolean requireDiscord,
                          @Arg("Include the discord mention of countering nations")
                          @Switch("p") boolean ping,
                          @Arg("Include counters from the same alliance as the defender")
                          @Switch("s") boolean allowSameAlliance) {
        if (counterWith == null) {
            Set<Integer> aaIds = db.getAllianceIds();
            if (aaIds.isEmpty()) {
                Set<Integer> allies = db.getAllies(true);
                if (allies.isEmpty()) {
                    return "No alliance or allies are set.\n" + GuildKey.ALLIANCE_ID.getCommandMention() + "\nOR\n " + "CM.coalition.create.cmd.create(null, Coalition.ALLIES.name())" + "";
                } else {
                    counterWith = new HashSet<>(Trocutus.imp().getDB().getKingdomsMatching(f -> allies.contains(f.getAlliance_id())));
                }
            } else {
                counterWith = new HashSet<>(Trocutus.imp().getDB().getKingdomsMatching(f -> aaIds.contains(f.getAlliance_id())));
            }
        }
        counterWith.removeIf(f -> f.isVacation() == true || f.getActive_m() > 10000 || f.getPosition().ordinal() <= Rank.APPLICANT.ordinal());
        if (requireDiscord) counterWith.removeIf(f -> f.getUser() == null);

        double score = target.getScore();
        double scoreMin = score / 1.5;
        double scoreMax = score / 0.5;

        if (onlyActive) counterWith.removeIf(f -> {
            if (f.getActive_m() < 60) return false;
            User user = f.getUser();
            if (user == null) return true;
            for (Guild mutualGuild : user.getMutualGuilds()) {
                Member member = mutualGuild.getMember(user);
                if (member != null) {
                    return switch (member.getOnlineStatus()) {
                        case ONLINE -> false;
                        case IDLE -> true;
                        case DO_NOT_DISTURB -> true;
                        case INVISIBLE -> true;
                        case OFFLINE -> true;
                        case UNKNOWN -> true;
                        default -> true;
                    };
                }
            }
            return true;
        });
        counterWith.removeIf(nation -> nation.getScore() < scoreMin || nation.getScore() > scoreMax);
        counterWith.removeIf(nation -> nation.getAlliance_id() == 0);
        counterWith.removeIf(nation -> nation.getActive_m() > TimeUnit.DAYS.toMinutes(2));
        counterWith.removeIf(nation -> nation.isVacation());
        if (!allowWeak) counterWith.removeIf(f -> f.getAttackStrength() < f.getDefenseStrength());
        Set<Integer> counterWithAlliances = counterWith.stream().map(DBKingdom::getAlliance_id).collect(Collectors.toSet());
        if (counterWithAlliances.size() == 1 && !allowSameAlliance && counterWithAlliances.contains(target.getAlliance_id())) {
            return "Please enable `-s allowSameAlliance` to counter with the same alliance";
        }
        if (!allowSameAlliance) counterWith.removeIf(nation -> nation.getAlliance_id() == target.getAlliance_id());

        List<DBKingdom> attackersSorted = new ArrayList<>(counterWith);
        attackersSorted = CounterGenerator.generateCounters(db, target, attackersSorted);

        if (attackersSorted.isEmpty()) {
            return "No nations available to counter";
        }

        StringBuilder response = new StringBuilder();
        response.append("**Enemy: **").append(target.toMarkdown(null, false)).append("\n**Counters**\n");

        int count = 0;
        int maxResults = 25;
        for (DBKingdom nation : attackersSorted) {
            if (count++ == maxResults) break;

            String statusStr = "";

            User user = nation.getUser();
            if (user != null) {
                List<Guild> mutual = user.getMutualGuilds();
                if (!mutual.isEmpty()) {
                    Guild guild = mutual.get(0);
                    Member member = guild.getMember(user);
                    if (member != null) {
                        OnlineStatus status = member.getOnlineStatus();
                        if (status != OnlineStatus.OFFLINE && status != OnlineStatus.UNKNOWN) {
                            statusStr = status.name() + " | ";
                        }
                    }
                }
            }
            if (user != null) {
                response.append(statusStr);
                response.append(user.getName() + " / ");
                if (ping) response.append(user.getAsMention());
                else response.append("`" + user.getAsMention() + "` ");
            }
            response.append(nation.toMarkdown(null, false)).append('\n');
        }

        return response.toString();
    }
}