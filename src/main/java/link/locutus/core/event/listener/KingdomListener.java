package link.locutus.core.event.listener;

import com.google.common.eventbus.Subscribe;
import link.locutus.Trocutus;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomFilter;
import link.locutus.core.db.entities.kingdom.KingdomMeta;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.db.guild.entities.EnemyAlertChannelMode;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.event.kingdom.KingdomChangeActiveEvent;
import link.locutus.core.event.kingdom.KingdomChangeAlertEvent;
import link.locutus.core.event.kingdom.KingdomChangePositionEvent;
import link.locutus.core.event.kingdom.KingdomChangeSpellAlertEvent;
import link.locutus.core.event.kingdom.KingdomChangeVacationEvent;
import link.locutus.core.event.kingdom.KingdomCreateEvent;
import link.locutus.core.event.kingdom.KingdomDeleteEvent;
import link.locutus.core.settings.Settings;
import link.locutus.util.AlertUtil;
import link.locutus.util.DiscordUtil;
import link.locutus.util.FileUtil;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.scheduler.CaughtRunnable;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class KingdomListener {
    @Subscribe
    public void onKingdomChangeActive(KingdomChangeActiveEvent event) {
        DBKingdom previous = event.getFrom();
        DBKingdom nation = event.getTo();

        {
            long activeTurn = TimeUtil.getTurn(nation.getLast_active());
            Trocutus.imp().getDB().setActivity(nation.getId(), activeTurn);
        }

        Map<Long, Long> notifyMap = nation.getLoginNotifyMap();
        if (notifyMap != null) {
            nation.deleteMeta(KingdomMeta.LOGIN_NOTIFY);
            if (!notifyMap.isEmpty()) {
                String message = ("This is your login alert for:\n" + nation.toMarkdown(null, false));

                for (Map.Entry<Long, Long> entry : notifyMap.entrySet()) {
                    Long userId = entry.getKey();
                    User user = Trocutus.imp().getDiscordApi().getUserById(userId);
                    if (user == null) continue;
                    try {
                        DiscordChannelIO channel = new DiscordChannelIO(RateLimitUtil.complete(user.openPrivateChannel()), null);
                        channel.send(message);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Subscribe
    public void onKingdomCreate(KingdomCreateEvent event) {
        DBKingdom current = event.getTo();
    }

    private void checkOfficerChange(DBKingdom previous, DBKingdom current) {
        if (current == null || previous == null || previous.getAlliance_id() == current.getAlliance_id() || current.getActive_m() > 4880) return;
        if (previous.getPosition().ordinal() < Rank.ADMIN.ordinal()) {
            Rank position = previous.getPosition();
            if (position == null || position.ordinal() < Rank.ADMIN.ordinal()) return;
        }
        DBAlliance alliance = previous.getAlliance();

        if (alliance != null)
        {
            String title = current.getName() + " (" + (previous.getPosition()) + ") leaves " + previous.getAllianceName();
            String body = current.toMarkdown(null, true);
            AlertUtil.forEachChannel(f -> true, GuildKey.AA_ADMIN_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    @Subscribe
    public void onKingdomDelete(KingdomDeleteEvent event) {
        DBKingdom previous = event.getFrom();
        DBKingdom current = event.getTo();

        handleOfficerDelete(previous, current);
        processDeletion(previous, current);
    }

    @Subscribe
    public void onKingdomLeaveAlert(KingdomChangeAlertEvent event) {
        DBKingdom nation = event.getTo();
        if (nation.isVacation() == false) {
            enemyAlert(event.getFrom(), nation);
        }
    }

    @Subscribe
    public void onKingdomLeaveAlert(KingdomChangeSpellAlertEvent event) {
        DBKingdom nation = event.getTo();
        if (nation.isVacation() == false) {
            enemyAlert(event.getFrom(), nation);
        }
    }

    @Subscribe
    public void onVM(KingdomChangeVacationEvent event) {
        DBKingdom previous = event.getFrom();
        DBKingdom current = event.getTo();
        if (previous.isVacation() == false && current.isVacation() == true) {
//            processVMTransfers(previous, current);
        } else if (previous.isVacation() && !current.isVacation()) {
//            raidAlert(current);
            enemyAlert(previous, current);
        }
    }

    private boolean enemyAlert(DBKingdom previous, DBKingdom current) {
        if (current.getActive_m() > 7200) return false;

        if (previous.getAlert_level() > current.getAlert_level()) return false;
        if (previous.getSpell_alert() > current.getSpell_alert()) return false;

        if (current.getActive_m() > 7200) return false;

        List<String> changes = new ArrayList<>();
        if (current.getAlert_level() < previous.getAlert_level() && current.getAlert_level() == 0) {
            changes.add("Attack: " + previous.getAlert_level() + " -> " + current.getAlert_level());
        }
        if (current.getSpell_alert() < previous.getSpell_alert() && current.getSpell_alert() == 0) {
            changes.add("Spell: " + previous.getSpell_alert() + " -> " + current.getSpell_alert());
        }
        if (previous.isVacation() && current.isVacation()) {
            changes.add("Vacation: " + previous.isVacation() + " -> " + current.isVacation());
        }
        if (changes.isEmpty()) return false;

        String title = StringMan.join(changes, " | ");

        double minScore = current.getScore() / 1.75;
        double maxScore = current.getScore() / 0.75;

        AlertUtil.forEachChannel(GuildDB::isValidAlliance, GuildKey.ENEMY_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                EnemyAlertChannelMode mode = guildDB.getOrNull(GuildKey.ENEMY_ALERT_CHANNEL_MODE);
                if (mode == null) mode = EnemyAlertChannelMode.PING_USERS_IN_RANGE;

                KingdomFilter filter = GuildKey.ENEMY_ALERT_FILTER.getOrNull(guildDB);
                if (filter != null && !filter.test(current)) return;

                double strength = current.getDefenseStrength();
                Set<Integer> enemies = guildDB.getCoalition(Coalition.ENEMIES);
                Set<DBKingdom> inRangeKingdoms = new HashSet<>();
                Set<Member> inRangeUsersStronger = new HashSet<>();
                Set<Member> inRangeUsersWeaker = new HashSet<>();

                if (!enemies.isEmpty() && (enemies.contains(current.getAlliance_id()))) {
                    boolean inRange = false;
                    Set<DBKingdom> nations = guildDB.getAllianceList().getKingdoms();
                    for (DBKingdom nation : nations) {
                        if (nation.getRealm_id() == current.getRealm_id() && nation.getScore() >= minScore && nation.getScore() <= maxScore && nation.getActive_m() < 1440) {
                            boolean weaker = nation.getAttackStrength() < strength;
                            inRange = !weaker || mode.pingUsersWeaker();

                            if (inRange) {
                                inRangeKingdoms.add(nation);
                                User user = nation.getUser();
                                if (user != null) {
                                    Member member = guildDB.getGuild().getMember(user);
                                    if (member != null) {
                                        if (weaker) {
                                            inRangeUsersWeaker.add(member);
                                        } else {
                                            inRangeUsersStronger.add(member);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!inRange && mode.requireInRange()) return;

                    String cardInfo = current.toMarkdown(null, true);
                    DiscordChannelIO io = new DiscordChannelIO(channel);

                    IMessageBuilder msg = io.create();
                    msg = msg.embed(title, cardInfo);

                    Guild guild = guildDB.getGuild();
                    Role bountyRole = Roles.ENEMY_ALERT.toRole(guild);
                    if (bountyRole == null) {
                        msg.send();
                        return;
                    }

                    if (mode.pingUsers()) {
                        Role optOut = Roles.WAR_ALERT_OPT_OUT.toRole(guild);
                        Predicate<Member> userFilter = f -> {
                            if (!f.getRoles().contains(bountyRole)) return true;
                            if (optOut != null && f.getRoles().contains(optOut)) return true;
                            return false;
                        };
                        inRangeUsersWeaker.removeIf(userFilter);
                        inRangeUsersStronger.removeIf(userFilter);

                        if (!inRangeUsersStronger.isEmpty()) {
                            msg.append("Stronger: ");
                            msg.append(StringMan.join(inRangeUsersStronger, ", ", m -> m.getAsMention()));
                            msg.append("\n");
                        }
                        if (!inRangeUsersWeaker.isEmpty()) {
                            msg.append("Weaker: ");
                            msg.append(StringMan.join(inRangeUsersWeaker, ", ", m -> m.getAsMention()));
                            msg.append("\n");
                        }
                    } else if (mode.pingRole()) {
                        msg.append(bountyRole.getAsMention());
                    }
//                    msg.addClaimButton();
                    msg.send();
                }
            }
        });
        return true;
    }

    @Subscribe
    public void onPositionChange(KingdomChangePositionEvent event) {
        DBKingdom current = event.getTo();
        DBKingdom previous = event.getFrom();

        checkOfficerChange(previous, current);
        Trocutus.imp().getDB().addRemove(current.getId(), previous.getAlliance_id(), System.currentTimeMillis(), (previous.getPosition()));
    }

    private void handleOfficerDelete(DBKingdom previous, DBKingdom current) {
        if (current != null || previous == null || previous.getActive_m() > 10000) return;
        if (previous.getPosition().ordinal() < Rank.ADMIN.ordinal()) {
            Rank position = previous.getPosition();
            if (position == null || position.ordinal() < Rank.ADMIN.ordinal()) return;
        }
        DBAlliance alliance = previous.getAlliance();
        if (alliance != null)
        {
            String title = previous.getName() + " (" + (previous.getPosition()) + ") deleted from " + previous.getAllianceName();
            String body = previous.toMarkdown(null, false);
            AlertUtil.forEachChannel(f -> true, GuildKey.AA_ADMIN_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    public static void processDeletion(DBKingdom previous, DBKingdom current) {
        if (previous.getActive_m() < 14400 && current == null) {
            List<String> adminInfo = new ArrayList<>();

            String type = "DELETION";
            String body = previous.toMarkdown(null, false);
            String url = previous.getUrl(null);

            String finalType = type;
            String finalBody = body;
            String title = "Detected " + finalType + ": " + previous.getName() + " | " + "" + url + " | " + previous.getAllianceName();
            AlertUtil.forEachChannel(f -> true, GuildKey.DELETION_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB db) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }
}
