package link.locutus.core.event.listener;

import com.google.common.eventbus.Subscribe;
import link.locutus.Trocutus;
import link.locutus.core.api.alliance.AllianceMetric;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.event.alliance.AllianceCreateEvent;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MarkupUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class AllianceListener {

    @Subscribe
    public void turnChangeEvent(TurnChangeEvent event) {
        AllianceMetric.update(80);
    }

    @Subscribe
    public void onNewAlliance(AllianceCreateEvent event) {
        DBAlliance alliance = event.getCurrent();
        int aaId = alliance.getId();

        Set<DBKingdom> members = alliance.getKingdoms();
        String title = "Created: " + alliance.getName();

        StringBuilder body = new StringBuilder();

        for (DBKingdom member : members) {
            if (member.getPosition().ordinal() < Rank.HEIR.id) continue;
            Map.Entry<Integer, Rank> lastAA = member.getPreviousAlliance();

            body.append("Leader: " + MarkupUtil.markdownUrl(member.getKingdom(), member.getKingdomUrl()) + "\n");

            if (lastAA != null) {
                String previousAAName = Trocutus.imp().getDB().getAllianceName(lastAA.getKey());
                body.append("- " + member.getKingdom() + " previously " + lastAA.getValue() + " in " + previousAAName + "\n");

                GuildDB db = Trocutus.imp().getRootCoalitionServer();
                if (db != null) {
                    Set<String> coalitions = db.findCoalitions(lastAA.getKey());
                    if (!coalitions.isEmpty()) {
                        body.append("- in coalitions: `" + StringMan.join(coalitions, ",") + "`\n");
                    }
                }
            }

            Map<Integer, Integer> wars = new HashMap<>();
            for (DBWar activeWar : member.getRecentWars(TimeUnit.DAYS.toMillis(1))) {
                int otherAA = activeWar.attacker_id == member.getKingdom_id() ? activeWar.defender_aa : activeWar.attacker_aa;
                if (otherAA == 0) continue;
                wars.put(otherAA, wars.getOrDefault(otherAA, 0) + 1);
            }

            if (!wars.isEmpty()) body.append("Wars:\n");
            for (Map.Entry<Integer, Integer> entry : wars.entrySet()) {
                body.append("- " + entry.getValue() + " wars vs " + TrounceUtil.getMarkdownUrl(entry.getKey(), true) + "\n");
            }
        }

        body.append(TrounceUtil.getUrl(aaId, true));

        AlertUtil.forEachChannel(f -> true, GuildKey.TREATY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                DiscordUtil.createEmbedCommand(channel, title, body.toString());
            }
        });
    }
}
