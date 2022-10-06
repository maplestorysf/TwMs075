package server.maps;

import java.awt.Point;
import client.MapleCharacter;
import client.MapleClient;
import tools.packet.CField;


public class MapleKite extends MapleMapObject {

    private final Point pos;
    private final MapleCharacter owner;
    private final String text;
    private final int ft, itemid;

    public MapleKite(MapleCharacter owner, Point pos, int ft, String text, int itemid) {
        this.owner = owner;
        this.pos = pos;
        this.text = text;
        this.ft = ft;
        this.itemid = itemid;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.KITE;
    }

    @Override
    public Point getPosition() {
        return pos.getLocation();
    }

    public MapleCharacter getOwner() {
        return owner;
    }

    @Override
    public void setPosition(Point position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(makeDestroyData());
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        client.sendPacket(makeSpawnData());
    }

    public byte[] makeSpawnData() {
        return CField.EffectPacket.spawnKite(getObjectId(), itemid, owner.getName(), text, pos, ft);
    }

    public byte[] makeDestroyData() {
        return  CField.EffectPacket.destroyKite(getObjectId());
    }
}
