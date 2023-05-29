package link.locutus.core.event.spy;

import link.locutus.Trocutus;
import link.locutus.core.api.game.AttackOrSpell;
import link.locutus.core.db.entities.spells.DBFireball;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.event.GuildScopeEvent;

import java.util.concurrent.TimeUnit;

public class SpellCreateEvent extends GuildScopeEvent {
    private final AttackOrSpell attack;

    public SpellCreateEvent(AttackOrSpell attack) {
        this.attack = attack;
    }

    public AttackOrSpell getSpell() {
        return attack;
    }

    protected void postToGuilds() {
        if (attack.getDate() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) return;
        post(Trocutus.imp().getGuildDBByAA(attack.getAttacker_aa()));
        post(Trocutus.imp().getGuildDBByAA(attack.getDefender_aa()));

        if (attack.getDefender_aa() != 0) new DefensiveSpellEvent(attack).postToGuilds();
        if (attack.getAttacker_aa() != 0) new OffensiveSpellEvent(attack).postToGuilds();
    }
}
