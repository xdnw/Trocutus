package link.locutus.core.event.listener;

import com.google.common.eventbus.Subscribe;
import link.locutus.Trocutus;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.event.kingdom.KingdomCreateEvent;
import link.locutus.core.event.kingdom.KingdomDeleteEvent;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.TimeUtil;
import link.locutus.util.scheduler.CaughtRunnable;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.BiConsumer;

public class KingdomListener {
    @Subscribe
    public void onKingdomChangeActive(KingdomChangeActiveEvent event) {
        DBKingdom previous = event.getPrevious();
        DBKingdom nation = event.getCurrent();

        {
            long activeTurn = TimeUtil.getTurn(nation.lastActiveMs());
            Trocutus.imp().getDB().setActivity(nation.getId(), activeTurn);
        }

        Map<Long, Long> notifyMap = nation.getLoginNotifyMap();
        if (notifyMap != null) {
            nation.deleteMeta(KingdomMeta.LOGIN_NOTIFY);
            if (!notifyMap.isEmpty()) {
                String message = ("This is your login alert for:\n" + nation.toEmbedString(true));

                for (Map.Entry<Long, Long> entry : notifyMap.entrySet()) {
                    Long userId = entry.getKey();
                    User user = Trocutus.imp().getDiscordApi().getUserById(userId);
                    DBKingdom attacker = DiscordUtil.getKingdom(userId);
                    if (user == null || attacker == null) continue;


                    boolean hasActiveWar = false;
                    for (DBWar war : nation.getActiveWars()) {
                        if (war.attacker_id == attacker.getId() || war.defender_id == attacker.getId()) {
                            hasActiveWar = true;
                            break;
                        }
                    }
                    String messageCustom = message;
                    if (hasActiveWar) {
                        messageCustom += "\n**You have an active war with this nation.**";
                    } else {
                        messageCustom += "\n**You do NOT have an active war with this nation.**";
                    }

                    try {
                        DiscordChannelIO channel = new DiscordChannelIO(RateLimitUtil.complete(user.openPrivateChannel()), null);
                        channel.send(messageCustom);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (nation.active_m() < 3) {
            long activeMinute = nation.lastActiveMs();
            // round to nearest minute
            activeMinute = (activeMinute / 60_000) * 60_000;
            Trocutus.imp().getDB().setSpyActivity(nation.getId(), nation.getProjectBitMask(), nation.getSpies(), activeMinute, nation.getWarPolicy());

            if (previous.active_m() > 240 && Settings.INSTANCE.TASKS.AUTO_FETCH_UID) {
                Trocutus.imp().getExecutor().submit(new CaughtRunnable() {
                    @Override
                    public void runUnsafe() throws Exception {
                        nation.fetchUid();
                    }
                });
            }
        }
    }

    @Subscribe
    public void onKingdomCreate(KingdomCreateEvent event) {
        DBKingdom current = event.getCurrent();

        // Reroll alerts (run on another thread since fetching UID takes time)
        Trocutus.imp().getExecutor().submit(() -> {
            int rerollId = current.isReroll(true);
            if (rerollId > 0) {
                ZonedDateTime yesterday = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);


                String title = "Detected reroll: " + current.getKingdom();
                StringBuilder body = new StringBuilder(current.toMarkdown(null, true));
                if (rerollId != current.getId()) {
                    body.append("\nReroll of: " + TrounceUtil.getKingdomUrl(rerollId));
                }

                AlertUtil.forEachChannel(f -> true, GuildKey.REROLL_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body.toString());
                    }
                });
            }
        });
    }

    @Subscribe
    public void onKingdomPositionChange(KingdomChangeRankEvent event) {
        DBKingdom previous = event.getPrevious();
        DBKingdom current = event.getCurrent();

        checkOfficerChange(previous, current);
    }

    private void checkOfficerChange(DBKingdom previous, DBKingdom current) {
        if (current == null || previous == null || previous.getAlliance_id() == current.getAlliance_id() || current.getActive_m() > 4880) return;
        if (previous.getPosition().ordinal() < Rank.OFFICER.id) {
            DBAlliancePosition position = previous.getAlliancePosition();
            if (position == null || !position.hasAnyAdminPermission()) return;
        }
        DBAlliance alliance = previous.getAlliance(false);

        if (alliance != null && alliance.getRank() < 50)
        {
            String title = current.getKingdom() + " (" + Rank.byId(previous.getPosition()) + ") leaves " + previous.getAllianceName();
            String body = current.toEmbedString(false);
            AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    @Subscribe
    public void onKingdomDelete(KingdomDeleteEvent event) {
        DBKingdom previous = event.getPrevious();
        DBKingdom current = event.getCurrent();

        handleOfficerDelete(previous, current);
        processDeletion(previous, current);
    }

    @Subscribe
    public void onKingdomLeaveAlert(KingdomLeaveAlertEvent event) {
        DBKingdom nation = event.getCurrent();
        if (nation.isVacation() == false) {
            raidAlert(nation);
            enemyAlert(event.getPrevious(), nation);
        }
    }

    @Subscribe
    public void onVM(KingdomChangeVacationEvent event) {
        DBKingdom previous = event.getPrevious();
        DBKingdom current = event.getCurrent();
        if (previous.isVacation() == false && current.isVacation() == true) {
            processVMTransfers(previous, current);
        }
    }

    @Subscribe
    public void onKingdomLeaveVacation(KingdomLeaveVacationEvent event) {
        DBKingdom nation = event.getCurrent();
        if (nation.isVacation() == false && !nation.isBeige()) {
            raidAlert(nation);
            enemyAlert(event.getPrevious(), nation);
        }
    }

    private boolean enemyAlert(DBKingdom previous, DBKingdom current) {
        if (current.active_m() > 7200) return false;
        // enemy leave alert channel
    }

    @Subscribe
    public void onPositionChange(KingdomChangePositionEvent event) {
        DBKingdom current = event.getCurrent();
        DBKingdom previous = event.getPrevious();

        Trocutus.imp().getDB().addRemove(current.getId(), previous.getAlliance_id(), System.currentTimeMillis(), Rank.byId(previous.getPosition()));
    }
}
