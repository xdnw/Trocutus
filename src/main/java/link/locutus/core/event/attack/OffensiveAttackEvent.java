package link.locutus.core.event.attack;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.DBAttack;

public class OffensiveAttackEvent extends AttackCreateEvent{
    public OffensiveAttackEvent(DBAttack attack) {
        super(attack);
    }

    @Override
    protected void postToGuilds() {
        post(Trocutus.imp().getGuildDBByAA(getAttack().attacker_aa));
    }
}
