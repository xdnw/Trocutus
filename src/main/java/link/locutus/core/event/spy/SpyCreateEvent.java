package link.locutus.core.event.spy;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.event.GuildScopeEvent;

import java.util.concurrent.TimeUnit;

public class SpyCreateEvent extends GuildScopeEvent {
    private final DBSpy attack;

    public SpyCreateEvent(DBSpy attack) {
        this.attack = attack;
    }

    public DBSpy getSpy() {
        return attack;
    }

    protected void postToGuilds() {
        if (attack.date < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) return;
        post(Trocutus.imp().getGuildDBByAA(attack.attacker_aa));
        post(Trocutus.imp().getGuildDBByAA(attack.defender_aa));

        if (attack.defender_aa != 0) new DefensiveSpyEvent(attack).postToGuilds();
        if (attack.attacker_aa != 0) new OffensiveSpyEvent(attack).postToGuilds();
    }
}
