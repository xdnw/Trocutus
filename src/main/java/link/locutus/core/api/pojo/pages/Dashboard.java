package link.locutus.core.api.pojo.pages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

public class Dashboard {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Alliance{
        public int id;
        public String name;
        public String tag;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hero{
        public String name;
        public int level;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Loyalty{
        public int total;
        public int level;
        public Perks perks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Perks{
        public int income;
        public int attack;
        public int defense;
        public int heroExp;
        public int manaRegen;
        public int spellCost;
        public int upkeep;
        public int casualtyOff;
        public int casualtyDef;
        public int exploring;
        public int bonusTradePacts;
        public boolean sellCredits;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Realm{
        public int id;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Kingdom{
        public int id;
        public String name;
        public String slug;
        public int realm_id;
        public int alliance_id;
        public String alliance_permissions;
        public int alert_level;
        public int resource_level;
        public int spell_alert;
        public Date vacation_start;
        public int loyalty_points;
        public Date vip_expires;
        public String alert_label;
        public String resource_label;
        public String spell_alert_label;
        public boolean on_vacation;
        public Loyalty loyalty;
        public Realm realm;
        public Hero hero;
        public Alliance alliance;
    }


}
