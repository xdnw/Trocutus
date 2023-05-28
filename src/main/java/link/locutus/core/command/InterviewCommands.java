package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Filter;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.Timediff;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomMeta;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.interview.IACategory;
import link.locutus.core.db.guild.interview.IAChannel;
import link.locutus.core.db.guild.interview.IACheckup;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;
import org.sqlite.core.DB;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class InterviewCommands {
    @Command(desc = "Send a message to the interview channels of the nations specified")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String interviewMessage(@Me GuildDB db, Set<DBKingdom> nations, String message, @Switch("p") boolean pingMentee) {
        Map<DBKingdom, IAChannel> map = db.getIACategory().getChannelMap();
        int num = 0;
        for (DBKingdom nation : nations) {
            IAChannel iaChan = map.get(nation);
            if (iaChan == null) continue;
            GuildMessageChannel channel = iaChan.getChannel();
            if (channel != null) {
                try {
                    String localMessage = message;
                    User user = nation.getUser();
                    if (pingMentee && user != null) {
                        localMessage += "\n" + user.getAsMention();
                    }
                    RateLimitUtil.queue(channel.sendMessage(localMessage));
                    num++;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        return "Done. Sent " + num + " messaged!";
    }

    @Command(desc = "Unassign a mentee from all mentors")
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public String unassignMentee(@Me GuildDB db, @Me Guild guild, @Me User author, DBKingdom mentee) {
        ByteBuffer mentorBuf = db.getKingdomMeta(mentee.getId(), KingdomMeta.CURRENT_MENTOR);
        DBKingdom currentMentor = mentorBuf != null ?  DBKingdom.get(mentorBuf.getInt()) : null;

        if (currentMentor != null && currentMentor.getActive_m() < 1440) {
            User currentMentorUser = currentMentor.getUser();
        }

        ByteBuffer buf = ByteBuffer.allocate(Long.SIZE + Long.SIZE);
        buf.putLong(0);
        buf.putLong(System.currentTimeMillis());

        db.setMeta(mentee.getId(), KingdomMeta.CURRENT_MENTOR, buf.array());

        MessageChannel alertChannel = db.getOrNull(GuildKey.INTERVIEW_PENDING_ALERTS);
        if (alertChannel != null) {
            String message = "Mentor (" + author.getName() +
                    ") unassigned Mentee (" + mentee.getName() + " | " + mentee.getUserDiscriminator() + ")"
                    + (currentMentor != null ? " from Mentor (" + currentMentor.getName() + " | " + currentMentor.getUserDiscriminator() + ")" : "");
            RateLimitUtil.queue(alertChannel.sendMessage(message));
        }

        return "Set " + mentee.getName() + "'s mentor to null";
    }

    @Command(desc = "Assign a mentor to a mentee")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String mentor(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, User mentor, DBKingdom mentee, @Switch("f") boolean force) {
        User menteeUser = mentee.getUser();
        if (menteeUser == null) return "Mentee is not registered";

        if (!force) {
            ByteBuffer mentorBuf = db.getKingdomMeta(mentee.getId(), KingdomMeta.CURRENT_MENTOR);
            if (mentorBuf != null) {
                User current = Trocutus.imp().getDiscordApi().getUserById(mentorBuf.getLong());
                if (current != null && Roles.MEMBER.has(current, db.getGuild())) {
                    String title = mentee.getName() + " already has a mentor";
                    StringBuilder body = new StringBuilder();
                    body.append("Current mentor: " + current.getAsMention());
                    io.create().confirmation(title, body.toString(), command).send();
                    return null;
                }
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(Long.SIZE + Long.SIZE);
        buf.putLong(mentor.getIdLong());
        buf.putLong(System.currentTimeMillis());

        db.setMeta(mentee.getId(), KingdomMeta.CURRENT_MENTOR, buf.array());
        return "Set " + mentee.getName() + "'s mentor to " + mentor.getName();
    }

    @Command(desc = "Assign yourself as someone's mentor")
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public String mentee(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Me User me, DBKingdom mentee, @Switch("f") boolean force) {
        return mentor(command, io, db, me, mentee, force);
    }

    @Command(desc = "List mentors and their respective mentees", aliases = {"mymentees"})
    @RolePermission(value=Roles.INTERNAL_AFFAIRS)
    public String myMentees(@Me Guild guild, @Me GuildDB db, @Me User author, @Default("*") Set<DBKingdom> mentees, @Arg("Activity requirements for mentors") @Default("2w") @Timediff long timediff) throws InterruptedException, ExecutionException, IOException {
        return listMentors(guild, db, author, mentees, timediff, db.isWhitelisted(), true, false);
    }

    @Command(desc = "List mentors and their respective mentees", aliases = {"listMentors", "mentors", "mentees"})
    @RolePermission(value=Roles.INTERNAL_AFFAIRS)
    public String listMentors(@Me Guild guild, @Me GuildDB db, @Me User author, @Default("*") Set<DBKingdom> mentees,
                              @Arg("Activity requirements for mentors") @Default("2w") @Timediff long timediff,
                              @Arg("Include an audit summary with the list") @Switch("a") boolean includeAudit,
                              @Arg("Do NOT list members without a mentor") @Switch("u") boolean ignoreUnallocatedMembers,
                              @Arg("List mentors without any active mentees") @Switch("i") boolean listIdleMentors) throws IOException, ExecutionException, InterruptedException {
        if (includeAudit && !db.isWhitelisted()) return "No permission to include audits";

        IACategory iaCat = db.getIACategory();
        if (iaCat == null) return "No ia category is enabled";

        IACheckup checkup = includeAudit ? new IACheckup(db, db.getAllianceList(), true) : null;

        Map<User, List<DBKingdom>> mentorMenteeMap = new HashMap<>();
        Map<DBKingdom, User> menteeMentorMap = new HashMap<>();
        Map<DBKingdom, IACategory.SortedCategory> categoryMap = new HashMap<>();
        Map<DBKingdom, Boolean> passedMap = new HashMap<>();

        for (Map.Entry<DBKingdom, IAChannel> entry : iaCat.getChannelMap().entrySet()) {
            DBKingdom mentee = entry.getKey();
            if (!mentees.contains(mentee)) continue;
            User user = mentee.getUser();
            if (user == null) continue;

            boolean graduated = Roles.hasAny(user, guild, Roles.GRADUATED, Roles.INTERNAL_AFFAIRS_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON_STAFF, Roles.MILCOM);

            IAChannel iaChan = iaCat.get(mentee);
            if (iaChan != null) {
                TextChannel myChan = iaChan.getChannel();
                if (myChan != null && myChan.getParentCategory() != null) {
                    IACategory.SortedCategory category = IACategory.SortedCategory.parse(myChan.getParentCategory().getName());
                    if (category != null) {
                        categoryMap.put(mentee, category);
                        if (category == IACategory.SortedCategory.ARCHIVE) {
                            graduated = true;
                        }
                    }
                }
            }
            passedMap.put(mentee, graduated);

            IACategory.AssignedMentor mentor = iaCat.getMentor(mentee, timediff);
            if (mentor != null) {
                mentorMenteeMap.computeIfAbsent(mentor.mentor, f -> new ArrayList<>()).add(mentee);
                menteeMentorMap.put(mentee, mentor.mentor);
            }
        }

        if (mentorMenteeMap.isEmpty()) return "No mentees found";

        List<Map.Entry<User, List<DBKingdom>>> sorted = new ArrayList<>(mentorMenteeMap.entrySet());
        sorted.sort(new Comparator<Map.Entry<User, List<DBKingdom>>>() {
            @Override
            public int compare(Map.Entry<User, List<DBKingdom>> o1, Map.Entry<User, List<DBKingdom>> o2) {
                return Integer.compare(o2.getValue().size(), o1.getValue().size());
            }
        });

        long requiredMentorActivity = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20);

        StringBuilder response = new StringBuilder();

        for (Map.Entry<User, List<DBKingdom>> entry : sorted) {
            User mentor = entry.getKey();
            List<DBKingdom> myMentees = new ArrayList<>(entry.getValue());
            Collections.sort(myMentees, new Comparator<DBKingdom>() {
                @Override
                public int compare(DBKingdom o1, DBKingdom o2) {
                    IACategory.SortedCategory c1 = categoryMap.get(o1);
                    IACategory.SortedCategory c2 = categoryMap.get(o2);
                    if (c1 != null && c2 != null) {
                        return Integer.compare(c1.ordinal(), c2.ordinal());
                    }
                    return Integer.compare(c1 == null ? 1 : 0, c2 == null ? 1 : 0);
                }
            });

            int numPassed = (int) myMentees.stream().filter(f -> passedMap.getOrDefault(f, false)).count();
            myMentees.removeIf(f -> passedMap.getOrDefault(f, false));
            if (myMentees.isEmpty()) {
                response.append("**No current mentors**");
                continue;
            }

            response.append("\n\n**--- Mentor: " + mentor.getName()).append("**: " + myMentees.size() + "\n");
            response.append("Graduated: " + numPassed + "\n");

            User mentorUser = mentor;
            if (mentorUser == null) {
                response.append("**MENTOR IS NOT VERIFIED:** ").append("\n");
            } else {
                if (!Roles.MEMBER.has(mentorUser, guild)) {
                    response.append("**MENTOR IS NOT MEMBER:** ").append("\n");
                } else if (!Roles.hasAny(mentorUser, guild, Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERVIEWER)) {
                    response.append("**MENTOR IS NOT IA STAFF:** ").append("\n");
                }
            }

            for (DBKingdom myMentee : myMentees) {
                IAChannel myChan = iaCat.get(myMentee);
                IACategory.SortedCategory category = categoryMap.get(myMentee);
                response.append("`" + myMentee.getName() + "` <" + myMentee.getUrl(null) + ">\n");
                response.append("- " + category + " | ");
                if (myChan != null && myChan.getChannel() != null) {
                    GuildMessageChannel tc = myChan.getChannel();
                    response.append(" | " + tc.getAsMention());
                    if (tc.getLatestMessageIdLong() > 0) {
                        long lastMessageTime = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(tc.getLatestMessageIdLong()).toEpochSecond() * 1000L;
                        response.append(" | " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - lastMessageTime));
                    }
                }
                response.append("\n- score:" + myMentee.getTotal_land());

                if (includeAudit) {
                    Map<IACheckup.AuditType, Map.Entry<Object, String>> checkupResult = checkup.checkup(myMentee, true, true);
                    checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);
                    if (!checkupResult.isEmpty()) {
                        response.append("\n- Failed: [" + StringMan.join(checkupResult.keySet(), ", ") + "]");
                    }
                }
                response.append("\n\n");
            }

        }

        if (db.isValidAlliance()) {
            AllianceList alliance = db.getAllianceList();
            Set<DBKingdom> members = alliance.getKingdoms(f -> f.getPosition().ordinal() >= Rank.MEMBER.ordinal() && f.getActive_m() < 2880 && !f.isVacation());
            members.removeIf(f -> !mentees.contains(f));

            List<DBKingdom> membersUnverified = new ArrayList<>();
            List<DBKingdom> membersNotOnDiscord = new ArrayList<>();
            List<DBKingdom> nationsNoIAChan = new ArrayList<>();
            List<DBKingdom> noMentor = new ArrayList<>();
            for (DBKingdom member : members) {
                User user = member.getUser();
                if (user == null) {
                    membersUnverified.add(member);
                    continue;
                }
                if (guild.getMember(user) == null) {
                    membersNotOnDiscord.add(member);
                    continue;
                }
                if (Roles.hasAny(user, guild, Roles.GRADUATED, Roles.ADMIN, Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS, Roles.INTERVIEWER, Roles.MILCOM, Roles.MILCOM_NO_PINGS, Roles.ECON, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.FOREIGN_AFFAIRS)) {
                    continue;
                }
                if (!passedMap.getOrDefault(member, false)) {
                    if (!menteeMentorMap.containsKey(member)) {
                        noMentor.add(member);
                        continue;
                    }
                }
            }

            if (!ignoreUnallocatedMembers) {
                if (listIdleMentors) {
                    Role role = Roles.MENTOR.toRole(guild);
                    if (role == null) {
                        return "No mentor role";
                    }
                    List<Member> mentors = guild.getMembersWithRoles(role);
                    List<User> idleMentors = new ArrayList<>();
                    for (Member mentor : mentors) {
                        List<DBKingdom> myMentees = mentorMenteeMap.getOrDefault(mentor.getUser(), Collections.emptyList());
                        myMentees.removeIf(f -> f.getActive_m() > 4880 || f.isVacation() || passedMap.getOrDefault(f, false));
                        if (myMentees.isEmpty()) {
                            idleMentors.add(mentor.getUser());
                        }
                    }
                    if (!idleMentors.isEmpty()) {
                        List<String> memberNames = idleMentors.stream().map(User::getName).collect(Collectors.toList());
                        response.append("\n**Idle mentors**").append("\n- ").append(StringMan.join(memberNames, "\n- "));
                    }
                }

                if (!membersUnverified.isEmpty()) {
                    List<String> memberNames = membersUnverified.stream().map(DBKingdom::getName).collect(Collectors.toList());
                    response.append("\n**Unverified members**").append("\n- ").append(StringMan.join(memberNames, "\n- "));
                }
                if (!membersNotOnDiscord.isEmpty()) {
                    List<String> memberNames = membersNotOnDiscord.stream().map(DBKingdom::getName).collect(Collectors.toList());
                    response.append("\n**Members left discord**").append("\n- ").append(StringMan.join(memberNames, "\n- "));
                }
                if (!nationsNoIAChan.isEmpty()) {
                    List<String> memberNames = nationsNoIAChan.stream().map(DBKingdom::getName).collect(Collectors.toList());
                    response.append("\n**No interview channel**").append("\n- ").append(StringMan.join(memberNames, "\n- "));
                }
            }
            if (!noMentor.isEmpty()) {
                List<String> memberNames = noMentor.stream().map(DBKingdom::getName).collect(Collectors.toList());
                response.append("\n**No mentor**").append("\n- ").append(StringMan.join(memberNames, "\n- "));
            }
        }

        response.append("\n\nTo assign a nation as your mentee, use " + "CM.interview.mentee.cmd.toSlashMention()");
        return response.toString();
    }

    @Command(desc = "Set the discord category for an interview channel", aliases = {"iacat", "interviewcat", "interviewcategory"})
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String iaCat(@Me IMessageIO channel, @Filter("interview.") Category category) {
        if (!(channel instanceof ICategorizableChannel)) return "This channel cannot be categorized";
        ICategorizableChannel tc = ((ICategorizableChannel) channel);
        RateLimitUtil.queue(tc.getManager().setParent(category));
        return "Moved " + tc.getAsMention() + " to " + category.getName();
    }

    @Command(desc = "Create an interview channel")
    public String interview(@Me GuildDB db, DBKingdom kingdom, @Default("%user%") User user) {
        Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(user);
        if (kingdoms.isEmpty() || !kingdoms.containsValue(kingdom)) {
            return "You are not registered to `" + kingdom.getName() + "`. Please register using `\"CM.register\"`";
        }
        IACategory iaCat = db.getIACategory(true, true, true);

        if (iaCat.getCategories().isEmpty()) {
            return "No categories found starting with: `interview`";
        }

        GuildMessageChannel channel = iaCat.getOrCreate(kingdom, true);
        if (channel == null) return "Unable to find or create channel (does a category called `interview` exist?)";

        Role applicantRole = Roles.APPLICANT.toRole(db.getGuild());
        if (applicantRole != null) {
            Member member = db.getGuild().getMember(user);
            if (member == null || !member.getRoles().contains(applicantRole)) {
                RateLimitUtil.queue(db.getGuild().addRoleToMember(user, applicantRole));
            }
        }

        return channel.getAsMention();
    }

    @Command(aliases = {"syncInterviews", "syncInterview"}, desc = "Force an update of all interview channels and print the results")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String syncInterviews(@Me IMessageIO channel, @Me GuildDB db) {
        IACategory iaCat = db.getIACategory();
        iaCat.load();
        iaCat.purgeUnusedChannels(channel);
        iaCat.alertInvalidChannels(channel);
        return "Done!";
    }

    @Command(aliases = {"sortInterviews", "sortInterview"}, desc = "Sort the interview channels to an audit category\n" +
            "An appropriate discord category must exist in the form: `interview-CATEGORY`\n" +
            "Allowed categories: `INACTIVE,ENTRY,RAIDS,BANK,SPIES,BUILD,COUNTERS,TEST,ARCHIVE`")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String sortInterviews(@Me GuildMessageChannel channel, @Me IMessageIO io, @Me GuildDB db, @Arg("Sort channels already in a category") @Default("true") boolean sortCategorized) {
        IACategory iaCat = db.getIACategory();
        iaCat.purgeUnusedChannels(io);
        iaCat.load();
        if (iaCat.isInCategory(channel)) {
            iaCat.sort(io, Collections.singleton((TextChannel) channel), sortCategorized);
        } else {
            iaCat.sort(io, iaCat.getAllChannels(), true);
        }
        return "Done!";
    }

    @Command(desc = "List the interview channels, by category + activity")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String iachannels(@Me User author, @Me Guild guild, @Me GuildDB db, String filter, @Arg("Highlight channels inactive for longer than the time specified") @Default("1d") @Timediff long time) throws IOException, GeneralSecurityException {
        try {
            if (!filter.isEmpty()) filter += ",*";
            Set<DBKingdom> allowedKingdoms = DBKingdom.parseList(guild, filter, false);

            Set<Integer> aaIds = db.getAllianceIds();
            if (aaIds.isEmpty()) return "No alliance set " + GuildKey.ALLIANCE_ID.getCommandMention() + "";

            IACategory cat = db.getIACategory();
            if (cat.getCategories().isEmpty()) return "No `interview` categories found";
            cat.load();

            Map<Category, List<IAChannel>> channelsByCategory = new LinkedHashMap<>();

            for (Map.Entry<DBKingdom, IAChannel> entry : cat.getChannelMap().entrySet()) {
                DBKingdom nation = entry.getKey();

                if (!allowedKingdoms.contains(nation)) continue;
                if (!aaIds.contains(nation.getAlliance_id()) || nation.getActive_m() > 10000 || nation.isVacation() == true) continue;
                User user = nation.getUser();
                if (user == null) continue;


                IAChannel iaChan = entry.getValue();
                TextChannel channel = iaChan.getChannel();
                Category category = channel.getParentCategory();
                String name = category.getName().toLowerCase();
                if (name.endsWith("-archive") || name.endsWith("-inactive")) continue;
                channelsByCategory.computeIfAbsent(category, f -> new ArrayList<>()).add(iaChan);
            }

            List<Category> categories = new ArrayList<>(channelsByCategory.keySet());
            Collections.sort(categories, Comparator.comparingInt(Category::getPosition));

            StringBuilder response = new StringBuilder();

            for (Category category : categories) {
                List<IAChannel> channels = channelsByCategory.get(category);
                List<Map.Entry<IAChannel, Long>> channelsByActivity = new ArrayList<>();
                Map<IAChannel, Map.Entry<Message, Message>> latestMsgs = new LinkedHashMap<>();


                for (IAChannel iaChan : channels) {
                    GuildMessageChannel channel = iaChan.getChannel();
                    DBKingdom nation = iaChan.getKingdom();
                    List<Message> messages = RateLimitUtil.complete(channel.getHistory().retrievePast(25));
                    User user = nation.getUser();

                    Message latestMessageUs = null;
                    Message latestMessageThem = null;

                    for (Message message : messages) {
                        User msgAuth = message.getAuthor();
                        if (msgAuth.isBot() || msgAuth.isSystem()) continue;
                        String content = DiscordUtil.trimContent(message.getContentRaw());
                        if (content.startsWith(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX)) continue;

                        long msgTime = message.getTimeCreated().toEpochSecond();

                        if (!msgAuth.isBot() && !msgAuth.isSystem()) {


                            if (msgAuth.getIdLong() != user.getIdLong()) {
                                if (latestMessageUs == null || latestMessageUs.getTimeCreated().toEpochSecond() < msgTime) {
                                    latestMessageUs = message;
                                }
                            } else if (latestMessageThem == null || latestMessageThem.getTimeCreated().toEpochSecond() < msgTime) {
                                latestMessageThem = message;
                            }
                        }
                    }


                    long last = 0;
                    if (latestMessageUs != null) last = Math.max(last, latestMessageUs.getTimeCreated().toEpochSecond() * 1000L);
                    if (latestMessageThem != null) last = Math.max(last, latestMessageThem.getTimeCreated().toEpochSecond() * 1000L);
                    long now = System.currentTimeMillis();
                    long diffMsg = now - last;
                    long diffActive = TimeUnit.MINUTES.toMillis(nation.getActive_m());

                    if (last == 0 || diffMsg > diffActive + time) {
                        long activityValue = diffMsg - diffActive;
                        channelsByActivity.add(new AbstractMap.SimpleEntry<>(iaChan, activityValue));
                        latestMsgs.put(iaChan, new AbstractMap.SimpleEntry<>(latestMessageUs, latestMessageThem));
                    }
                }

                if (!channelsByActivity.isEmpty()) {

                    Collections.sort(channelsByActivity, (o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));

                    String name = category.getName().toLowerCase().replaceAll("interview-", "");

                    response.append("**" + name + "**:\n");
                    for (Map.Entry<IAChannel, Long> channelInfo : channelsByActivity) {
                        IAChannel iaChan = channelInfo.getKey();
                        GuildMessageChannel channel = iaChan.getChannel();

                        DBKingdom nation = iaChan.getKingdom();


                        response.append(channel.getAsMention() + " " + nation.getTotal_land() + " ns | " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m()));
                        response.append("\n");

                        Map.Entry<Message, Message> messages = latestMsgs.get(iaChan);
                        List<Message> msgsSorted = new ArrayList<>(2);
                        if (messages.getKey() != null) msgsSorted.add(messages.getKey());
                        if (messages.getValue() != null) msgsSorted.add(messages.getValue());
                        if (msgsSorted.size() == 2 && msgsSorted.get(1).getTimeCreated().toEpochSecond() < msgsSorted.get(0).getTimeCreated().toEpochSecond()) {
                            Collections.reverse(msgsSorted);
                        }

                        if (!msgsSorted.isEmpty()) {
                            for (Message message : msgsSorted) {
                                String msgTrimmed = DiscordUtil.trimContent(message.getContentRaw());
                                if (msgTrimmed.length() > 100) msgTrimmed = msgTrimmed.substring(0, 100) + "...";
                                long epoch = message.getTimeCreated().toEpochSecond() * 1000L;
                                long roundTo = TimeUnit.HOURS.toMillis(1);
                                long diffRounded = System.currentTimeMillis() - epoch;
                                diffRounded = (diffRounded / roundTo) * roundTo;

                                String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diffRounded);

                                response.append("- [" + timeStr + "] **" + message.getAuthor().getName() + "**: `" + msgTrimmed + "`");
                                response.append("\n");
                            }

                        }
                    }
                }
            }
            if (response.length() == 0) return "No results found";
            return response.toString() + "\n" + author.getAsMention();
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
