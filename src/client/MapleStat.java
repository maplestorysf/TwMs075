package client;

public enum MapleStat {

    SKIN(0x1), // byte
    FACE(0x2), // int
    HAIR(0x4), // int
    PET(0x38), // Pets: 0x8 + 0x80000 + 0x100000  [3 longs]
    LEVEL(0x40), // byte
    JOB(0x80), // short
    STR(0x100), // short
    DEX(0x200), // short
    INT(0x400), // short
    LUK(0x800), // short
    HP(0x1000), // short
    MAXHP(0x2000), // short
    MP(0x4000), // short
    MAXMP(0x8000), // short
    AVAILABLEAP(0x10000), // short
    AVAILABLESP(0x20000), // short (depends)
    EXP(0x40000), // int
    FAME(0x80000), // short
    MESO(0x100000), // int
    GACHAPONEXP(0x200000), // int
    ;

    private final long i;

    private MapleStat(long i) {
        this.i = i;
    }

    public long getValue() {
        return i;
    }

    public static final MapleStat getByValue(final long value) {
        for (final MapleStat stat : MapleStat.values()) {
            if (stat.i == value) {
                return stat;
            }
        }
        return null;
    }

    public static enum Temp {

        STR(0x1),
        DEX(0x2),
        INT(0x4),
        LUK(0x8),
        WATK(0x10),
        WDEF(0x20),
        MATK(0x40),
        MDEF(0x80),
        ACC(0x100),
        AVOID(0x200),
        SPEED(0x400), // byte
        JUMP(0x800), // byte
        UNKNOWN(0x1000); // byte

        private final int i;

        private Temp(int i) {
            this.i = i;
        }

        public int getValue() {
            return i;
        }
    }
}
