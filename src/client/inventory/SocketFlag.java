package client.inventory;

/**
 *
 * @author AlphaEta
 */
public enum SocketFlag {

    DEFAULT(0x01), // You can mount a nebulite item
    SOCKET_BOX_1(0x02),
    SOCKET_BOX_2(0x04),
    SOCKET_BOX_3(0x08),
    USED_SOCKET_1(0x10),
    USED_SOCKET_2(0x20),
    USED_SOCKET_3(0x40);
    private final int i;

    private SocketFlag(int i) {
        this.i = i;
    }

    public final int getValue() {
        return i;
    }

    public final boolean check(int flag) {
        return (flag & i) == i;
    }
}
