package link.locutus.core.event.listener;

import com.google.common.eventbus.Subscribe;
import link.locutus.Trocutus;
import link.locutus.core.api.alliance.AllianceMetric;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.event.alliance.AllianceCreateEvent;
import link.locutus.util.AlertUtil;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MarkupUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import link.locutus.util.TrounceUtil;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class AllianceListener {

    public AllianceListener() {
        Trocutus.imp().getScheduler().scheduleAtFixedRate(new Runnable() {
            private long lastTurn = 0;
            @Override
            public void run() {
                if (TimeUtil.getTurn() != lastTurn) {
                    lastTurn = TimeUtil.getTurn();
                    AllianceMetric.update();
                }
            }
        }, 60, 1, TimeUnit.MINUTES);
    }

    @Subscribe
    public void onNewAlliance(AllianceCreateEvent event) {
        DBAlliance alliance = event.getTo();
        int aaId = alliance.getId();

        Set<DBKingdom> members = alliance.getKingdoms();
        String title = "Created: " + alliance.getName();

        StringBuilder body = new StringBuilder();

        for (DBKingdom member : members) {
            if (member.getPosition().ordinal() < Rank.ADMIN.ordinal()) continue;
            Map.Entry<Integer, Rank> lastAA = member.getPreviousAlliance();

            body.append("Leader: " + MarkupUtil.markdownUrl(member.getName(), member.getUrl(null)) + "\n");

            if (lastAA != null) {
                String previousAAName = TrounceUtil.getAllianceName(lastAA.getKey());
                body.append("- " + member.getName() + " previously " + lastAA.getValue() + " in " + previousAAName + "\n");

                GuildDB db = Trocutus.imp().getRootCoalitionServer();
                if (db != null) {
                    Set<String> coalitions = db.findCoalitions(lastAA.getKey());
                    if (!coalitions.isEmpty()) {
                        body.append("- in coalitions: `" + StringMan.join(coalitions, ",") + "`\n");
                    }
                }
            }

            Map<Integer, Integer> wars = new HashMap<>();
            for (DBAttack activeWar : member.getRecentAttacks(TimeUnit.DAYS.toMillis(1))) {
                int otherAA = activeWar.attacker_id == member.getId() ? activeWar.defender_aa : activeWar.attacker_aa;
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
