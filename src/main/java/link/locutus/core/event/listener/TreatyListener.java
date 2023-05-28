package link.locutus.core.event.listener;

import com.google.common.eventbus.Subscribe;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.alliance.DBTreaty;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Coalition;
import link.locutus.core.event.treaty.TreatyCancelEvent;
import link.locutus.core.event.treaty.TreatyCreateEvent;
import link.locutus.core.event.treaty.TreatyDowngradeEvent;
import link.locutus.core.event.treaty.TreatyEvent;
import link.locutus.core.event.treaty.TreatyUpgradeEvent;
import link.locutus.util.AlertUtil;
import link.locutus.util.TrounceUtil;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.Set;
import java.util.function.BiConsumer;

public class TreatyListener {
    @Subscribe
    public void onTreatyCreate(TreatyCreateEvent event) {
        update("Signed", event);
    }
    @Subscribe
    public void onTreatyCancel(TreatyCancelEvent event) {
        update("Cancelled", event);
    }
    @Subscribe
    public void onTreatyDowngrade(TreatyDowngradeEvent event) {
        update("Downgraded", event);
    }
    @Subscribe
    public void onTreatyUpgraded(TreatyUpgradeEvent event) {
        update("Upgraded", event);
    }

    private void update(String title, TreatyEvent event) {
        DBTreaty previous = event.getFrom();

        DBTreaty current = event.getTo();

        DBTreaty existing = previous == null ? current : previous;
        DBAlliance fromAA = DBAlliance.get(existing.getFrom_id());
        DBAlliance toAA = DBAlliance.get(existing.getTo_id());

        // Ignore treaty changes from alliance deletion
        if (fromAA == null || toAA == null) return;

        if (previous == null) {
            title += " " + current.getType();
        } else if (current == null) {
            title += " " + previous.getType();
        } else  if(current.getType() != previous.getType()) {
            title += " " + (previous.getType() + "->" + current.getType());
        } else {
            title += " " + current.getType();
        }

        StringBuilder body = new StringBuilder();
        body.append("From: " + TrounceUtil.getMarkdownUrl(existing.getFrom_id(), true)).append("\n");
        body.append("To: " + TrounceUtil.getMarkdownUrl(existing.getTo_id(), true)).append("\n");

        String finalTitle = title;
        AlertUtil.forEachChannel(f -> true, GuildKey.TREATY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                StringBuilder finalBody = new StringBuilder(body);

//                Integer allianceId = guildDB.getOrNull(GuildKey.ALLIANCE_ID);
//                if (allianceId != null)
//                {
//                    Set<Integer> tracked = guildDB.getAllies(true);
//                    if (!tracked.isEmpty()) {
//                        finalBody.append("\n\n**IN SPHERE**");
//
//                        tracked.addAll(guildDB.getCoalition("enemies"));
//                        tracked.addAll(guildDB.getCoalition(Coalition.DNR));
//                        tracked.addAll(guildDB.getCoalition(Coalition.DNR_MEMBER));
//                        tracked.addAll(guildDB.getCoalition(Coalition.MASKED_ALLIANCES));
//                        tracked.addAll(guildDB.getCoalition(Coalition.COUNTER));
//                        tracked.addAll(guildDB.getCoalition(Coalition.FA_FIRST));
//
//                        if (!tracked.contains(existing.getFrom_id()) && !tracked.contains(existing.getTo_id())) {
//                            return;
//                        }
//                    }
//                }
                DiscordChannelIO io = new DiscordChannelIO(channel);
                io.create().embed(finalTitle, finalBody.toString()).sendWhenFree();
            }
        });
    }
}
