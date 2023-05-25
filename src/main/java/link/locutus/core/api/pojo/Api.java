package link.locutus.core.api.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;

public class Api {
    public static class Abilities {
        public int attack;
        public int defense;
        public int max_mana;
    }

    public static class Activity {
        public String label;
        public Date last_active;
    }

    public static class Alert {
        public int resource_level;
        public int alert_level;
        public int spell_alert;
    }

    public static class Alliance {
        public int id;
        public String name;
        public String tag;
        public String permission;
    }

    public static class Army {
        public int total;
        public int soldiers;
        public int cavalry;
        public int archers;
        public int elites;
    }

    public static class Boosts {
        public Offensive offensive;
        public Defensive defensive;
        public Economic economic;
    }

    public static class Bp {
        public int current;
        public int max;
        public int perTick;
        public int perAttack;
    }

    public static class Defensive {
        public int defense;
        public int casualties;
        public int protectedAcres;
        public int protectedGold;
        public int exp;
        public int spellDefense;
    }

    public static class Economic {
        public int undevelopedIncome;
        public int developedIncome;
        public int pactIncome;
        public int upkeep;
        public int recruitment;
        public int building;
    }

    public static class Energy {
        public Bp bp;
        public Sp sp;
    }

    public static class Equipment {
        public Boosts boosts;
        public Sets sets;
    }

    public static class Hero {
        public String name;
        public int experience;
        public int level;
        public Mana mana;
        public Abilities abilities;
    }

    public static class Income {
        public int income;
        public int upkeep;
        public int net;
    }

    public static class Root {
        public Kingdom kingdom;
    }

    public static class Kingdom{
        public String name;
        public String slug;
        public int gold;
        public int held_gold;
        public Alert alert;
        public Energy energy;
        public Land land;
        public Income income;
        public Army army;
        public Activity activity;
        public Protection protection;
        public Vacation vacation;
        public Vip vip;
        public Loyalty loyalty;
        public Pacts pacts;
        public Alliance alliance;
        public Hero hero;
        public Equipment equipment;
    }

    public static class Land{
        public int total;
        public int undeveloped;
        public int developed;
        public int forts;
        public double barracks;
        public double training_grounds;
    }

    public static class Loyalty{
        public int points;
        public int level;
    }

    public static class Mana{
        public int current;
        public int max;
        public int perTick;
    }

    public static class Offensive{
        public int attack;
        public int casualties;
        public int bonusAcres;
        public int bonusGold;
        public int exp;
        public int spellAttack;
    }

    public static class Pacts{
        public ArrayList<Trade> trade;
    }

    public static class Protection{
        public boolean isCurrent;
        public Date expires;
    }

    public static class Sets{
        @JsonProperty("Offensive")
        public ArrayList<Object> offensive;
        @JsonProperty("Defensive")
        public ArrayList<Object> defensive;
        @JsonProperty("Economic")
        public ArrayList<Object> economic;
        public ArrayList<Object> all;
    }

    public static class Sp{
        public int current;
        public int max;
        public int perTick;
        public int perAttack;
    }

    public static class Trade{
        public String kingdom;
        public boolean accepted;
    }

    public static class Vacation{
        public boolean isCurrent;
        public Date starts;
    }

    public static class Vip{
        public boolean isCurrent;
        public Date expires;
    }
}
