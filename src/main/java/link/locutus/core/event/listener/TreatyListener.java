package link.locutus.core.event.listener;

import com.google.common.eventbus.Subscribe;
import link.locutus.core.db.entities.alliance.DBAlliance;

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
    public void onTreatyExtend(TreatyExtendEvent event) {
        update("Extended", event);
    }
    @Subscribe
    public void onTreatyUpgraded(TreatyUpgradeEvent event) {
        update("Upgraded", event);
    }
    @Subscribe
    public void onTreatyExpire(TreatyExpireEvent event) {
        update("Expired", event);
    }

    private void update(String title, TreatyChangeEvent event) {
        Treaty previous = event.getPrevious();

        Treaty current = event.getCurrent();

        Treaty existing = previous == null ? current : previous;
        DBAlliance fromAA = DBAlliance.get(existing.getFromId());
        DBAlliance toAA = DBAlliance.get(existing.getToId());

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
        body.append("From: " + TrounceUtil.getMarkdownUrl(existing.getFromId(), true)).append("\n");
        body.append("To: " + TrounceUtil.getMarkdownUrl(existing.getToId(), true)).append("\n");

        String finalTitle = title;
        AlertUtil.forEachChannel(f -> true, GuildKey.TREATY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                StringBuilder finalBody = new StringBuilder(body);

//                Integer allianceId = guildDB.getOrNull(GuildKey.ALLIANCE_ID);
//                if (allianceId != null)
                {
                    Set<Integer> tracked = guildDB.getAllies(true);
                    if (!tracked.isEmpty()) {
                        finalBody.append("\n\n**IN SPHERE**");

                        tracked.addAll(guildDB.getCoalition("enemies"));
                        tracked.addAll(guildDB.getCoalition(Coalition.DNR));
                        tracked.addAll(guildDB.getCoalition(Coalition.DNR_MEMBER));
                        tracked.addAll(guildDB.getCoalition(Coalition.MASKEDALLIANCES));
                        tracked.addAll(guildDB.getCoalition(Coalition.COUNTER));
                        tracked.addAll(guildDB.getCoalition(Coalition.FA_FIRST));
                        tracked.addAll(guildDB.getCoalition(Coalition.OFFSHORE));
                        tracked.addAll(guildDB.getCoalition(Coalition.OFFSHORING));
                        tracked.addAll(guildDB.getCoalition(Coalition.TRACK_DEPOSITS));

                        if (!tracked.contains(existing.getFromId()) && !tracked.contains(existing.getToId())) {
                            return;
                        }
                    }
                }
                DiscordChannelIO io = new DiscordChannelIO(channel);
                io.create().embed(finalTitle, finalBody.toString()).sendWhenFree();
            }
        });
    }
}
