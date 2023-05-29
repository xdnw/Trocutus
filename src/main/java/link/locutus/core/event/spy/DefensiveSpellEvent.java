package link.locutus.core.event.spy;

import link.locutus.Trocutus;
import link.locutus.core.api.game.AttackOrSpell;
import link.locutus.core.db.entities.spells.DBSpy;

public class DefensiveSpellEvent extends SpellCreateEvent {
    public DefensiveSpellEvent(AttackOrSpell attack) {
        super(attack);
    }

    @Override
    protected void postToGuilds() {
        post(Trocutus.imp().getGuildDBByAA(getSpell().getDefender_aa()));
    }
}
