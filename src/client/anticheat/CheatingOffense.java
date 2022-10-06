package client.anticheat;

public enum CheatingOffense {

    快速召喚獸攻擊((byte) 5, 10000, 30, (byte) 3),// 10秒內觸發三十次即封鎖
    快速攻擊((byte) 5, 60000, 50, (byte) 2),// 60秒內觸發五十次即DC
    快速攻擊2((byte) 5, 60000, 50, (byte) 2),// 60秒內觸發五十次即DC
    MOVE_MONSTERS((byte) 5, 30000, 500, (byte) 2),
    FAST_HP_MP_REGEN((byte) 5, 20000, 100, (byte) 2),
    相同商害((byte) 5, 180000, 100, (byte) 2),// 180秒內觸發一百次即DC
    ATTACK_WITHOUT_GETTING_HIT((byte) 1, 30000, 1200, (byte) 0),
    魔法傷害過高((byte) 5, 30000),// 30秒內觸發五次即DC
    魔法傷害過高2((byte) 10, 180000),// 180秒內觸發十次即DC
    傷害過高((byte) 5, 30000),// 30秒內觸發五次即DC
    傷害過高2((byte) 10, 180000),// 180秒內觸發十次即DC
    全圖打((byte) 5, 180000, 20, (byte) 2), // 180秒內觸二十次即DC
    召喚獸全圖打((byte) 5, 180000, 20, (byte) 2),// 180秒內觸二十次即DC
    REGEN_HIGH_HP((byte) 10, 30000, 1000, (byte) 2),
    REGEN_HIGH_MP((byte) 10, 30000, 1000, (byte) 2),
    ITEMVAC_CLIENT((byte) 3, 10000, 100),
    ITEMVAC_SERVER((byte) 2, 10000, 100, (byte) 2),
    PET_ITEMVAC_CLIENT((byte) 3, 10000, 100),
    PET_ITEMVAC_SERVER((byte) 2, 10000, 100, (byte) 2),
    USING_FARAWAY_PORTAL((byte) 1, 60000, 100, (byte) 0),
    FAST_TAKE_DAMAGE((byte) 1, 60000, 50),// 60秒內 觸發50次 封鎖
    HIGH_AVOID((byte) 5, 180000, 100),
    召喚獸攻擊怪物數量異常((byte) 1, 600000, 7, (byte) 3),// 600秒內 觸發7次 自訂封鎖
    攻擊怪物數量異常((byte) 1, 600000, 7, (byte) 3),// 600秒內 觸發7次 自訂封鎖
    技能攻擊次數異常((byte) 1, 600000, 7, (byte) 3),// 600秒內 觸發7次 自訂封鎖
    群體治癒攻擊不死系怪物((byte) 1, 600000, 7, (byte) 3),// 600秒內 觸發7次 自訂封鎖
	無MP使用技能((byte) 1, 60000, 5, (byte) 3),// 60秒內 觸發5次 自訂封鎖
    吸怪((byte) 3, 7000, 7, (byte) 3),// 7秒內 觸發7次 自訂封鎖
    楓幣炸彈異常((byte) 1, 300000),
    ATTACKING_WHILE_DEAD((byte) 1, 300000),
    USING_UNAVAILABLE_ITEM((byte) 1, 300000),
    FAMING_SELF((byte) 1, 300000), // purely for marker reasons (appears in the database)
    FAMING_UNDER_15((byte) 1, 300000);
    private final byte points;
    private final long validityDuration;
    private final int autobancount;
    private byte bantype = 0; // 0 = Disabled, 1 = 封鎖處理, 2 = 斷線處理, 3 = 自訂

    public final byte getPoints() {
        return points;
    }

    public final long getValidityDuration() {
        return validityDuration;
    }

    public final boolean shouldAutoban(final int count) {
        if (autobancount < 0) {
            return false;
        }
        return count >= autobancount;
    }

    public final byte getBanType() {
        return bantype;
    }

    public final void setEnabled(final boolean enabled) {
        bantype = (byte) (enabled ? 1 : 0);
    }

    public final boolean isEnabled() {
        return bantype >= 1;
    }

    private CheatingOffense(final byte points, final long validityDuration) {
        this(points, validityDuration, -1, (byte) 2);
    }

    private CheatingOffense(final byte points, final long validityDuration, final int autobancount) {
        this(points, validityDuration, autobancount, (byte) 1);
    }

    private CheatingOffense(final byte points, final long validityDuration, final int autobancount, final byte bantype) {
        this.points = points;
        this.validityDuration = validityDuration;
        this.autobancount = autobancount;
        this.bantype = bantype;
    }
}
