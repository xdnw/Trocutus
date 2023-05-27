package link.locutus.core.event.kingdom;

import link.locutus.core.db.entities.kingdom.DBKingdom;

public class KingdomUpdateEvent extends KingdomEvent {
    public KingdomUpdateEvent(DBKingdom from, DBKingdom to) {
        super(from, to);
    }


    @Override
    public void post() {
        if (getFrom() != null && getTo() != null) {
            if (getFrom().getPosition() != getTo().getPosition()) {
                // KingdomChangePositionEvent
                new KingdomChangePositionEvent(getFrom(), getTo()).post();
            }
            if (!getFrom().getName().equalsIgnoreCase(getTo().getName())) {
                new KingdomChangeNameEvent(getFrom(), getTo()).post();
            }
            if (getFrom().getTotal_land() != getTo().getTotal_land()) {
                new KingdomChangeLandEvent(getFrom(), getTo()).post();
            }
            if (getFrom().getAlert_level() != getTo().getAlert_level()) {
                new KingdomChangeAlertEvent(getFrom(), getTo()).post();
            }
            if (getFrom().getResource_level() != getTo().getResource_level()) {
                new KingdomChangeResourceEvent(getFrom(), getTo()).post();
            }
            if (getFrom().getSpell_alert() != getTo().getSpell_alert()) {
                new KingdomChangeSpellAlertEvent(getFrom(), getTo()).post();
            }
            if (getTo().getLast_active() > getFrom().getLast_active()) {
                new KingdomChangeActiveEvent(getFrom(), getTo()).post();
            }
            if (getTo().isVacation() != getFrom().isVacation()) {
                new KingdomChangeVacationEvent(getFrom(), getTo()).post();
            }
            if (getTo().getHero() != getFrom().getHero()) {
                new KingdomChangeHeroEvent(getFrom(), getTo()).post();
            }
            if (getTo().getHero_level() != getFrom().getHero_level()) {
                new KingdomChangeHeroLevelEvent(getFrom(), getTo()).post();
            }
        }
    }
}
