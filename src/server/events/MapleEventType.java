package server.events;

public enum MapleEventType {

    農夫的樂趣(new int[]{109080000}), //just using one
    採集的樂趣(new int[]{109080010}), //just using one
    障礙競走(new int[]{109040000, 109040001, 109040002, 109040003, 109040004}),
    向上攀升(new int[]{109030001, 109030002, 109030003}),
    選邊站(new int[]{109020001}),
    滾雪球(new int[]{109060000}), //just using one
    生存遊戲(new int[]{809040000, 809040100}),
    黃金傳說(new int[]{109010000, 109010100, 109010102, 109010103, 109010104, 109010105, 109010106, 109010107, 109010108, 109010109, 109010110, 109010200, 109010201, 109010202, 109010203, 109010204, 109010205, 109010206});
    public int[] mapids;

    private MapleEventType(int[] mapids) {
        this.mapids = mapids;
    }

    public static final MapleEventType getByString(final String splitted) {
        for (MapleEventType t : MapleEventType.values()) {
            if (t.name().equalsIgnoreCase(splitted)) {
                return t;
            }
        }
        return null;
    }
}
