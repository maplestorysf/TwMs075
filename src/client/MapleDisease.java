package client;

import handling.Buffstat;
import java.io.Serializable;
import server.Randomizer;

public enum MapleDisease implements Serializable, Buffstat {

    STUN(0x20000, 1, 123),
    POISON(0x40000, 1, 125),
    SEAL(0x80000, 1, 120),
    DARKNESS(0x100000, 1, 121),
    WEAKEN(0x40000000, 1, 122),
    CURSE(0x80000000, 1, 124),
    SLOW(0x1, 2, 126),
    MORPH(0x2, 2, 172),
    SEDUCE(0x80, 2, 128),
    ZOMBIFY(0x4000, 2, 133),
    REVERSE_DIRECTION(0x80000, 2, 132),
    POTION(0x800, 3, 134),
    SHADOW(0x1000, 3, 135), //receiving damage/moving
    BLIND(0x2000, 3, 136),
    FREEZE(0x80000, 3, 137),
    DISABLE_POTENTIAL(0x4000000, 4, 138),
    TORNADO(0x40000000, 4, 173),
    FLAG(0x2, 6, 799); // PVP - Capture the Flag
    // 127 = 1 snow?
    // 129 = turn?
    // 131 = poison also, without msg
    // 133, become undead?..50% recovery?
    // 0x100 is disable skill except buff
    private static final long serialVersionUID = 0L;
    private int i;
    private int first;
    private int disease;

    private MapleDisease(int i, int first, int disease) {
        this.i = i;
        this.first = first;
        this.disease = disease;
    }

    public int getPosition() {
        return first;
    }

    public int getValue() {
        return i;
    }

    public int getDisease() {
        return disease;
    }

    public static final MapleDisease getRandom() {
        while (true) {
            for (MapleDisease dis : MapleDisease.values()) {
                if (Randomizer.nextInt(MapleDisease.values().length) == 0) {
                    return dis;
                }
            }
        }
    }

    public static final MapleDisease getBySkill(final int skill) {
        for (MapleDisease d : MapleDisease.values()) {
            if (d.getDisease() == skill) {
                return d;
            }
        }
        return null;
    }

    public static final MapleDisease getByMobSkill(final int skill) {
        switch (skill) {
            case 120:
                return SEAL;
            case 121:
                return DARKNESS;
            case 122:
                return WEAKEN;
            case 123:
                return STUN;
            case 124:
                return CURSE;
            case 125:
                return POISON;
            case 126:
                return SLOW;
            case 128:
                return SEDUCE;
            case 132:
                return REVERSE_DIRECTION;
            case 133:
                return ZOMBIFY;
            case 134:
                return POTION;
            case 135:
                return SHADOW;
            case 136:
                return BLIND;
            case 137:
                return FREEZE;
        }
        return null;
    }
}
