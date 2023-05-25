package link.locutus.core.api.pojo.pages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

public class AttackInteraction {
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

    public static class AttackerCasualties{
        public int soldiers;
        public int cavalry;
        public int archers;
        public int elites;
    }

    public static class Data{
        public boolean victory;
        public AttackerCasualties attackerCasualties;
        public DefenderCasualties defenderCasualties;
        public int goldLoot;
        public int acreLoot;
        public int defenderAcreLoss;
        public int atkHeroExp;
        public int defHeroExp;
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

    public static class DefenderCasualties{
        public int soldiers;
        public int cavalry;
        public int archers;
        public int elites;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attack {
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
