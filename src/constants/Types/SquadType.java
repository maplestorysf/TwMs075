package constants.Types;

import java.io.Serializable;

/**
 *
 * @author Windyboy
 */
public enum SquadType implements Serializable {

    龍王遠征隊(160100, 240060200),
    混沌龍王遠征隊(160101, 240060201),
    炎魔遠征隊(160102, 280030000),
    混沌炎魔遠征隊(160103, 280030001),
    皮卡啾遠征隊(160104, 270050100),
    混沌皮卡啾遠征隊(160105, 270051100),
    巴洛谷遠征隊(160106, 105100300),
    凡雷恩遠征隊(160107, 211070100),
    雄獅遠征隊(160108, 551030200),
    希拉遠征隊(160109, 262030300),
    混沌希拉遠征隊(160110, 262031300),
    阿卡伊農遠征隊(160111, 272020200),
    混沌阿卡伊農遠征隊(160112, 272020110),
    女皇遠征隊(160113, 271040100),
    渾沌女皇遠征隊(160114, 271040100),;

    private int quest = 0, map = 0;

    private SquadType(int questid, int mapid) {
        this.quest = questid;
        this.map = mapid;
    }

    public final int getMap() {
        return map;
    }

    public final int getQuest() {
        return quest;
    }

    public static String nameOfbyMap(int id) {
        for (SquadType q : SquadType.values()) {
            if (q.getMap() == id) {
                return q.name();
            }
        }
        return "未知任務";
    }

    public static String nameOf(int id) {
        for (SquadType q : SquadType.values()) {
            if (q.getQuest() == id) {
                return q.name();
            }
        }
        return "未知任務";
    }
}
