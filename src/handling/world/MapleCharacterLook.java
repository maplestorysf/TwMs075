package handling.world;

import java.util.Map;

/**
 *
 * @author AlphaEta
 */
public interface MapleCharacterLook {

    public byte getGender();

    public byte getSkinColor();

    public int getFace();

    public int getHair();

    public int getDemonMarking();

    public short getJob();

    public Map<Byte, Integer> getEquips();
}
