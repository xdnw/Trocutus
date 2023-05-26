package link.locutus.core.api.pojo.pages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

public class AllianceMembers {
    public static class Army{
        public int total;
        public int soldiers;
        public int cavalry;
        public int archers;
        public int elites;
    }

    public static class Hero {
        public String name;
        public int level;
        public String ruleset;
    }

    public static class Land {
        public int undeveloped;
        public int developed;
        public int total;
        public int forts;
        public double barracks;
        public int training_grounds;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AllianceMember {
        public int id;
        public String name;
        public String slug;
        public int alliance_id;
        public String alliance_permissions;
        public Land land;
        public Army army;
        public int realm_id;
        public String ruleset;
        public int alert_level;
        public int resource_level;
        public int spell_alert;
        public Date last_active;
        public int loyalty_points;
        public Date vacation_start;
        public Date vip_expires;
        public Stats stats;
        public String alert_label;
        public String resource_label;
        public String spell_alert_label;
        public String active;
        public Hero hero;
    }

    public static class Stats {
        public int attack;
        public int defense;
    }


}
