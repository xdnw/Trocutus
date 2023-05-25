package link.locutus.core.event.attack;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.DBAttack;
import link.locutus.core.event.GuildScopeEvent;

import java.util.concurrent.TimeUnit;

public class AttackCreateEvent extends GuildScopeEvent {
    private final DBAttack attack;

    public AttackCreateEvent(DBAttack attack) {
        this.attack = attack;
    }

    public DBAttack getAttack() {
        return attack;
    }

    protected void postToGuilds() {
        if (attack.date < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) return;
        post(Trocutus.imp().getGuildDBByAA(attack.attacker_aa));
        post(Trocutus.imp().getGuildDBByAA(attack.defender_aa));

        if (attack.defender_aa != 0) new DefensiveAttackEvent(attack).postToGuilds();
        if (attack.attacker_aa != 0) new OffensiveAttackEvent(attack).postToGuilds();
    }
}
