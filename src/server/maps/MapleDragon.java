package server.maps;

import client.MapleCharacter;
import client.MapleClient;
import tools.packet.CField;

public class MapleDragon extends AnimatedMapleMapObject {

    private int owner, jobid;

    public MapleDragon(MapleCharacter owner) {
        super();
        this.owner = owner.getId();
        this.jobid = owner.getJob();
        if (jobid < 2200 || jobid > 2218) {
            throw new RuntimeException("Trying to create a dragon for a non-Evan");
        }
        setPosition(owner.getTruePosition());
        setStance(4);
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        client.sendPacket(CField.spawnDragon(this));
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(CField.removeDragon(this.owner));
    }

    public int getOwner() {
        return this.owner;
    }

    public int getJobId() {
        return this.jobid;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.SUMMON;
    }
}
