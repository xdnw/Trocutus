package link.locutus.core.db.guild.interview;

import link.locutus.Trocutus;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.command.CM;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomMeta;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MathMan;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IACategory {
    private final GuildDB db;
    private final AllianceList alliance;
    private final Map<DBKingdom, IAChannel> channelMap = new HashMap<>();
    private final Guild guild;
    private IMessageIO output;

    private List<Category> activeCategories = new ArrayList<>();
    private List<Category> inactiveCategories = new ArrayList<>();
    private List<Category> passedCategories = new ArrayList<>();

    public Category getFreeCategory(Collection<Category> categories) {
        return DiscordUtil.findFreeCategory(categories);
    }

    public boolean isInCategory(GuildMessageChannel channel) {
        if (!(channel instanceof ICategorizableChannel)) return false;
        Category parent = ((ICategorizableChannel) channel).getParentCategory();
        for (Category category : getCategories()) {
            if (category.equals(parent)) return true;
        }
        return false;
    }


    public IACategory(GuildDB db) {
        this.db = db;
        MessageChannel outputChannel = db.getOrNull(GuildKey.INTERVIEW_INFO_SPAM);
        if (outputChannel != null) {
            this.output = new DiscordChannelIO(outputChannel);
        } else {
            this.output = null;
        }
        this.guild = db.getGuild();
        this.alliance = db.getAllianceList();
        if (this.alliance == null || this.alliance.isEmpty()) throw new IllegalArgumentException("No ALLIANCE_ID set. See: " + GuildKey.ALLIANCE_ID.getCommandMention());
        fetchChannels();
    }

    public void setOutput(GuildMessageChannel outputChannel) {
        this.output = new DiscordChannelIO(outputChannel);;
    }

    public Map<DBKingdom, IAChannel> getChannelMap() {
        return Collections.unmodifiableMap(channelMap);
    }

    public IAChannel get(GuildMessageChannel channel) {
        return get(channel, false);
    }

    public IAChannel get(GuildMessageChannel channel, boolean create) {
        if (!(channel instanceof TextChannel)) return null;
        TextChannel tc = (TextChannel) channel;
        Category parent = tc.getParentCategory();
        if (parent == null) return null;
        Map.Entry<DBKingdom, User> nationUser = getKingdomUser(channel);
        IAChannel value = channelMap.get(nationUser.getKey());
        if (value == null || !create) {
            return value;
        }
        if (nationUser.getKey() != null) {
            DBKingdom nation = nationUser.getKey();
            if (getCategories().contains(parent)) {
                IAChannel iaChan = new IAChannel(nation, db, parent, tc);
                channelMap.put(nation, iaChan);
                return iaChan;
            }
        }
        return null;
    }

    public IAChannel get(DBKingdom nation) {
        return channelMap.get(nation);
    }

    public IAChannel find(DBKingdom nation) {
        IAChannel result = get(nation);
        if (result != null) return result;
        User user = nation.getUser();
        if (user == null) return null;

        Member member = guild.getMember(user);
        if (member == null) return null;

        for (TextChannel channel : getAllChannels()) {
            Member overrideMem = getOverride(channel.getMemberPermissionOverrides());
            if (member.equals(overrideMem)) {
                IAChannel iaChannel = new IAChannel(nation, db, channel.getParentCategory(), channel);
                iaChannel.updatePerms();
                channelMap.put(nation, iaChannel);
                return iaChannel;
            }
        }
        return null;
    }


    public GuildMessageChannel getOrCreate(DBKingdom kingdom) {
        return getOrCreate(kingdom, false);
    }
    public GuildMessageChannel getOrCreate(DBKingdom kingdom, boolean throwError) {
        User user = kingdom.getUser();
        if (user == null) throw new IllegalArgumentException("Please use `/register/`");
        Member member = guild.getMember(user);
        if (member == null) {
            try {
                member = RateLimitUtil.complete(guild.retrieveMemberById(user.getIdLong()));
                if (member == null) {
                    if (throwError) throw new IllegalArgumentException("Member is null");
                    return null;
                }
            } catch (ErrorResponseException e) {
                if (throwError) throw new IllegalArgumentException("Member is null: " + e.getMessage());
                return null;
            }
        }
        Map<DBRealm, DBKingdom> nations = DBKingdom.getFromUser(user);
        if (!nations.isEmpty()) {
            IAChannel iaChannel = channelMap.get(user.getIdLong());
            if (iaChannel != null && iaChannel.getChannel() != null) {
                TextChannel channel = guild.getTextChannelById(iaChannel.getChannel().getIdLong());
                if (channel != null) return iaChannel.getChannel();
            }

            for (TextChannel channel : getAllChannels()) {
                String[] split = channel.getName().split("-");
                if (split.length >= 2 && MathMan.isInteger(split[split.length - 1])) {
                    int nationId = Integer.parseInt(split[split.length - 1]);
                    if (nationId == kingdom.getId()) {
                        iaChannel = new IAChannel(kingdom, db, channel.getParentCategory(), channel);
                        iaChannel.updatePerms();
                        channelMap.put(kingdom, iaChannel);
                        return channel;
                    }
                }
            }
        }
        for (TextChannel channel : getAllChannels()) {
            Member overrideMem = getOverride(channel.getMemberPermissionOverrides());
            if (user.getIdLong() == overrideMem.getIdLong()) {
                return channel;
            }
        }
        Category category = getFreeCategory(activeCategories);

        if (category == null) {
            if (throwError) throw new IllegalArgumentException("No interview category found");
            return null;
        }

        String channelName;
        if (kingdom != null) {
            channelName = kingdom.getSlug() + "-" + kingdom.getId();
        } else {
            channelName = user.getName();
        }

        TextChannel channel = RateLimitUtil.complete(category.createTextChannel(channelName));
        if (channel == null) {
            if (guild.getCategoryById(category.getIdLong()) == null) {
                fetchChannels();
                category = getFreeCategory(activeCategories);
                if (category == null) {
                    if (throwError) throw new IllegalArgumentException("Interview category was deleted or is inaccessible");
                    return null;
                }
                channel = RateLimitUtil.complete(category.createTextChannel(user.getName()));
            }
            if (channel == null) {
                if (throwError) throw new IllegalArgumentException("Error creating channel");
                return null;
            }
        }
        RateLimitUtil.complete(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));

        Role interviewer = Roles.INTERVIEWER.toRole(guild);
        if (interviewer != null) {
            RateLimitUtil.queue(channel.upsertPermissionOverride(interviewer).grant(Permission.VIEW_CHANNEL));
        }
        Role iaRole = Roles.INTERNAL_AFFAIRS_STAFF.toRole(guild);
        if (iaRole != null && interviewer == null) {
            RateLimitUtil.queue(channel.upsertPermissionOverride(iaRole).grant(Permission.VIEW_CHANNEL));
        }

        Role iaRole2 = Roles.INTERNAL_AFFAIRS.toRole(guild);
        if (iaRole2 != null && interviewer == null && iaRole2 != iaRole) {
            RateLimitUtil.queue(channel.upsertPermissionOverride(iaRole).grant(Permission.VIEW_CHANNEL));
        }

        RateLimitUtil.queue(channel.upsertPermissionOverride(guild.getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL));

        if (kingdom != null) {
            IAChannel iaChannel = new IAChannel(kingdom, db, channel.getParentCategory(), channel);
            iaChannel.updatePerms();
            channelMap.put(iaChannel.getKingdom(), iaChannel);
        }

        IMessageIO io = new DiscordChannelIO(channel);
        IMessageBuilder msg = io.create();

        String body = db.getCopyPasta("interview", true);
        if (body != null) {
            String title = "Welcome to " + guild.getName() + " " + user.getName();
            msg.embed(title, body);
        }

        msg.append(user.getAsMention()).send();

        return channel;
    }

    public List<Category> getCategories() {
        Set<Category> result = new LinkedHashSet<>();
        result.addAll(activeCategories);
        result.addAll(inactiveCategories);
        result.addAll(passedCategories);
        return new ArrayList<>(result);
    }

    public List<TextChannel> getAllChannels() {
        Set<TextChannel> result = new LinkedHashSet<>();
        for (Category category : getCategories()) {
            result.addAll(category.getTextChannels());
        }
        return new ArrayList<>(result);
    }

    private void fetchChannels() {
        channelMap.clear();

        List<Category> categories = guild.getCategories().stream().filter(f -> f.getName().toLowerCase().startsWith("interview")).collect(Collectors.toList());

        for (Category category : categories) {
            String name = category.getName().toLowerCase();
            if (name.startsWith("interview-inactive")) inactiveCategories.add(category);
            else if (name.startsWith("interview-passed")) passedCategories.add(category);
            else if (name.startsWith("interview-archive")) passedCategories.add(category);
            else if (name.startsWith("interview-entry")) activeCategories.add(0, category);
            else activeCategories.add(category);
        }
    }

    public Map.Entry<DBKingdom, User> getKingdomUser(GuildMessageChannel channel) {
        if (!(channel instanceof TextChannel)) return new AbstractMap.SimpleEntry<>(null, null);
        TextChannel tc = (TextChannel) channel;
        DBKingdom nation = null;
        User user = null;

        String[] split = channel.getName().split("-");
        if (MathMan.isInteger(split[split.length - 1])) {
            int nationId = Integer.parseInt(split[split.length - 1]);
            nation = DBKingdom.get(nationId);
            if (nation != null) {
                user = nation.getUser();
            }
        }
        return new AbstractMap.SimpleEntry<>(nation, user);

    }

    public void purgeUnusedChannels(IMessageIO output) {
        if (output == null) output = this.output;

        List<TextChannel> channels = getAllChannels();
        for (TextChannel channel : channels) {
            IAChannel iaChannel = get(channel, true);
            if (iaChannel == null) {
                Member member = getOverride(channel.getMemberPermissionOverrides());
                if (member == null) {
                    if (output != null) output.send("Deleted channel " + channel.getName() + " (no member was found)");
                    RateLimitUtil.queue(channel.delete());
                    continue;
                }
                GuildMessageChannel tc = (GuildMessageChannel) channel;
                try {
                    Message latest = RateLimitUtil.complete(tc.retrieveMessageById(tc.getLatestMessageIdLong()));
                    long created = latest.getTimeCreated().toEpochSecond() * 1000L;
                    if (System.currentTimeMillis() - created > TimeUnit.DAYS.toMillis(10)) {
                        if (output != null) output.send("Deleted channel " + channel.getName() + " (no recent message- no nation (found)");
                        RateLimitUtil.queue(tc.delete());
                    }
                } catch (Exception e) {}
            } else {
                DBKingdom nation = iaChannel.getKingdom();
                if (nation.getActive_m() > 20000 || (nation.getActive_m() > 10000) && !alliance.isInAlliance(nation)) {
                   if (output != null) output.send("Deleted channel " + channel.getName() + " (nation is (inactive)");
                    RateLimitUtil.queue(channel.delete());
                }
            }
        }
    }

    public void alertInvalidChannels(IMessageIO output) {
        if (output == null) output = this.output;

        List<TextChannel> channels = getAllChannels();
        for (TextChannel channel : channels) {
            IAChannel iaChannel = get(channel, true);

            if (
                // channel is null
                    iaChannel == null ||
                            // nation is inactive vm app
                            (iaChannel.getKingdom().getActive_m() > 10000 && iaChannel.getKingdom().getPosition().ordinal() <= 1) ||
                            // nation not in alliance
                            !alliance.contains(iaChannel.getKingdom().getAlliance_id()) ||
                            // user is null
                            iaChannel.getKingdom().getUser() == null ||
                            guild.getMember(iaChannel.getKingdom().getUser()) == null
            ) {
                try {

                    StringBuilder body = new StringBuilder(channel.getAsMention());
                    DBKingdom nation = null;
                    if (iaChannel != null) {
                        nation = iaChannel.getKingdom();
                    }
                    if (nation != null) {
                        body.append("\n").append(nation.getKingdomUrlMarkup(null, true) + " | " + nation.getAllianceUrlMarkup(null, true));
                        body.append("\nPosition: ").append(nation.getPosition());
                        body.append("\nActive: ").append(TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m()));
                        User user = nation.getUser();
                        if (user != null) {
                            body.append("\n").append(user.getAsMention());
                        }
                    }
                    if (channel.getLatestMessageIdLong() > 0) {
                        long latestMessage = channel.getLatestMessageIdLong();
                        Message message = RateLimitUtil.complete(channel.retrieveMessageById(latestMessage));
                        if (message != null) {
                            long diff = System.currentTimeMillis() - message.getTimeCreated().toEpochSecond() * 1000L;
                            body.append("\n\nLast message: ").append(TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
                        }
                    }

                    String emoji = "Delete Channel";

                    body.append("\n\nPress `" + emoji + "` to delete");
                    output.create().embed( "Interview not assigned to a member", body.toString())
//                                    .commandButton(CM.channel.delete.current.cmd.create(channel.getAsMention()), emoji)
                                            .send();

                    if (nation != null && ((nation.getActive_m() > 7200) || (nation.getActive_m() > 2880 && (nation.getPosition().ordinal() <= 1 || !alliance.contains(nation.getAlliance_id()))))) {
                        if (getFreeCategory(inactiveCategories) != null && !inactiveCategories.contains(channel.getParentCategory())) {
                            RateLimitUtil.queue(channel.getManager().setParent(getFreeCategory(inactiveCategories)));
                        }
                    }
                } catch (Throwable ignore) {
                    ignore.printStackTrace();
                }
            }
        }
    }

    public IACategory load() {
        fetchChannels();

        List<TextChannel> channels = getAllChannels();

        Set<Integer> duplicates = new HashSet<>();

        for (TextChannel channel : channels) {
            String name = channel.getName();
            String[] split = name.split("-");
            if (split.length <= 1) continue;
            String idStr = split[split.length - 1];

            IAChannel iaChannel = null;
            if (MathMan.isInteger(idStr)) {
                int nationId = Integer.parseInt(idStr);
                DBKingdom nation = DBKingdom.get(nationId);
                if (nation != null) {
                    iaChannel = new IAChannel(nation, db, channel.getParentCategory(), channel);
                }
            }
            if (iaChannel != null) {
                DBKingdom nation = iaChannel.getKingdom();
                if (!duplicates.add(nation.getId())) {
                    RateLimitUtil.queue(iaChannel.getChannel().delete());
                    continue;
                }
                channelMap.put(iaChannel.getKingdom(), iaChannel);
                if (getFreeCategory(inactiveCategories) != null) {
                    if ((nation.getActive_m() > 7200) ||
                            (nation.getActive_m() > 2880 && (nation.getPosition().ordinal() <= 1 || !alliance.contains(nation.getAlliance_id()))) ||
                            (nation.getActive_m() > 2880 && inactiveCategories.contains(channel.getParentCategory()))
                    ) {
                        if (!inactiveCategories.contains(channel.getParentCategory())) {
                            RateLimitUtil.queue(channel.getManager().setParent(getFreeCategory(inactiveCategories)));
                        }
                    } else if (inactiveCategories.contains(channel.getParentCategory())) {
                        RateLimitUtil.queue(channel.getManager().setParent(getFreeCategory(activeCategories)));
                    }
                }
            }
        }

        Set<DBKingdom> nations = alliance.getKingdoms(f -> f.getPosition().ordinal() > Rank.APPLICANT.ordinal());
        for (DBKingdom nation : nations) {
            if (channelMap.containsKey(nation)) continue;
        }
        return this;
    }

    private Member getOverride(List<PermissionOverride> overrides) {
        Member member = null;
        for (PermissionOverride override : overrides) {
            if (override.getMember() != null) {
                if (override.getMember().getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) continue;
                if (member != null) {
                    return null;
                }
                member = override.getMember();
            }
        }
        return member;
    }

    public IMessageIO getOutput() {
        return output;
    }

    public void update(Map<DBKingdom, Map<IACheckup.AuditType, Map.Entry<Object, String>>> result) {
        for (Map.Entry<DBKingdom, IAChannel> entry : channelMap.entrySet()) {
            DBKingdom nation = entry.getKey();
            IAChannel iaChannel = entry.getValue();

            Map<IACheckup.AuditType, Map.Entry<Object, String>> audit = result.get(nation);
            if (audit == null) continue;
            audit = IACheckup.simplify(audit);

            Category archived = getFreeCategory(passedCategories);
            if (audit.isEmpty() && archived != null) {
                TextChannel channel = iaChannel.getChannel();
                if (activeCategories.contains(channel.getParentCategory())) {
                    RateLimitUtil.queue(channel.getManager().setParent(archived));
                }
            }

            iaChannel.update(audit);
        }
    }

    public Map<DBKingdom, Map<IACheckup.AuditType, Map.Entry<Object, String>>> update() throws InterruptedException, ExecutionException, IOException {
        ArrayList<DBKingdom> nations = new ArrayList<>(channelMap.keySet());
        ArrayList<DBKingdom> toCheckup = new ArrayList<>(nations);
        toCheckup.removeIf(f -> f.isVacation() || f.getActive_m() > 2880 || f.getPosition().ordinal() <= 1);
        IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);

        Map<DBKingdom, Map<IACheckup.AuditType, Map.Entry<Object, String>>> result = checkup.checkup(toCheckup, f -> {}, true);

        update(result);

        return result;
    }

    public boolean isValid() {
        return !channelMap.isEmpty();
    }

    public SortedCategory getSortedCategory(GuildMessageChannel channel) {
        if (!(channel instanceof ICategorizableChannel)) return null;
        Category category = ((ICategorizableChannel) channel).getParentCategory();
        if (category != null) {
            return SortedCategory.parse(category.getName());
        }
        return null;
    }

    public void sort(IMessageIO output, Collection<TextChannel> channels, boolean sortCategoried) {
        Set<TextChannel> toSort = new HashSet<>();

        Map<SortedCategory, Set<Category>> categories = new HashMap<>();
        for (Category category : getCategories()) {
            SortedCategory sortCat = SortedCategory.parse(category.getName());
            if (sortCat != null) {
                categories.computeIfAbsent(sortCat, f -> new HashSet<>()).add(category);
            }
        }


        if (!sortCategoried) {
            for (TextChannel channel : channels) {
                Category category = channel.getParentCategory();
                SortedCategory sortedType = SortedCategory.parse(category.getName());
                if (sortedType != null) continue;
                toSort.add(channel);
            }
        } else {
            toSort.addAll(channels);
        }
        toSort.removeIf(f -> f.getParentCategory().getName().contains("archive"));
        if (toSort.isEmpty()) throw new IllegalArgumentException("No channels found to sort");

        List<SortedCategory> fullCategories = new ArrayList<>();

        outer:
        for (TextChannel channel : channels) {
            for (SortedCategory sort : SortedCategory.values()) {
                IAChannel iaCat = get(channel, true);
                if (iaCat == null) continue;
                if (sort.matches(this, db, alliance.getIds(), channel, iaCat)) {
                    SortedCategory currentSort = SortedCategory.parse(channel.getParentCategory().getName());
                    if (currentSort == sort) continue outer;

                    DBKingdom nation = iaCat.getKingdom();
                    if (nation != null && currentSort != null && sort != null && sort.ordinal() > currentSort.ordinal()) {
                        ByteBuffer buf = db.getKingdomMeta(nation.getId(), KingdomMeta.IA_CATEGORY_MAX_STAGE);
                        int previousMaxStage = -1;
                        if (buf != null) {
                            previousMaxStage = buf.get();
                        }

                        byte[] valueArr = new byte[]{(byte) sort.ordinal()};
                        db.setMeta(nation.getId(), KingdomMeta.IA_CATEGORY_MAX_STAGE, valueArr);
                    }

                    Set<Category> newParents = categories.get(sort);
                    if (newParents == null) throw new IllegalArgumentException("No category found for: " + sort);
                    Category parent = getFreeCategory(newParents);
                    if (parent == null) {
                        fullCategories.add(sort);
                        continue outer;
                    }
                    output.send("Moving " + channel.getAsMention() + " from " + channel.getParentCategory().getName() + " to " + parent.getName());
                    RateLimitUtil.complete(channel.getManager().setParent(parent));
                    continue outer;
                }
            }
            Category parent = getFreeCategory(passedCategories);
            if (parent != null && !channel.getParentCategory().equals(parent)) {
                output.send("Moving " + channel.getAsMention() + " from " + channel.getParentCategory().getName() + " to " + parent.getName());
                RateLimitUtil.complete(channel.getManager().setParent(parent));
            }
        }

        if (!fullCategories.isEmpty()) {
            throw new IllegalArgumentException("Categories are full for (50 channels per category): " + StringMan.getString(fullCategories));
        }
    }

    public class AssignedMentor {
        public final User mentor;
        public final DBKingdom mentee;
        public final long timeAssigned;
        public final KingdomMeta type;

        public AssignedMentor(User mentor, DBKingdom mentee, long timeAssigned, KingdomMeta type) {
            this.mentor = mentor;
            this.mentee = mentee;
            this.timeAssigned = timeAssigned;
            this.type = type;
        }
    }

    public AssignedMentor getMentor(DBKingdom member, long updateTimediff) {
        List<KingdomMeta> metas = Arrays.asList(KingdomMeta.CURRENT_MENTOR, KingdomMeta.REFERRER);
        User latestKingdom = null;
        KingdomMeta latestMeta = null;
        long latestTime = 0;
        for (KingdomMeta meta : metas) {
            ByteBuffer buf = db.getMeta(member.getId(), meta);
            if (buf != null) {
                User user = Trocutus.imp().getDiscordApi().getUserById(buf.getLong());
                long timeStamp = buf.getLong();
                if (timeStamp > latestTime) {
                    latestTime = timeStamp;
                    latestKingdom = user;
                    latestMeta = meta;
                    break;
                }
            }
        }
        long cutoff = System.currentTimeMillis() - updateTimediff;
        if (latestKingdom != null) {
            return new AssignedMentor(latestKingdom, member, latestTime, latestMeta);
        }
        return null;
    }

    public List<Category> getActiveCategories() {
        return activeCategories;
    }

    public enum SortedCategory {
        INACTIVE("Inactive nations") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (!(channel instanceof TextChannel)) return false;
                TextChannel tc = (TextChannel) channel;
                DBKingdom nation = iaChan == null ? null : iaChan.getKingdom();
                if (nation != null && Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                if (iaCat.inactiveCategories.contains(tc.getParentCategory())) {
                    if (iaChan == null ||
                        (nation.getActive_m() > 2880 && (nation.getPosition().ordinal() <= 1 || !allianceIds.contains(nation.getAlliance_id()))) ||
                        nation.getActive_m() > 7200 ||
                        db.getGuild().getMember(iaChan.getKingdom().getUser()) == null) {
                        return true;
                    }
                }
                if (iaChan == null) {
                    return false;
                }
                if (nation.getActive_m() > 7200 || (nation.getActive_m() > 2880 && (nation.getPosition().ordinal() <= 1 || !allianceIds.contains(nation.getAlliance_id())))) return true;
                return false;
            }
        },
        ENTRY("people still doing the initial interview / not a member yet") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return true;
                DBKingdom nation = iaChan.getKingdom();
                if (!allianceIds.contains(nation.getAlliance_id()) || nation.getPosition().ordinal() <= 1) return true;
                User user = nation.getUser();
                if (user == null || db.getGuild().getMember(user) == null) return true;
                return false;
            }
        },
        SPIES("hasn't used " + CM.war.find.intel.cmd.toSlashCommand() + " and " + "CM.spy.find.intel.cmd.toSlashCommand()" + " (and posted spy report)") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBKingdom nation = iaChan.getKingdom();
                if (Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                return nation.getAllOffSpyops().isEmpty();
            }
        },
        RAIDS("haven't started their initial raids yet") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBKingdom nation = iaChan.getKingdom();
                if (Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                if (nation.getLatestOffensive() == null) {
                    return true;
                }
                return false;
            }
        },
        TEST("hasn't done the tests (and received the graduated role)") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBKingdom nation = iaChan.getKingdom();
                User user = nation.getUser();
                Role role = Roles.GRADUATED.toRole(db.getGuild());
                if (role == null || user == null) return false;
                Member member = db.getGuild().getMember(user);
                return (member != null && !member.getRoles().contains(role));
            }
        },

        ARCHIVE("graduated nations") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                return true;
            }
        }

        ;

        private final String desc;

        SortedCategory(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }

        public static SortedCategory parse(String name) {
            String[] split = name.split("-");
            for (SortedCategory value : SortedCategory.values()) {
                for (String s : split) {
                    if (s.equalsIgnoreCase(value.name())) {
                        return value;
                    }
                }
            }
            return null;
        }

        public abstract boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan);
    }
}
