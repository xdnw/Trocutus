package link.locutus.core.api.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;

public class Api {
    public class Abilities{
        public int attack;
        public int defense;
        public int max_mana;
    }

    public class Activity{
        public String label;
        public Date last_active;
    }

    public class Alert{
        public int resource_level;
        public int alert_level;
        public int spell_alert;
    }

    public class Alliance{
        public int id;
        public String name;
        public String tag;
        public String permission;
    }

    public class Army{
        public int total;
        public int soldiers;
        public int cavalry;
        public int archers;
        public int elites;
    }

    public class Boosts{
        public Offensive offensive;
        public Defensive defensive;
        public Economic economic;
    }

    public class Bp{
        public int current;
        public int max;
        public int perTick;
        public int perAttack;
    }

    public class Defensive{
        public int defense;
        public int casualties;
        public int protectedAcres;
        public int protectedGold;
        public int exp;
        public int spellDefense;
    }

    public class Economic{
        public int undevelopedIncome;
        public int developedIncome;
        public int pactIncome;
        public int upkeep;
        public int recruitment;
        public int building;
    }

    public class Energy{
        public Bp bp;
        public Sp sp;
    }

    public class Equipment{
        public Boosts boosts;
        public Sets sets;
    }

    public class Hero{
        public String name;
        public int experience;
        public int level;
        public Mana mana;
        public Abilities abilities;
    }

    public class Income{
        public int income;
        public int upkeep;
        public int net;
    }

    public class Kingdom{
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

    public class Land{
        public int total;
        public int undeveloped;
        public int developed;
        public int forts;
        public double barracks;
        public double training_grounds;
    }

    public class Loyalty{
        public int points;
        public int level;
    }

    public class Mana{
        public int current;
        public int max;
        public int perTick;
    }

    public class Offensive{
        public int attack;
        public int casualties;
        public int bonusAcres;
        public int bonusGold;
        public int exp;
        public int spellAttack;
    }

    public class Pacts{
        public ArrayList<Trade> trade;
    }

    public class Protection{
        public boolean isCurrent;
        public Date expires;
    }

    public class Sets{
        @JsonProperty("Offensive")
        public ArrayList<Object> offensive;
        @JsonProperty("Defensive")
        public ArrayList<Object> defensive;
        @JsonProperty("Economic")
        public ArrayList<Object> economic;
        public ArrayList<Object> all;
    }

    public class Sp{
        public int current;
        public int max;
        public int perTick;
        public int perAttack;
    }

    public class Trade{
        public String kingdom;
        public boolean accepted;
    }

    public class Vacation{
        public boolean isCurrent;
        public Date starts;
    }

    public class Vip{
        public boolean isCurrent;
        public Date expires;
    }
}
