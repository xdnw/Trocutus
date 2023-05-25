package link.locutus.core.api.pojo.pages;

import java.util.ArrayList;

public class AllianceDirectory {
    public static class Breakdown{
        public int level;
        public int progress;
        public int next;
    }

    public static class Pact{
        public String type;
        public String other;
    }

    public static class Alliance{
        public int id;
        public String name;
        public String tag;
        public String description;
        public int experience;
        public int kingdoms_count;
        public ArrayList<Pact> pacts;
        public Breakdown breakdown;
    }
}
