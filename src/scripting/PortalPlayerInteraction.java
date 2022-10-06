package scripting;

import client.MapleClient;
import server.MaplePortal;

public class PortalPlayerInteraction extends AbstractPlayerInteraction {

    private final MaplePortal portal;

    public PortalPlayerInteraction(final MapleClient c, final MaplePortal portal) {
        super(c);
        this.portal = portal;
    }

    public final MaplePortal getPortal() {
        return portal;
    }

    public final void inFreeMarket() {
//        if (getMapId() != 910000000) {
        if (getPlayer().getLevel() >= 10) {
            saveLocation("FREE_MARKET");
            playPortalSE();
            warp(910000000, "st00");
        } else {
            playerMessage(5, "你需要10級才可以進入自由市場");
        }
//        }
    }

    // summon one monster on reactor location
    @Override
    public void spawnMonster(int id) {
        spawnMonster(id, 1, portal.getPosition());
    }

    // summon monsters on reactor location
    @Override
    public void spawnMonster(int id, int qty) {
        spawnMonster(id, qty, portal.getPosition());
    }
}
