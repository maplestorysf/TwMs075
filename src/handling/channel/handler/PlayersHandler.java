package handling.channel.handler;

import client.inventory.Item;
import client.MapleCharacter;
import client.MapleClient;
import client.MapleQuestStatus;
import client.inventory.MapleInventoryType;
import client.MapleStat;
import client.anticheat.ReportType;
import client.inventory.Equip;
import client.inventory.MapleRing;
import constants.GameConstants;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import scripting.ReactorScriptManager;
import server.events.MapleCoconut;
import server.events.MapleCoconut.MapleCoconuts;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.events.MapleEventType;
import server.life.MapleMonsterInformationProvider;
import server.life.MonsterDropEntry;
import server.life.MonsterGlobalDropEntry;
import server.maps.MapleDoor;
import server.maps.MapleMapObject;
import server.maps.MapleMist;
import server.maps.MapleReactor;
import server.maps.MechDoor;
import server.quest.MapleQuest;
import tools.FileoutputUtil;
import tools.data.LittleEndianAccessor;
import tools.packet.CField;
import tools.packet.CWvsContext;

public class PlayersHandler {

    public static void Note(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final byte type = slea.readByte();

        switch (type) {
            case 0:
                String name = slea.readMapleAsciiString();
                String msg = slea.readMapleAsciiString();
                boolean fame = slea.readByte() > 0;
                slea.readInt(); //0?
                Item itemz = chr.getCashInventory().findByCashId((int) slea.readLong());
                if (itemz == null || !itemz.getGiftFrom().equalsIgnoreCase(name) || !chr.getCashInventory().canSendNote(itemz.getUniqueId())) {
                    return;
                }
                try {
                    chr.sendNote(name, msg, fame ? 1 : 0);
                    chr.getCashInventory().sendedNote(itemz.getUniqueId());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case 1:
                short num = slea.readShort();
                if (num < 0) { // note overflow, shouldn't happen much unless > 32767 
                    num = 32767;
                }
                slea.skip(1); // first byte = wedding boolean? 
                for (int i = 0; i < num; i++) {
                    final int id = slea.readInt();
                    chr.deleteNote(id, slea.readByte() > 0 ? 1 : 0);
                }
                break;
            default:
                System.out.println("Unhandled note action, " + type + "");
        }
    }

    public static void GiveFame(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        final int who = slea.readInt();
        final int mode = slea.readByte();

        final int famechange = mode == 0 ? -1 : 1;
        final MapleCharacter target = chr.getMap().getCharacterById(who);

        if (target == null || target == chr) { // faming self
            c.sendPacket(CWvsContext.giveFameErrorResponse(1));
            return;
        } else if (chr.getLevel() < 15) {
            c.sendPacket(CWvsContext.giveFameErrorResponse(2));
            return;
        }
        switch (chr.canGiveFame(target)) {
            case OK:
                if (Math.abs(target.getFame() + famechange) <= 99999) {
                    target.addFame(famechange);
                    target.updateSingleStat(MapleStat.FAME, target.getFame());
                }
                if (!chr.isGM()) {
                    chr.hasGivenFame(target);
                }
                c.sendPacket(CWvsContext.OnFameResult(0, target.getName(), famechange == 1, target.getFame()));
                target.getClient().sendPacket(CWvsContext.OnFameResult(5, chr.getName(), famechange == 1, 0));
                break;
            case NOT_TODAY:
                c.sendPacket(CWvsContext.giveFameErrorResponse(3));
                break;
            case NOT_THIS_MONTH:
                c.sendPacket(CWvsContext.giveFameErrorResponse(4));
                break;
        }
    }

    public static void UseDoor(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final int oid = slea.readInt();
        final boolean mode = slea.readByte() == 0; // specifies if backwarp or not, 1 town to target, 0 target to town

        for (MapleMapObject obj : chr.getMap().getAllDoorsThreadsafe()) {
            final MapleDoor door = (MapleDoor) obj;
            if (door.getOwnerId() == oid) {
                door.warp(chr, mode);
                break;
            }
        }
    }

    public static void UseMechDoor(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final int oid = slea.readInt();
        final Point pos = slea.readPos();
        final int mode = slea.readByte(); // specifies if backwarp or not, 1 town to target, 0 target to town
        chr.getClient().sendPacket(CWvsContext.enableActions());
        for (MapleMapObject obj : chr.getMap().getAllMechDoorsThreadsafe()) {
            final MechDoor door = (MechDoor) obj;
            if (door.getOwnerId() == oid && door.getId() == mode) {
                chr.checkFollow();
                chr.getMap().movePlayer(chr, pos);
                break;
            }
        }
    }

    public static void TransformPlayer(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        // D9 A4 FD 00
        // 11 00
        // A0 C0 21 00
        // 07 00 64 66 62 64 66 62 64
        chr.updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final String target = slea.readMapleAsciiString();

        final Item toUse = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        switch (itemId) {
            case 2212000:
                final MapleCharacter search_chr = chr.getMap().getCharacterByName(target);
                if (search_chr != null) {
                    MapleItemInformationProvider.getInstance().getItemEffect(2210023).applyTo(search_chr);
                    search_chr.dropMessage(6, chr.getName() + " has played a prank on you!");
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                }
                break;
        }
    }

    public static void HitReactor(final LittleEndianAccessor slea, final MapleClient c) {
        final int oid = slea.readInt();
        final int charPos = slea.readInt();
        final short stance = slea.readShort();
        final MapleReactor reactor = c.getPlayer().getMap().getReactorByOid(oid);

        if (reactor == null || !reactor.isAlive()) {
            return;
        }
        reactor.hitReactor(charPos, stance, c);
    }

    public static void TouchReactor(final LittleEndianAccessor slea, final MapleClient c) {
        final int oid = slea.readInt();
        final boolean touched = slea.available() == 0 || slea.readByte() > 0; //the byte is probably the state to set it to
        final MapleReactor reactor = c.getPlayer().getMap().getReactorByOid(oid);
        if (!touched || reactor == null || !reactor.isAlive() || reactor.getTouch() == 0) {
            return;
        }
        if (reactor.getTouch() == 2) {
            ReactorScriptManager.getInstance().act(c, reactor); //not sure how touched boolean comes into play
        } else if (reactor.getTouch() == 1 && !reactor.isTimerActive()) {
            if (reactor.getReactorType() == 100) {
                final int itemid = GameConstants.getCustomReactItem(reactor.getReactorId(), reactor.getReactItem().getLeft());
                if (c.getPlayer().haveItem(itemid, reactor.getReactItem().getRight())) {
                    if (reactor.getArea().contains(c.getPlayer().getTruePosition())) {
                        MapleInventoryManipulator.removeById(c, GameConstants.getInventoryType(itemid), itemid, reactor.getReactItem().getRight(), true, false);
                        reactor.hitReactor(c);
                    } else {
                        c.getPlayer().dropMessage(5, "You are too far away.");
                    }
                } else {
                    c.getPlayer().dropMessage(5, "You don't have the item required.");
                }
            } else {
                //just hit it
                reactor.hitReactor(c);
            }
        }
    }

    public static void hitCoconut(LittleEndianAccessor slea, MapleClient c) {
        /*CB 00 A6 00 06 01
         * A6 00 = coconut id
         * 06 01 = ?
         */
        int id = slea.readShort();
        String co = "打果子";
        MapleCoconut map = (MapleCoconut) c.getChannelServer().getEvent(MapleEventType.農夫的樂趣);
        if (map == null || !map.isRunning()) {
            map = (MapleCoconut) c.getChannelServer().getEvent(MapleEventType.採集的樂趣);
            co = "打瓶蓋";
            if (map == null || !map.isRunning()) {
                return;
            }
        }
        //System.out.println("Coconut1");
        MapleCoconuts nut = map.getCoconut(id);
        if (nut == null || !nut.isHittable()) {
            return;
        }
        if (System.currentTimeMillis() < nut.getHitTime()) {
            return;
        }
        //System.out.println("Coconut2");
        if (nut.getHits() > 2 && Math.random() < 0.4 && !nut.isStopped()) {
            //System.out.println("Coconut3-1");
            nut.setHittable(false);
            if (Math.random() < 0.01 && map.getStopped() > 0) {
                nut.setStopped(true);
                map.stopCoconut();
                c.getPlayer().getMap().broadcastMessage(CField.hitCoconut(false, id, 1));
                return;
            }
            nut.resetHits(); // For next event (without restarts)
            //System.out.println("Coconut4");
            if (Math.random() < 0.05 && map.getBombings() > 0) {
                //System.out.println("Coconut5-1");
                c.getPlayer().getMap().broadcastMessage(CField.hitCoconut(false, id, 2));
                map.bombCoconut();
            } else if (map.getFalling() > 0) {
                //System.out.println("Coconut5-2");
                c.getPlayer().getMap().broadcastMessage(CField.hitCoconut(false, id, 3));
                map.fallCoconut();
                if (c.getPlayer().getTeam() == 0) {
                    map.addMapleScore();
                    //c.getPlayer().getMap().broadcastMessage(CWvsContext.serverNotice(5, c.getPlayer().getName() + " of Team Maple knocks down a " + co + "."));
                } else {
                    map.addStoryScore();
                    //c.getPlayer().getMap().broadcastMessage(CWvsContext.serverNotice(5, c.getPlayer().getName() + " of Team Story knocks down a " + co + "."));
                }
                c.getPlayer().getMap().broadcastMessage(CField.coconutScore(map.getCoconutScore()));
            }
        } else {
            //System.out.println("Coconut3-2");
            nut.hit();
            c.getPlayer().getMap().broadcastMessage(CField.hitCoconut(false, id, 1));
        }
    }

    public static void FollowRequest(final LittleEndianAccessor slea, final MapleClient c) {
        MapleCharacter tt = c.getPlayer().getMap().getCharacterById(slea.readInt());
        if (slea.readByte() > 0) {
            //1 when changing map
            tt = c.getPlayer().getMap().getCharacterById(c.getPlayer().getFollowId());
            if (tt != null && tt.getFollowId() == c.getPlayer().getId()) {
                tt.setFollowOn(true);
                c.getPlayer().setFollowOn(true);
            } else {
                c.getPlayer().checkFollow();
            }
            return;
        }
        if (slea.readByte() > 0) { //cancelling follow
            tt = c.getPlayer().getMap().getCharacterById(c.getPlayer().getFollowId());
            if (tt != null && tt.getFollowId() == c.getPlayer().getId() && c.getPlayer().isFollowOn()) {
                c.getPlayer().checkFollow();
            }
            return;
        }
        if (tt != null && tt.getPosition().distanceSq(c.getPlayer().getPosition()) < 10000 && tt.getFollowId() == 0 && c.getPlayer().getFollowId() == 0 && tt.getId() != c.getPlayer().getId()) { //estimate, should less
            tt.setFollowId(c.getPlayer().getId());
            tt.setFollowOn(false);
            tt.setFollowInitiator(false);
            c.getPlayer().setFollowOn(false);
            c.getPlayer().setFollowInitiator(false);
            tt.getClient().sendPacket(CWvsContext.followRequest(c.getPlayer().getId()));
        } else {
            c.sendPacket(CWvsContext.serverNotice(1, "You are too far away."));
        }
    }

    public static void FollowReply(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().getFollowId() > 0 && c.getPlayer().getFollowId() == slea.readInt()) {
            MapleCharacter tt = c.getPlayer().getMap().getCharacterById(c.getPlayer().getFollowId());
            if (tt != null && tt.getPosition().distanceSq(c.getPlayer().getPosition()) < 10000 && tt.getFollowId() == 0 && tt.getId() != c.getPlayer().getId()) { //estimate, should less
                boolean accepted = slea.readByte() > 0;
                if (accepted) {
                    tt.setFollowId(c.getPlayer().getId());
                    tt.setFollowOn(true);
                    tt.setFollowInitiator(false);
                    c.getPlayer().setFollowOn(true);
                    c.getPlayer().setFollowInitiator(true);
                    c.getPlayer().getMap().broadcastMessage(CField.followEffect(tt.getId(), c.getPlayer().getId(), null));
                } else {
                    c.getPlayer().setFollowId(0);
                    tt.setFollowId(0);
                    tt.getClient().sendPacket(CField.getFollowMsg(5));
                }
            } else {
                if (tt != null) {
                    tt.setFollowId(0);
                    c.getPlayer().setFollowId(0);
                }
                c.sendPacket(CWvsContext.serverNotice(1, "You are too far away."));
            }
        } else {
            c.getPlayer().setFollowId(0);
        }
    }

