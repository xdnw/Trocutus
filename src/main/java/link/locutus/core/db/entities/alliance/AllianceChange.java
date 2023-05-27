package link.locutus.core.db.entities.alliance;


import link.locutus.core.api.alliance.Rank;

public class AllianceChange {
    public int fromAA, toAA;
    public Rank fromRank, toRank;
    public long date;

    public AllianceChange(Integer fromAA, Integer toAA, Rank fromRank, Rank toRank, long date) {
        if (fromAA == null) fromAA = 0;
        if (toAA == null) toAA = 0;
        if (fromRank == null) fromRank = Rank.NONE;
        if (toRank == null) fromRank = Rank.NONE;
        this.fromAA = fromAA;
        this.toAA = toAA;
        this.fromRank = fromRank;
        this.toRank = toRank;
        this.date = date;
    }
}
