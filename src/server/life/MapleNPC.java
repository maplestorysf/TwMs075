package server.life;

import client.MapleClient;
import server.MapleShopFactory;
import server.maps.MapleMapObjectType;
import tools.packet.CField.NPCPacket;

public class MapleNPC extends AbstractLoadedMapleLife {

    private String name = "MISSINGNO";
    private boolean custom = false;

    public MapleNPC(final int id, final String name) {
        super(id);
        this.name = name;
    }

    public final boolean hasShop() {
        return MapleShopFactory.getInstance().getShopForNPC(getId()) != null;
    }

    public final void sendShop(final MapleClient c) {
        MapleShopFactory.getInstance().getShopForNPC(getId()).sendShop(c);
    }

    @Override
    public void sendSpawnData(final MapleClient client) {
        if (getId() >= 9901000) {
            return;
        } else {
            client.sendPacket(NPCPacket.spawnNPC(this, true));
            client.sendPacket(NPCPacket.spawnNPCRequestController(this, true));
        }
    }

    @Override
    public final void sendDestroyData(final MapleClient client) {
        client.sendPacket(NPCPacket.removeNPCController(getObjectId()));
        client.sendPacket(NPCPacket.removeNPC(getObjectId()));
    }

    @Override
    public final MapleMapObjectType getType() {
        return MapleMapObjectType.NPC;
    }

    public final String getName() {
        return name;
    }

    public void setName(String n) {
        this.name = n;
    }

    public final boolean isCustom() {
        return custom;
    }

    public final void setCustom(final boolean custom) {
        this.custom = custom;
    }
}
