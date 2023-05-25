package link.locutus.core.event;

import link.locutus.Trocutus;

public class Event {
    public void post() {
        long start = System.currentTimeMillis();
        Trocutus.post(this);
        long diff = System.currentTimeMillis() - start;
        if (diff > 100) {
            System.out.println("Posted " + this.getClass().getSimpleName() + "(took " + diff + ")");
        }
    }
}
