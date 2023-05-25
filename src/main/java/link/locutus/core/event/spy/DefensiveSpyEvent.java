package link.locutus.core.event.spy;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.DBSpy;

public class DefensiveSpyEvent extends SpyCreateEvent {
    public DefensiveSpyEvent(DBSpy attack) {
        super(attack);
    }

    @Override
    protected void postToGuilds() {
        post(Trocutus.imp().getGuildDBByAA(getSpy().defender_aa));
    }
}
