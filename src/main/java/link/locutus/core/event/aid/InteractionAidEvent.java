package link.locutus.core.event.aid;

import link.locutus.core.db.entities.spells.DBAid;

public class InteractionAidEvent {
    public DBAid aid;

    public InteractionAidEvent(DBAid aid) {
        this.aid = aid;
    }

    public DBAid getAid() {
        return aid;
    }
}
