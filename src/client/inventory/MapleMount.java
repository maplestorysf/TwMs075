package client.inventory;

import client.MapleBuffStat;
import client.MapleCharacter;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.io.Serializable;

import database.DatabaseConnection;
import server.Randomizer;
import tools.packet.CWvsContext;

public class MapleMount implements Serializable {

    private static final long serialVersionUID = 9179541993413738569L;
    private int itemid, skillid, exp;
    private byte fatigue, level;
    private transient boolean changed = false;
    private long lastFatigue = 0;
    private transient WeakReference<MapleCharacter> owner;

    public MapleMount(MapleCharacter owner, int id, int skillid, byte fatigue, byte level, int exp) {
        this.itemid = id;
        this.skillid = skillid;
        this.fatigue = fatigue;
        this.level = level;
        this.exp = exp;
        this.owner = new WeakReference<>(owner);
    }

    public void saveMount(final int charid) throws SQLException {
        if (!changed) {
            return;
        }
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("UPDATE mountdata set `Level` = ?, `Exp` = ?, `Fatigue` = ? WHERE characterid = ?");
        ps.setByte(1, level);
        ps.setInt(2, exp);
        ps.setByte(3, fatigue);
        ps.setInt(4, charid);
        ps.executeUpdate();
        ps.close();
    }

    public int getItemId() {
        return itemid;
    }

    public int getSkillId() {
        return skillid;
    }

    public byte getFatigue() {
        return fatigue;
    }

    public int getExp() {
        return exp;
    }

    public byte getLevel() {
        return level;
    }

    public void setItemId(int c) {
        changed = true;
        this.itemid = c;
    }

    public void setFatigue(byte amount) {
        changed = true;
        fatigue += amount;
        if (fatigue < 0) {
            fatigue = 0;
        }
    }

    public void setExp(int c) {
        changed = true;
        this.exp = c;
    }

    public void setLevel(byte c) {
        changed = true;
        this.level = c;
    }

    public void increaseFatigue() {
        changed = true;
        this.fatigue++;
        if (fatigue > 100 && owner.get() != null) {
            owner.get().cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
        }
        update();
    }

    public final boolean canTire(long now) {
        return lastFatigue > 0 && lastFatigue + 30000 < now;
    }

    public void startSchedule() {
        lastFatigue = System.currentTimeMillis();
    }

    public void cancelSchedule() {
        lastFatigue = 0;
    }

    public void increaseExp() {
        int e;
        if (level >= 1 && level <= 7) {
            e = Randomizer.nextInt(10) + 15;
        } else if (level >= 8 && level <= 15) {
            e = Randomizer.nextInt(13) + 15 / 2;
        } else if (level >= 16 && level <= 24) {
            e = Randomizer.nextInt(23) + 18 / 2;
        } else {
            e = Randomizer.nextInt(28) + 25 / 2;
        }
        setExp(exp + e);
    }

    public void update() {
        final MapleCharacter chr = owner.get();
        if (chr != null) {
//	    cancelSchedule();
            chr.getMap().broadcastMessage(CWvsContext.updateMount(chr, false));
        }
    }
}
