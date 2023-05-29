package link.locutus.core.api.pojo.pages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

public class FireballInteraction {
    public static class Attacker{
        public String name;
        public int id;
        public String slug;
    }

    public static class AttackerAlliance{
        public String name;
        public int id;
        public String tag;
    }

    public static class Casualties{
        public int cavalry;
        public int archers;
        public int soldiers;
        public int elites;
    }

    public static class Data{
        public Casualties casualties;
    }

    public static class Defender{
        public String name;
        public int id;
        public String slug;
    }

    public static class DefenderAlliance{
        public String name;
        public int id;
        public String tag;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fireball{
        public Data data;
        public Date created_at;
        public Attacker attacker;
        public int id;
        public String type;
        public DefenderAlliance defender_alliance;
        public AttackerAlliance attacker_alliance;
        public Defender defender;
    }


}
