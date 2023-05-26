package link.locutus.core.db.guild;

import com.google.common.eventbus.Subscribe;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.core.db.entities.DBAlliance;
import link.locutus.core.db.entities.DBAttack;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.entities.DBSpy;
import link.locutus.core.event.attack.DefensiveAttackEvent;
import link.locutus.core.event.attack.OffensiveAttackEvent;
import link.locutus.core.event.spy.DefensiveSpyEvent;
import link.locutus.core.event.spy.OffensiveSpyEvent;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MathMan;
import link.locutus.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.concurrent.TimeUnit;

public class GuildHandler {
    private final GuildDB db;

    public GuildHandler(GuildDB db) {
        this.db = db;

    }

    public boolean onMessageReceived(MessageReceivedEvent event) {
        return true;
    }

    @Subscribe
    public void onDefensiveAttack(DefensiveAttackEvent event) {
        MessageChannel channel = db.getOrNull(GuildKey.DEFENSE_WAR_CHANNEL);
        String title = "Defensive Attack";
        postAttack(channel, title, event.getAttack());
    }

    @Subscribe
    public void onOffensiveAttack(OffensiveAttackEvent event) {
        MessageChannel channel = db.getOrNull(GuildKey.OFFENSIVE_WAR_CHANNEL);
        String title = "Offensive Attack";
        postAttack(channel, title, event.getAttack());
    }

    private final static String soldierChar = "\uD83D\uDC82\u200D\uFE0F";
    private final static String archerChar = "\uD83C\uDFF9";
    private final static String cavalryChar = "\uD83C\uDFC7";
    private final static String eliteChar = "\uD83E\uDDD9\u200D\uFE0F";

    private void postAttack(MessageChannel channel, String title, DBAttack attack) {
        StringBuilder body = new StringBuilder();
        if (attack.victory) {
            body.append(" (Attacker Victory)\n");
        } else {
            body.append(" (Attacker Defeat)\n");
        }
        DBKingdom attacker = DBKingdom.get(attack.attacker_id);
        DBKingdom defender = DBKingdom.get(attack.defender_id);

        String attackerAllianceName = attack.getAttackerAllianceName();
        String attackerKingdomName = attack.getAttackerKingdomName();
        String defenderAllianceName = attack.getDefenderAllianceName();
        String defenderKingdomName = attack.getDefenderKingdomName();
        body.append("## Attacker: `" + attackerKingdomName + "` | `" + attackerAllianceName + "`\n");
        if (attacker != null) {
            body.append("<" + attacker.getUrl("__your_name__") + ">\n");
            body.append("land: `" + attacker.getTotal_land() + "`\n");
        }
        body.append("## Defender: `" + defenderKingdomName + "` | `" + defenderAllianceName + "`\n\n");
        if (defender != null) {
            body.append("<" + defender.getUrl("__your_name__") + ">\n");
            body.append("land: `" + defender.getTotal_land() + "`\n");
        }


        body.append("## Casualties\n");
        body.append("**attacker**\n```");
        body.append(String.format("%6s", attack.attacker_soldiers)).append(" " + soldierChar).append(" | ")
                .append(String.format("%5s", attack.attacker_cavalry)).append(" " + archerChar).append(" | ")
                .append(String.format("%5s", attack.attacker_archers)).append(" " + cavalryChar).append(" | ")
                .append(String.format("%4s", attack.attacker_elites)).append(" " + eliteChar)
                .append("```\n");
        body.append("**defender**\n```");
        body.append(String.format("%6s", attack.defender_soldiers)).append(" " + soldierChar).append(" | ")
                .append(String.format("%5s", attack.defender_cavalry)).append(" " + archerChar).append(" | ")
                .append(String.format("%5s", attack.defender_archers)).append(" " + cavalryChar).append(" | ")
                .append(String.format("%4s", attack.defender_elites)).append(" " + eliteChar)
                .append("```\n");
        body.append("**loot**: $" + MathMan.format(attack.goldLoot) + " | " + MathMan.format(attack.acreLoot) + " acres\n");
        body.append("**acre loss**: " + MathMan.format(attack.defenderAcreLoss) + " acres\n");
        body.append("**hero exp**: " + MathMan.format(attack.atkHeroExp) + "(attacker) | " + MathMan.format(attack.defHeroExp) + " (defender)\n");
        body.append("**date**: " + DiscordUtil.timestamp(attack.date, "R") + "\n");

        new DiscordChannelIO(channel).create().embed(title, body.toString()).send();
    }

    @Subscribe
    public void onDefensiveSpy(DefensiveSpyEvent event) {
        MessageChannel channel = db.getOrNull(GuildKey.DEFENSIVE_SPY_CHANNEL);
        String title = "Defensive Spy";
        postSpy(channel, title, event.getSpy(), true);
    }

    @Subscribe
    public void onOffensiveSpy(OffensiveSpyEvent event) {
        MessageChannel channel = db.getOrNull(GuildKey.OFFENSIVE_SPY_CHANNEL);
        String title = "Offensive Spy";
        postSpy(channel, title, event.getSpy(), true);
    }

    private void postSpy(MessageChannel channel, String title, DBSpy spy, boolean includeInfo) {
        StringBuilder body = new StringBuilder();
        DBKingdom attacker = spy.getAttacker();
        DBKingdom defender = spy.getDefender();

        if (attacker != null){
            body.append("## Attacker: `").append(attacker.getName()).append("` | `").append(attacker.getAllianceName()).append("`\n");
            body.append("<" + attacker.getUrl("__your_name__") + ">\n");
            body.append("land: `" + attacker.getTotal_land() + "`\n");
        } else {
            body.append("## Attacker: `").append(spy.attacker_id).append("` | `").append(spy.attacker_aa).append("`\n");
        }
        if (defender != null) {
            body.append("## Defender: `").append(defender.getName()).append("` | `").append(defender.getAllianceName()).append("`\n");
            body.append("<" + defender.getUrl("__your_name__") + ">\n");
            body.append("land: `" + defender.getTotal_land() + "`\n");
        } else {
            body.append("## Defender: `").append(spy.defender_id).append("` | `").append(spy.defender_aa).append("`\n");
        }

        if (includeInfo) {
            body.append("$" + spy.gold + " (total) | $" + spy.protectedGold + " (protected)\n");
            body.append("Attack Str: " + spy.attack + " | Defense Str: " + spy.defense + "\n```");
            body.append(String.format("%6s", spy.soldiers)).append(" " + soldierChar).append(" | ")
                    .append(String.format("%5s", spy.cavalry)).append(" " + archerChar).append(" | ")
                    .append(String.format("%5s", spy.archers)).append(" " + cavalryChar).append(" | ")
                    .append(String.format("%4s", spy.elites)).append(" " + eliteChar)
                    .append("```\n");
        }

        new DiscordChannelIO(channel).create().embed(title, body.toString()).send();
    }
}
