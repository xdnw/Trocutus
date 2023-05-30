package link.locutus.core.db.guild.role;

import link.locutus.Trocutus;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.command.CM;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.AutoNickOption;
import link.locutus.core.db.guild.entities.AutoRoleOption;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.util.CIEDE2000;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MathMan;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.builder.RankBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;

import java.awt.Color;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AutoRoleTask implements IAutoRoleTask {
    private final Guild guild;
    private int position;

    private Map<Integer, Role> allianceRoles;

    private Role registeredRole;
    private final GuildDB db;
    private AutoNickOption setNickname;
    private AutoRoleOption setAllianceMask;

    private boolean autoRoleAllyGov = false;

    private Function<Integer, Boolean> allowedAAs = f -> true;
    private Rank autoRoleRank;

    public AutoRoleTask(Guild guild, GuildDB db) {
        this.guild = guild;
        this.db = db;
        this.allianceRoles = new HashMap<>();
        this.position = -1;
        syncDB();
    }

    public void setAllianceMask(AutoRoleOption value) {
        this.setAllianceMask = value == null ? AutoRoleOption.FALSE : value;
    }

    public void setNickname(AutoNickOption value) {
        this.setNickname = value == null ? AutoNickOption.FALSE : value;
    }

    public synchronized void syncDB() {
        AutoNickOption nickOpt = db.getOrNull(GuildKey.AUTONICK);
        if (nickOpt != null) {
            setNickname(nickOpt);
        }

        AutoRoleOption roleOpt = db.getOrNull(GuildKey.AUTOROLE);
        if (roleOpt != null) {
            try {
                setAllianceMask(roleOpt);
            } catch (IllegalArgumentException e) {}
        }
        initRegisteredRole = false;
        List<Role> roles = guild.getRoles();
        this.allianceRoles = new ConcurrentHashMap<>(DiscordUtil.getAARoles(roles));

        fetchTaxRoles(true);

        this.autoRoleAllyGov = Boolean.TRUE == db.getOrNull(GuildKey.AUTOROLE_ALLY_GOV);

        this.autoRoleRank = db.getOrNull(GuildKey.AUTOROLE_ALLIANCE_RANK);
        if (this.autoRoleRank == null) this.autoRoleRank = Rank.MEMBER;
        allowedAAs = null;
        Integer topX = db.getOrNull(GuildKey.AUTOROLE_TOP_X);
        if (topX != null) {
            Map<Integer, Double> aas = new RankBuilder<>(Trocutus.imp().getDB().getKingdomsMatching(f -> true)).group(DBKingdom::getAlliance_id).sumValues(f -> (double) f.getScore()).sort().get();
            List<Integer> topAAIds = new ArrayList<>(aas.keySet());
            if (topAAIds.size() > topX) {
                topAAIds = topAAIds.subList(0, topX);
            }
            Set<Integer> topAAIdSet = new HashSet<>(topAAIds);
            topAAIdSet.remove(0);
            allowedAAs = f -> topAAIdSet.contains(f);
        }
        if (setAllianceMask == AutoRoleOption.ALLIES) {
            Set<Integer> allies = new HashSet<>(db.getAllies(true));

            if (allowedAAs == null) allowedAAs = f -> allies.contains(f);
            else {
                Function<Integer, Boolean> previousAllowed = allowedAAs;
                allowedAAs = f -> previousAllowed.apply(f) || allies.contains(f);
            }
        } else if (allowedAAs == null) {
            allowedAAs = f -> true;
        }
        Set<Integer> masked = db.getCoalition(Coalition.MASKED_ALLIANCES);
        if (!masked.isEmpty()) {
            Function<Integer, Boolean> previousAllowed = allowedAAs;
            allowedAAs = f -> previousAllowed.apply(f) || masked.contains(f);
        }
    }

    public void autoRoleAllies(Consumer<String> output, Set<Member> members) {
        if (output == null) output = f -> {};
        if (!members.isEmpty()) {
            Role memberRole = Roles.MEMBER.toRole(guild);

            Set<Roles> maskRolesSet = db.getOrNull(GuildKey.AUTOROLE_ALLY_ROLES);

            Roles[] maskRoles = {Roles.MILCOM, Roles.ECON, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS};
            if (maskRolesSet != null) {
                maskRoles = maskRolesSet.toArray(new Roles[0]);
            }
            Role[] thisDiscordRoles = new Role[maskRoles.length];
            boolean thisHasRoles = false;
            for (int i = 0; i < maskRoles.length; i++) {
                thisDiscordRoles[i] = maskRoles[i].toRole(guild);
                thisHasRoles |= thisDiscordRoles[i] != null;
            }

            Set<Integer> allies = db.getAllies(true);
            if (allies.isEmpty()) return;

            List<Future<?>> tasks = new ArrayList<>();

            if (thisHasRoles) {
                Map<Member, List<Role>> memberRoles = new HashMap<>();

                for (Integer ally : allies) {
                    GuildDB guildDb = Trocutus.imp().getGuildDBByAA(ally);
                    if (guildDb == null) {
                        continue;
                    }
                    Guild allyGuild = guildDb.getGuild();
                    if (allyGuild == null) {
                        continue;
                    }
                    Role[] allyDiscordRoles = new Role[maskRoles.length];

                    boolean hasRole = false;
                    for (int i = 0; i < maskRoles.length; i++) {
                        Roles role = maskRoles[i];
                        Role discordRole = role.toRole(allyGuild);
                        allyDiscordRoles[i] = discordRole;
                        hasRole |= discordRole != null;
                    }

                    if (hasRole) {
                        Set<Long> otherMemberIds;
                        if (members.size() == 1) {
                            otherMemberIds = Collections.singleton(members.iterator().next().getIdLong());
                        } else {
                            otherMemberIds = allyGuild.getMembers().stream().map(ISnowflake::getIdLong).collect(Collectors.toSet());
                        }
                        for (Member member : members) {
                            if (!otherMemberIds.contains(member.getIdLong())) {
                                continue;
                            }
                            Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(member.getUser());

                            if (kingdoms.isEmpty()) {
                                continue;
                            }

                            Member allyMember = allyGuild.getMemberById(member.getIdLong());
                            if (allyMember == null) {
                                continue;
                            }

                            for (Map.Entry<DBRealm, DBKingdom> entry : kingdoms.entrySet()) {
                                DBKingdom kingdom = entry.getValue();
                                if (allies.contains(kingdom.getAlliance_id()) && kingdom.getPosition().ordinal() > Rank.APPLICANT.ordinal()) {
                                    List<Role> roles = member.getRoles();
                                    List<Role> allyRoles = allyMember.getRoles();

                                    // set member
                                    if (memberRole != null) {
                                        memberRoles.computeIfAbsent(member, f -> new ArrayList<>()).add(memberRole);
                                        if (!roles.contains(memberRole)) {
                                            roles = new ArrayList<>(roles);
                                            roles.add(memberRole);
                                            tasks.add(RateLimitUtil.queue(guild.addRoleToMember(member, memberRole)));
                                        }
                                    }
                                    for (int i = 0; i < allyDiscordRoles.length; i++) {
                                        Role allyRole = allyDiscordRoles[i];
                                        Role thisRole = thisDiscordRoles[i];
                                        if (allyRole == null || thisRole == null) {
                                            if (thisRole != null) output.accept("Role not registered " + thisRole.getName());
                                            continue;
                                        }

                                        if (allyRoles.contains(allyRole)) {
                                            memberRoles.computeIfAbsent(member, f -> new ArrayList<>()).add(thisRole);
                                            if (!roles.contains(thisRole)) {
                                                roles = new ArrayList<>(roles);
                                                roles.add(thisRole);
                                                tasks.add(RateLimitUtil.queue(guild.addRoleToMember(member, thisRole)));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for (Member member : members) {
                    List<Role> roles = new ArrayList<>(member.getRoles());

                    boolean isMember = false;
                    if (memberRole != null) {
                        Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(member.getUser());
                        if (!kingdoms.isEmpty()) {
                            for (Map.Entry<DBRealm, DBKingdom> entry : kingdoms.entrySet()) {
                                DBKingdom kingdom = entry.getValue();
                                if (allies.contains(kingdom.getAlliance_id()) && kingdom.getPosition().ordinal() > Rank.APPLICANT.ordinal()) {
                                    isMember = true;
                                    if (!roles.contains(memberRole)) {
                                        roles = new ArrayList<>(roles);
                                        roles.add(memberRole);
                                        tasks.add(RateLimitUtil.queue(guild.addRoleToMember(member, memberRole)));
                                    }
                                }
                            }
                        }
                    }
                    if (!isMember && roles.contains(memberRole)) {
                        tasks.add(RateLimitUtil.queue(guild.removeRoleFromMember(member, memberRole)));
                    }

                    List<Role> allowed = memberRoles.getOrDefault(member, new ArrayList<>());

                    for (Role role : thisDiscordRoles) {
                        if (roles.contains(role) && !allowed.contains(role)) {
                            tasks.add(RateLimitUtil.queue(guild.removeRoleFromMember(member, role)));
                        }
                    }
                }

            }
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    @Override
    public synchronized void autoRoleAll(Consumer<String> output) {
        syncDB();
        if (setNickname == AutoNickOption.FALSE && setAllianceMask == AutoRoleOption.FALSE) return;

        ArrayDeque<Future> tasks = new ArrayDeque<>();

        List<Member> members = guild.getMembers();

        Map<Integer, Role> existantAllianceRoles = new HashMap<>(allianceRoles);

        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            try {
                autoRole(member, true, output, f -> tasks.add(f));
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }
        if (autoRoleAllyGov) {
            HashSet<Member> memberSet = new HashSet<>(members);
            autoRoleAllies(output, memberSet);
        }
        while (!tasks.isEmpty()) {
            try {
                tasks.poll().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }

        for (Map.Entry<Integer, Role> entry : existantAllianceRoles.entrySet()) {
            Role role = entry.getValue();
            List<Member> withRole = guild.getMembersWithRoles(role);
            if (withRole.isEmpty()) {
                allianceRoles.remove(entry.getKey());
                tasks.add(RateLimitUtil.queue(role.delete()));
            }
        }
        while (!tasks.isEmpty()) {
            try {
                tasks.poll().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean initRegisteredRole = false;
    private Map<Long, Set<Integer>> memberAACache2 = new HashMap<>();

    @Override
    public synchronized void autoRole(Member member, Consumer<String> output) {
        autoRole(member, false, output, f -> {});
    }

    public synchronized void autoNick(boolean autoAll, Member member, Consumer<String> output, Consumer<Future> tasks, Map<DBRealm, DBKingdom> kingdomMap) {
        if (kingdomMap.isEmpty()) {
            output.accept(member.getEffectiveName() + " has the registered role, but no DB entry has been found.");
            return;
        }
        String leaderOrKingdom;
        switch (setNickname) {
            case KINGDOM:
                leaderOrKingdom = kingdomMap.values().iterator().next().getSlug();
                break;
            case DISCORD:
                leaderOrKingdom = member.getUser().getName();
                break;
            default:
                return;
        }
        boolean setName = true;

        String effective = member.getEffectiveName();
        if (autoAll && effective.contains("/")) {
            return;
        }

        String name = leaderOrKingdom;
        if (name.length() > 32) {
            if (effective.equalsIgnoreCase(leaderOrKingdom)) {
                setName = false;
            } else {
                name = leaderOrKingdom;
            }
        }
        if (setName) {
            output.accept("Set " + member.getEffectiveName() + "  to " + name);
            try {
                tasks.accept(RateLimitUtil.queue(member.modifyNickname(name)));
            } catch (HierarchyException ignore) {
                output.accept(member.getEffectiveName() + " " + ignore.getMessage());
            }
        } else if (!autoAll) {
            output.accept(member.getEffectiveName() + " already matches their ingame nation");
        }
    }

    public synchronized void autoRole(Member member, boolean autoAll, Consumer<String> output, Consumer<Future> tasks) {
        if (setNickname == AutoNickOption.FALSE && setAllianceMask == AutoRoleOption.FALSE) {
            return;
        }
        if (allianceRoles.isEmpty()) syncDB();
        {
            initRegisteredRole = true;
            this.registeredRole = Roles.REGISTERED.toRole(guild);
        }

        try {
        User user = member.getUser();
        List<Role> roles = member.getRoles();

        boolean isRegistered = registeredRole != null && roles.contains(registeredRole);

        Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(user);

        if (!isRegistered && registeredRole != null) {
            if (!kingdoms.isEmpty()) {
                RateLimitUtil.complete(guild.addRoleToMember(user, registeredRole));
                isRegistered = true;
            }
        }
        if (!isRegistered && !autoAll) {
            if (registeredRole == null) {
                output.accept("No registered role exists. Please create one on discord, then use " + CM.role.alias.set.cmd.create(Roles.REGISTERED.name(), null, null, null) + "");
            } else {
                output.accept(member.getEffectiveName() + " is NOT registered");
            }
        }

        if (!autoAll && this.autoRoleAllyGov) {
            autoRoleAllies(output, Collections.singleton(member));
        }

        if (setAllianceMask != null && setAllianceMask != AutoRoleOption.FALSE) {
            autoRoleAlliance(member, isRegistered, kingdoms, autoAll, output, tasks);
        }


        if (setNickname != null && setNickname != AutoNickOption.FALSE && member.getNickname() == null && isRegistered) {
            autoNick(autoAll, member, output, tasks, kingdoms);
        } else if (!autoAll && (setNickname == null || setNickname == AutoNickOption.FALSE)) {
            output.accept("Auto nickname is disabled");
        }

        } catch (Throwable e) {
            e.printStackTrace();
            output.accept("Failed for " + member.getEffectiveName() + ": " + e.getClass().getSimpleName() + " | " + e.getMessage());
//            e.printStackTrace();
        }
    }

    private Map<Map.Entry<Integer, Integer>, Role> taxRoles = null;

    private Map<Map.Entry<Integer, Integer>, Role> fetchTaxRoles(boolean update) {
        Map<Map.Entry<Integer, Integer>, Role> tmp = taxRoles;
        if (tmp == null || update) {
            taxRoles = tmp = new HashMap<>();
            for (Role role : guild.getRoles()) {
                String[] split = role.getName().split("/");
                if (split.length != 2 || !MathMan.isInteger(split[0]) || !MathMan.isInteger(split[1])) continue;

                int moneyRate = Integer.parseInt(split[0]);
                int rssRate = Integer.parseInt(split[1]);
                taxRoles.put(new AbstractMap.SimpleEntry<>(moneyRate, rssRate), role);
            }
        }
        return tmp;
    }

    public void autoRoleAlliance(Member member, boolean isRegistered, Map<DBRealm, DBKingdom> kingdoms, boolean autoAll, Consumer<String> output, Consumer<Future> tasks) {
        if (isRegistered) {
            if (!kingdoms.isEmpty()) {
                Set<Integer> alliancesToGrant = kingdoms.values().stream()
                        .filter(f -> f.getPosition().ordinal() >= autoRoleRank.ordinal())
                        .map(DBKingdom::getAlliance_id)
                        .filter(f -> f != 0 && allowedAAs.apply(f)).collect(Collectors.toSet());
                if (!allianceRoles.isEmpty() && position == -1) {
                    position = allianceRoles.values().iterator().next().getPosition();
                }
                Set<Integer> currentAARole = memberAACache2.get(member.getIdLong());



                if (currentAARole != null && currentAARole.equals(alliancesToGrant) && autoAll) {
                    return;
                } else {
                    memberAACache2.put(member.getIdLong(), new HashSet<>(alliancesToGrant));
                }

                Set<Integer> toRemove = new HashSet<>(currentAARole);
                toRemove.removeAll(alliancesToGrant);
                alliancesToGrant.removeAll(currentAARole);
                for (int alliance_id : toRemove) {
                    Role role = allianceRoles.get(alliance_id);
                    if (role != null) {
                        if (!autoAll)
                            output.accept("Remove " + role.getName() + " to " + member.getEffectiveName());
                        tasks.accept(RateLimitUtil.queue(guild.removeRoleFromMember(member, role)));
                    }
                }
                for (int alliance_id : alliancesToGrant) {
                    Role role = allianceRoles.get(alliance_id);
                    if (role == null) {
                        DBAlliance alliance = DBAlliance.get(alliance_id);
                        String name = alliance == null ? "Unknown" : alliance.getName();
                        role = createRole(position, guild, alliance_id, name);
                        if (role != null) {
                            allianceRoles.put(alliance_id, role);
                        }
                        if (!autoAll)
                            output.accept("Add " + role.getName() + " to " + member.getEffectiveName());
                        tasks.accept(RateLimitUtil.queue(guild.addRoleToMember(member, role)));
                    }
                }
            } else isRegistered = false;
        }
        if (!isRegistered && !autoAll) {
            Map<Integer, Role> memberAARoles = DiscordUtil.getAARoles(member.getRoles());
            if (!memberAARoles.isEmpty()) {
                for (Map.Entry<Integer, Role> entry : memberAARoles.entrySet()) {
                    output.accept("Remove " + entry.getValue().getName() + " from " + member.getEffectiveName());
                    tasks.accept(RateLimitUtil.queue(guild.removeRoleFromMember(member, entry.getValue())));
                }
            }
        }
    }

    public Role createRole(int position, Guild guild, int allianceId, String allianceName) {
        Random random = new Random(allianceId);
        Color color = null;
        double maxDiff = 0;
        for (int i = 0; i < 100; i++) {
            int nextInt = random.nextInt(0xffffff + 1);
            String colorCode = String.format("#%06x", nextInt);
            Color nextColor = Color.decode(colorCode);

            if (CIEDE2000.calculateDeltaE(BG, nextColor) < 12) continue;

            double minDiff = Double.MAX_VALUE;
            for (Role role : allianceRoles.values()) {
                Color otherColor = role.getColor();
                if (otherColor != null) {
                    minDiff = Math.min(minDiff, CIEDE2000.calculateDeltaE(nextColor, otherColor));
                }
            }
            if (minDiff > maxDiff) {
                maxDiff = minDiff;
                color = nextColor;
            }
            if (minDiff > 12) break;
        }

        String roleName = "AA " + allianceId + " " + allianceName;
        Role role = RateLimitUtil.complete(guild.createRole()
                .setName(roleName)
                .setColor(color)
                .setMentionable(false)
                .setHoisted(true)
                );

        if (role == null) {
            List<Role> roles = guild.getRolesByName(roleName, false);
            if (roles.size() == 1) role = roles.get(0);
            else {
                throw new IllegalStateException("Could not create role: " + roleName);
            }
        }

        if (position != -1) {
            RateLimitUtil.complete(guild.modifyRolePositions().selectPosition(role).moveTo(position));
        }
        return role;
    }

    private static Color BG = Color.decode("#36393E");
}
