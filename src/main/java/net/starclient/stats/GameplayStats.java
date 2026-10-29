package net.starclient.stats;

public class GameplayStats {
    public long playtimeSeconds;
    public long blocksPlaced;
    public long blocksMined;
    public long playerKills;
    public long mobKills;
    public long deaths;
    public double heartsLost;
    public double heartsRegenerated;
    public long timesEaten;
    public long worldsJoined;
    public long serversJoined;
    public long chatMessagesSent;
    public long itemsCrafted;
    public long itemsBroken;
    public double blocksTraveled;
    public long worldsCreated;

    public void add(GameplayStats other) {
        if (other == null) return;
        this.playtimeSeconds += other.playtimeSeconds;
        this.blocksPlaced += other.blocksPlaced;
        this.blocksMined += other.blocksMined;
        this.playerKills += other.playerKills;
        this.mobKills += other.mobKills;
        this.deaths += other.deaths;
        this.heartsLost += other.heartsLost;
        this.heartsRegenerated += other.heartsRegenerated;
        this.timesEaten += other.timesEaten;
        this.worldsJoined += other.worldsJoined;
        this.serversJoined += other.serversJoined;
        this.chatMessagesSent += other.chatMessagesSent;
        this.itemsCrafted += other.itemsCrafted;
        this.itemsBroken += other.itemsBroken;
        this.blocksTraveled += other.blocksTraveled;
        this.worldsCreated += other.worldsCreated;
    }

    public GameplayStats copy() {
        GameplayStats c = new GameplayStats();
        c.add(this);
        return c;
    }
}