    public static void DoRing(final MapleClient c, final String name, final int itemid) {
        final int newItemId = itemid == 2240000 ? 1112803 : (itemid == 2240001 ? 1112806 : (itemid == 2240002 ? 1112807 : (itemid == 2240003 ? 1112809 : (1112300 + (itemid - 2240004)))));
        final MapleCharacter chr = c.getChannelServer().getPlayerStorage().getCharacterByName(name);
        int errcode = 0;
        if (c.getPlayer().getMarriageId() > 0) {
            errcode = 0x17;
        } else if (chr == null) {
            errcode = 0x12;
        } else if (chr.getMapId() != c.getPlayer().getMapId()) {
            errcode = 0x13;
        } else if (!c.getPlayer().haveItem(itemid, 1) || itemid < 2240000 || itemid > 2240015) {
            errcode = 0x0D;
        } else if (chr.getMarriageId() > 0 || chr.getMarriageItemId() > 0) {
            errcode = 0x18;
        } else if (!MapleInventoryManipulator.checkSpace(c, newItemId, 1, "")) {
            errcode = 0x14;
        } else if (!MapleInventoryManipulator.checkSpace(chr.getClient(), newItemId, 1, "")) {
            errcode = 0x15;
        }
        if (errcode > 0) {
            c.sendPacket(CWvsContext.sendEngagement((byte) errcode, 0, null, null));
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        c.getPlayer().setMarriageItemId(itemid);
        chr.getClient().sendPacket(CWvsContext.sendEngagementRequest(c.getPlayer().getName(), c.getPlayer().getId()));
    }

    public static void RingAction(final LittleEndianAccessor slea, final MapleClient c) {
        final byte mode = slea.readByte();
        switch (mode) {
            case 0:
                DoRing(c, slea.readMapleAsciiString(), slea.readInt());
                // 1112300 + (itemid - 2240004)
                break;
            case 1:
                c.getPlayer().setMarriageItemId(0);
                break;
            case 2:  // accept/deny proposal
                final boolean accepted = slea.readByte() > 0;
                final String name = slea.readMapleAsciiString();
                final int id = slea.readInt();
                final MapleCharacter chr = c.getChannelServer().getPlayerStorage().getCharacterByName(name);
                if (c.getPlayer().getMarriageId() > 0 || chr == null || chr.getId() != id || chr.getMarriageItemId() <= 0 || !chr.haveItem(chr.getMarriageItemId(), 1) || chr.getMarriageId() > 0 || !chr.isAlive() || chr.getEventInstance() != null || !c.getPlayer().isAlive() || c.getPlayer().getEventInstance() != null) {
                    c.sendPacket(CWvsContext.sendEngagement((byte) 0x1D, 0, null, null));
                    c.sendPacket(CWvsContext.enableActions());
                    return;
                }
                if (accepted) {
                    final int itemid = chr.getMarriageItemId();
                    final int newItemId = itemid == 2240000 ? 1112803 : (itemid == 2240001 ? 1112806 : (itemid == 2240002 ? 1112807 : (itemid == 2240003 ? 1112809 : (1112300 + (itemid - 2240004)))));
                    if (!MapleInventoryManipulator.checkSpace(c, newItemId, 1, "") || !MapleInventoryManipulator.checkSpace(chr.getClient(), newItemId, 1, "")) {
                        c.sendPacket(CWvsContext.sendEngagement((byte) 0x15, 0, null, null));
                        c.sendPacket(CWvsContext.enableActions());
                        return;
                    }
                    try {
                        final int[] ringID = MapleRing.makeRing(newItemId, c.getPlayer(), chr);
                        Equip eq = (Equip) MapleItemInformationProvider.getInstance().getEquipById(newItemId, ringID[1]);
                        MapleRing ring = MapleRing.loadFromDb(ringID[1]);
                        if (ring != null) {
                            eq.setRing(ring);
                        }
                        MapleInventoryManipulator.addbyItem(c, eq);

                        eq = (Equip) MapleItemInformationProvider.getInstance().getEquipById(newItemId, ringID[0]);
                        ring = MapleRing.loadFromDb(ringID[0]);
                        if (ring != null) {
                            eq.setRing(ring);
                        }
                        MapleInventoryManipulator.addbyItem(chr.getClient(), eq);

                        MapleInventoryManipulator.removeById(chr.getClient(), MapleInventoryType.USE, chr.getMarriageItemId(), 1, false, false);

                        chr.getClient().sendPacket(CWvsContext.sendEngagement((byte) 0x10, newItemId, chr, c.getPlayer()));
                        chr.setMarriageId(c.getPlayer().getId());
                        c.getPlayer().setMarriageId(chr.getId());

                        chr.fakeRelog();
                        c.getPlayer().fakeRelog();
                    } catch (Exception e) {
                        FileoutputUtil.outputFileError(FileoutputUtil.PacketEx_Log, e);
                    }

                } else {
                    chr.getClient().sendPacket(CWvsContext.sendEngagement((byte) 0x1E, 0, null, null));
                }
                c.sendPacket(CWvsContext.enableActions());
                chr.setMarriageItemId(0);
                break;
            case 3: // drop, only works for ETC
                final int itemId = slea.readInt();
                final MapleInventoryType type = GameConstants.getInventoryType(itemId);
                final Item item = c.getPlayer().getInventory(type).findById(itemId);
                if (item != null && type == MapleInventoryType.ETC && itemId / 10000 == 421) {
                    MapleInventoryManipulator.drop(c, type, item.getPosition(), item.getQuantity());
                }
                break;
            default:
                break;
        }
    }

    public static void Solomon(final LittleEndianAccessor slea, final MapleClient c) {
        c.sendPacket(CWvsContext.enableActions());
        c.getPlayer().updateTick(slea.readInt());
        Item item = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slea.readShort());
        if (item == null || item.getItemId() != slea.readInt() || item.getQuantity() <= 0 || c.getPlayer().getGachExp() > 0 || c.getPlayer().getLevel() > 50 || MapleItemInformationProvider.getInstance().getItemEffect(item.getItemId()).getEXP() <= 0) {
            return;
        }
        c.getPlayer().setGachExp(c.getPlayer().getGachExp() + MapleItemInformationProvider.getInstance().getItemEffect(item.getItemId()).getEXP());
        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, item.getPosition(), (short) 1, false);
        c.getPlayer().updateSingleStat(MapleStat.GACHAPONEXP, c.getPlayer().getGachExp());
    }

    public static void GachExp(final LittleEndianAccessor slea, final MapleClient c) {
        c.sendPacket(CWvsContext.enableActions());
        c.getPlayer().updateTick(slea.readInt());
        if (c.getPlayer().getGachExp() <= 0) {
            return;
        }
        c.getPlayer().gainExp(c.getPlayer().getGachExp() * GameConstants.getExpRate_Quest(c.getPlayer().getLevel()), true, true, false);
        c.getPlayer().setGachExp(0);
        c.getPlayer().updateSingleStat(MapleStat.GACHAPONEXP, 0);
    }

    public static void Report(final LittleEndianAccessor slea, final MapleClient c) {
        //0 = success 1 = unable to locate 2 = once a day 3 = you've been reported 4+ = unknown reason
        MapleCharacter other;
        ReportType type;
        if (!GameConstants.GMS) {
            other = c.getPlayer().getMap().getCharacterById(slea.readInt());
            type = ReportType.getById(slea.readByte());
        } else {
            type = ReportType.getById(slea.readByte());
            other = c.getPlayer().getMap().getCharacterByName(slea.readMapleAsciiString());
            //then,byte(?) and string(reason)
        }
        if (other == null || type == null || other.isIntern()) {
            c.sendPacket(CWvsContext.report(4));
            return;
        }
        final MapleQuestStatus stat = c.getPlayer().getQuestNAdd(MapleQuest.getInstance(GameConstants.REPORT_QUEST));
        if (stat.getCustomData() == null) {
            stat.setCustomData("0");
        }
        final long currentTime = System.currentTimeMillis();
        final long theTime = Long.parseLong(stat.getCustomData());
        if (theTime + 7200000 > currentTime && !c.getPlayer().isIntern()) {
            c.sendPacket(CWvsContext.enableActions());
            c.getPlayer().dropMessage(5, "You may only report every 2 hours.");
        } else {
            stat.setCustomData(String.valueOf(currentTime));
            other.addReport(type);
            c.sendPacket(CWvsContext.report(2));
        }
    }

    public static final void MonsterBookInfoRequest(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (c.getPlayer() == null || c.getPlayer().getMap() == null) {
            return;
        }
        slea.readInt(); // tick
        final MapleCharacter player = c.getPlayer().getMap().getCharacterById(slea.readInt());
        c.sendPacket(CWvsContext.enableActions());
        if (player != null && !player.isClone()) {
            if (!player.isGM() || c.getPlayer().isGM()) {
                c.sendPacket(CWvsContext.getMonsterBookInfo(player));
            }
        }
    }

    public static final void MonsterBookDropsRequest(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (c.getPlayer() == null || c.getPlayer().getMap() == null) {
            return;
        }
        chr.updateTick(slea.readInt()); // tick
        final int cardid = slea.readInt();
        final int mobid = MapleItemInformationProvider.getInstance().getCardMobId(cardid);
        if (mobid <= 0 || !chr.getMonsterBook().hasCard(cardid)) {
            c.sendPacket(CWvsContext.getCardDrops(cardid, null));
            return;
        }
        final MapleMonsterInformationProvider ii = MapleMonsterInformationProvider.getInstance();
        final List<Integer> newDrops = new ArrayList<>();
        for (final MonsterDropEntry de : ii.retrieveDrop(mobid)) {
            if (de.itemId > 0 && de.questid <= 0 && !newDrops.contains(de.itemId)) {
                newDrops.add(de.itemId);
            }
        }
        for (final MonsterGlobalDropEntry de : ii.getGlobalDrop()) {
            if (de.itemId > 0 && de.questid <= 0 && !newDrops.contains(de.itemId)) {
                newDrops.add(de.itemId);
            }
        }
        c.sendPacket(CWvsContext.getCardDrops(cardid, newDrops));
    }

    public static final void ChangeSet(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (c.getPlayer() == null || c.getPlayer().getMap() == null) {
            return;
        }
        final int set = slea.readInt();
        if (chr.getMonsterBook().changeSet(set)) {
            chr.getMonsterBook().applyBook(chr, false);
            chr.getQuestNAdd(MapleQuest.getInstance(GameConstants.CURRENT_SET)).setCustomData(String.valueOf(set));
            c.sendPacket(CWvsContext.changeCardSet(set));
        }
    }

    public static boolean inArea(MapleCharacter chr) {
        for (Rectangle rect : chr.getMap().getAreas()) {
            if (rect.contains(chr.getTruePosition())) {
                return true;
            }
        }
        for (MapleMist mist : chr.getMap().getAllMistsThreadsafe()) {
            if (mist.getOwnerId() == chr.getId() && mist.isPoisonMist() == 2 && mist.getBox().contains(chr.getTruePosition())) {
                return true;
            }
        }
        return false;
    }
}
