package link.locutus.core.api.pojo.pages;

import java.util.Date;

public class SpyInteraction {
    public static class Attacker{
        public int id;
        public String name;
        public String slug;
    }

    public static class AttackerAlliance{
        public int id;
        public String name;
        public String tag;
    }

    public static class Data{
        public int protectedGold;
        public int gold;
        public int attack;
        public int defense;
        public int soldiers;
        public int cavalry;
        public int archers;
        public int elites;
    }

    public static class Defender{
        public int id;
        public String name;
        public String slug;
    }

    public static class DefenderAlliance{
        public int id;
        public String name;
        public String tag;
    }

    public static class Spy {
        public int id;
        public String type;
        public Data data;
        public Date created_at;
        public Attacker attacker;
        public AttackerAlliance attacker_alliance;
        public Defender defender;
        public DefenderAlliance defender_alliance;
    }


}
