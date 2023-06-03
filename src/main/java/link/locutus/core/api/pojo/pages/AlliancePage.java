package link.locutus.core.api.pojo.pages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

public class AlliancePage {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hero{
        public String name;
        public int level;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Kingdom {
        public int id;
        public String name;
        public String slug;
        public int alliance_id;
        public String alliance_permissions;
        public String total_land;
        public int realm_id;
        public int alert_level;
        public int resource_level;
        public int spell_alert;
        public Date last_active;
        public Date vacation_start;
        public String alert_label;
        public String resource_label;
        public String spell_alert_label;
        public String active;
        public Hero hero;
        public Boolean is_fey;
    }


}
