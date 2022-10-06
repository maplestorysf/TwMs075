package tools.data;
import constants.ServerConstants;
import handling.SendPacketOpcode;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import tools.HexTool;
import tools.StringUtil;
/**
 * Writes a maplestory-packet little-endian stream of bytes.
 *
 * @author Frz
 * @version 1.0
 * @since Revision 352
 */
public class MaplePacketLittleEndianWriter {
    private final ByteArrayOutputStream baos;
    private static final Charset ASCII = Charset.forName("BIG5"); // ISO-8859-1, UTF-8
    /**
     * Constructor - initializes this stream with a default size.
     */
    public MaplePacketLittleEndianWriter() {
        this(32);
    }
    /**
     * Constructor - initializes this stream with size <code>size</code>.
     *
     * @param size The size of the underlying stream.
     */
    public MaplePacketLittleEndianWriter(final int size) {
        this.baos = new ByteArrayOutputStream(size);
    }
    /**
     * Gets a <code>MaplePacket</code> instance representing this sequence of
     * bytes.
     *
     * @return A <code>MaplePacket</code> with the bytes in this stream.
     */
    public final byte[] getPacket() {
        if (ServerConstants.DEBUG_MODE) {
            byte[] input = baos.toByteArray();
            int pHeader = ((input[0]) & 0xFF) + (((input[1]) & 0xFF) << 8);
            int packetLen = input.length - 2;
            String op = SendPacketOpcode.nameOf(pHeader);
            String pHeaderStr = Integer.toHexString(pHeader).toUpperCase();
            pHeaderStr = "0x" + StringUtil.getLeftPaddedStr(pHeaderStr, '0', 4);
            System.out.println("[發送] " + op + "(" + pHeaderStr + ") " + packetLen + "字元:\n" + HexTool.toString(baos.toByteArray()) + "\n" + HexTool.toStringFromAscii(baos.toByteArray()));
        }
        return baos.toByteArray();
    }
    /**
     * Changes this packet into a human-readable hexadecimal stream of bytes.
     *
     * @return This packet as hex digits.
     */
    public final String toString() {
        return HexTool.toString(baos.toByteArray());
    }
    /**
     * Write the number of zero bytes
     *
     * @param b The bytes to write.
     */
    public final void writeZeroBytes(final int i) {
        for (int x = 0; x < i; x++) {
            baos.write((byte) 0);
        }
    }
    /**
     * Write an array of bytes to the stream.
     *
     * @param b The bytes to write.
     */
    public final void write(final byte[] b) {
        for (int x = 0; x < b.length; x++) {
            baos.write(b[x]);
        }
    }
    /**
     * Write a byte to the stream.
     *
     * @param b The byte to write.
     */
    public final void write(final byte b) {
        baos.write(b);
    }
    public final void write(final int b) {
        write((byte) b);
    }
    public final void write(final boolean b) {
        write(b ? 1 : 0);
    }
    /**
     * Write a short integer to the stream.
     *
     * @param i The short integer to write.
     */
    public final void writeShort(final int i) {
        baos.write((byte) (i & 0xFF));
        baos.write((byte) ((i >>> 8) & 0xFF));
    }
    /**
     * Writes an integer to the stream.
     *
     * @param i The integer to write.
     */
    public final void writeInt(final int i) {
        baos.write((byte) (i & 0xFF));
        baos.write((byte) ((i >>> 8) & 0xFF));
        baos.write((byte) ((i >>> 16) & 0xFF));
        baos.write((byte) ((i >>> 24) & 0xFF));
    }
    /**
     * Writes an ASCII string the the stream.
     *
     * @param s The ASCII string to write.
     */
    public final void writeAsciiString(final String s) {
        write(s.getBytes(ASCII));
    }
    public final void writeAsciiString(String s, final int max) {
        if (s.getBytes(ASCII).length > max) {
            s = s.substring(0, max);
        }
        write(s.getBytes(ASCII));
        for (int i = s.getBytes(ASCII).length; i < max; i++) {
            write(0);
        }
    }
    /**
     * Writes a maple-convention ASCII string to the stream.
     *
     * @param s The ASCII string to use maple-convention to write.
     */
    public final void writeMapleAsciiString(final String s) {
        writeShort((short) s.getBytes(ASCII).length);
        writeAsciiString(s);
    }
    /**
     * Writes a 2D 4 byte position information
     *
     * @param s The Point position to write.
     */
    public final void writePos(final Point s) {
        writeShort(s.x);
        writeShort(s.y);
    }
    public final void writeRect(final Rectangle s) {
        writeInt(s.x);
        writeInt(s.y);
        writeInt(s.x + s.width);
        writeInt(s.y + s.height);
    }
    /**
     * Write a long integer to the stream.
     *
     * @param l The long integer to write.
     */
    public final void writeLong(final long l) {
        baos.write((byte) (l & 0xFF));
        baos.write((byte) ((l >>> 8) & 0xFF));
        baos.write((byte) ((l >>> 16) & 0xFF));
        baos.write((byte) ((l >>> 24) & 0xFF));
        baos.write((byte) ((l >>> 32) & 0xFF));
        baos.write((byte) ((l >>> 40) & 0xFF));
        baos.write((byte) ((l >>> 48) & 0xFF));
        baos.write((byte) ((l >>> 56) & 0xFF));
    }
}


