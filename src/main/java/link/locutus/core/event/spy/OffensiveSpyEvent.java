package link.locutus.core.event.spy;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.DBSpy;

public class OffensiveSpyEvent extends SpyCreateEvent {
    public OffensiveSpyEvent(DBSpy attack) {
        super(attack);
    }

    @Override
    protected void postToGuilds() {
        post(Trocutus.imp().getGuildDBByAA(getSpy().attacker_aa));
    }
}
