package server;

import client.MapleCharacter;
import handling.world.World;
import tools.packet.CWvsContext;

/**
 *
 * @author KyleShum
 */
public class MapleAchievement {

    private String name;
    private int reward;
    private boolean notice;

    public MapleAchievement(String name, int reward) {
        this.name = name;
        this.reward = reward;
        this.notice = true;
    }

    public MapleAchievement(String name, int reward, boolean notice) {
        this.name = name;
        this.reward = reward;
        this.notice = notice;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getReward() {
        return reward;
    }

    public void setReward(int reward) {
        this.reward = reward;
    }

    public boolean getNotice() {
        return notice;
    }

    public void finishAchievement(MapleCharacter chr) {
        chr.modifyCSPoints(2, reward, false);
        chr.setAchievementFinished(MapleAchievements.getInstance().getByMapleAchievement(this));
        if (notice && !chr.isGM()) {
            World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "[恭喜] 玩家 " + chr.getName() + " " + name + "，獲得 " + reward + "  楓葉點數獎勵。"));
        } else {
            chr.getClient().sendPacket(CWvsContext.serverNotice(5, "[恭喜] 您" + name + "，獲得 " + reward + " 楓葉點數獎勵。"));
        }
    }
}
