package handling.channel.handler;

import java.util.Map;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.awt.Point;

import client.inventory.Equip;
import client.inventory.Equip.ScrollResult;
import client.inventory.Item;
import client.Skill;
import client.inventory.ItemFlag;
import client.inventory.MaplePet;
import client.inventory.MaplePet.PetFlag;
import client.inventory.MapleMount;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.MapleClient;
import client.MapleDisease;
import client.MapleQuestStatus;
import client.inventory.MapleInventoryType;
import client.inventory.MapleInventory;
import client.MapleStat;
import client.MonsterFamiliar;
import client.PlayerStats;
import client.SkillEntry;
import constants.GameConstants;
import client.SkillFactory;
import client.anticheat.CheatingOffense;
import constants.ServerConstants;
import database.DatabaseConnection;
import handling.channel.ChannelServer;
import handling.world.MaplePartyCharacter;
import handling.world.World;
import java.awt.Rectangle;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import server.Randomizer;
import server.RandomRewards;
import server.MapleShopFactory;
import server.MapleStatEffect;
import server.MapleItemInformationProvider;
import server.MapleInventoryManipulator;
import server.StructRewardItem;
import server.quest.MapleQuest;
import server.maps.SavedLocationType;
import server.maps.FieldLimitType;
import server.maps.MapleMap;
import server.maps.MapleMapItem;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.life.MapleMonster;
import server.life.MapleLifeFactory;
import scripting.NPCScriptManager;
import server.PredictCardFactory;
import server.StructFamiliar;
import server.StructItemOption;
import server.events.MapleEvent;
import server.events.MapleEventType;
import server.maps.MapleKite;
import server.maps.MapleMist;
import server.shops.HiredMerchant;
import server.shops.IMaplePlayerShop;
import tools.FileoutputUtil;
import tools.Pair;
import tools.packet.MTSCSPacket;
import tools.packet.PetPacket;
import tools.data.LittleEndianAccessor;
import tools.packet.CField.EffectPacket;
import tools.packet.CField;
import tools.packet.CWvsContext;
import tools.packet.CWvsContext.InfoPacket;
import tools.packet.CWvsContext.InventoryPacket;
import tools.packet.MobPacket;
import tools.packet.PlayerShopPacket;

public class InventoryHandler {

    public static final void ItemMove(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().hasBlockedInventory()) { //hack
            return;
        }
        c.getPlayer().setScrolledPosition((short) 0);
        c.getPlayer().updateTick(slea.readInt());
        final MapleInventoryType type = MapleInventoryType.getByType(slea.readByte()); //04
        final short src = slea.readShort();                                            //01 00
        final short dst = slea.readShort();                                            //00 00
        final short quantity = slea.readShort();                                       //53 01

        if (src < 0 && dst > 0) {
            MapleInventoryManipulator.unequip(c, src, dst);
        } else if (dst < 0) {
            MapleInventoryManipulator.equip(c, src, dst);
        } else if (dst == 0) {
            MapleInventoryManipulator.drop(c, type, src, quantity);
        } else {
            MapleInventoryManipulator.move(c, type, src, dst);
        }
    }

    public static final void SwitchBag(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().hasBlockedInventory()) { //hack
            return;
        }
        c.getPlayer().setScrolledPosition((short) 0);
        c.getPlayer().updateTick(slea.readInt());
        final short src = (short) slea.readInt();                                       //01 00
        final short dst = (short) slea.readInt();                                            //00 00
        if (src < 100 || dst < 100) {
            return;
        }
        MapleInventoryManipulator.move(c, MapleInventoryType.ETC, src, dst);
    }

    public static final void MoveBag(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().hasBlockedInventory()) { //hack
            return;
        }
        c.getPlayer().setScrolledPosition((short) 0);
        c.getPlayer().updateTick(slea.readInt());
        final boolean srcFirst = slea.readInt() > 0;
        short dst = (short) slea.readInt();                                       //01 00
        if (slea.readByte() != 4) { //must be etc) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        short src = slea.readShort();                                            //00 00
        MapleInventoryManipulator.move(c, MapleInventoryType.ETC, srcFirst ? dst : src, srcFirst ? src : dst);
    }

    public static final void ItemSort(final LittleEndianAccessor slea, final MapleClient c) {
        c.getPlayer().updateTick(slea.readInt());

        final MapleInventoryType pInvType = MapleInventoryType.getByType(slea.readByte());
        if (pInvType == MapleInventoryType.UNDEFINED || c.getPlayer().hasBlockedInventory()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final MapleInventory pInv = c.getPlayer().getInventory(pInvType); //Mode should correspond with MapleInventoryType
        boolean sorted = false;

        while (!sorted) {
            final byte freeSlot = (byte) pInv.getNextFreeSlot();
            if (freeSlot != -1) {
                byte itemSlot = -1;
                for (byte i = (byte) (freeSlot + 1); i <= pInv.getSlotLimit(); i++) {
                    if (pInv.getItem(i) != null) {
                        itemSlot = i;
                        break;
                    }
                }
                if (itemSlot > 0) {
                    MapleInventoryManipulator.move(c, pInvType, itemSlot, freeSlot);
                } else {
                    sorted = true;
                }
            } else {
                sorted = true;
            }
        }
        c.sendPacket(CWvsContext.finishedSort(pInvType.getType()));
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void ItemGather(final LittleEndianAccessor slea, final MapleClient c) {
        // [41 00] [E5 1D 55 00] [01]
        // [32 00] [01] [01] // Sent after

        c.getPlayer().updateTick(slea.readInt());
        c.getPlayer().setScrolledPosition((short) 0);
        if (c.getPlayer().hasBlockedInventory()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final byte mode = slea.readByte();
        final MapleInventoryType invType = MapleInventoryType.getByType(mode);
        MapleInventory Inv = c.getPlayer().getInventory(invType);

        final List<Item> itemMap = new LinkedList<>();
        for (Item item : Inv.list()) {
            itemMap.add(item.copy()); // clone all  items T___T.
        }
        for (Item itemStats : itemMap) {
            MapleInventoryManipulator.removeFromSlot(c, invType, itemStats.getPosition(), itemStats.getQuantity(), true, false, true);
        }

        final List<Item> sortedItems = sortItems(itemMap);
        for (Item item : sortedItems) {
            MapleInventoryManipulator.addFromDrop(c, item, false);
        }
        c.sendPacket(CWvsContext.finishedGather(mode));
        c.sendPacket(CWvsContext.enableActions());
        itemMap.clear();
        sortedItems.clear();
    }

    private static final List<Item> sortItems(final List<Item> passedMap) {
        final List<Integer> itemIds = new ArrayList<>(); // empty list.
        for (Item item : passedMap) {
            itemIds.add(item.getItemId()); // adds all item ids to the empty list to be sorted.
        }
        Collections.sort(itemIds); // sorts item ids

        final List<Item> sortedList = new LinkedList<>(); // ordered list pl0x <3.

        for (Integer val : itemIds) {
            for (Item item : passedMap) {
                if (val == item.getItemId()) { // Goes through every index and finds the first value that matches
                    sortedList.add(item);
                    passedMap.remove(item);
                    break;
                }
            }
        }
        return sortedList;
    }

    public static final boolean UseRewardItem(final byte slot, final int itemId, final MapleClient c, final MapleCharacter chr) {
        final Item toUse = c.getPlayer().getInventory(GameConstants.getInventoryType(itemId)).getItem(slot);
        c.sendPacket(CWvsContext.enableActions());
        if (toUse != null && toUse.getQuantity() >= 1 && toUse.getItemId() == itemId && !chr.hasBlockedInventory()) {
            if (chr.getInventory(MapleInventoryType.EQUIP).getNextFreeSlot() > -1 && chr.getInventory(MapleInventoryType.USE).getNextFreeSlot() > -1 && chr.getInventory(MapleInventoryType.SETUP).getNextFreeSlot() > -1 && chr.getInventory(MapleInventoryType.ETC).getNextFreeSlot() > -1) {
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final Pair<Integer, List<StructRewardItem>> rewards = ii.getRewardItem(itemId);

                if (rewards != null && rewards.getLeft() > 0) {
                    while (true) {
                        for (StructRewardItem reward : rewards.getRight()) {
                            if (reward.prob > 0 && Randomizer.nextInt(rewards.getLeft()) < reward.prob) { // Total prob
                                if (GameConstants.getInventoryType(reward.itemid) == MapleInventoryType.EQUIP) {
                                    final Item item = ii.getEquipById(reward.itemid);
                                    if (reward.period > 0) {
                                        item.setExpiration(System.currentTimeMillis() + (reward.period * 60 * 60 * 10));
                                    }
                                    item.setGMLog("Reward item: " + itemId + " on " + FileoutputUtil.CurrentReadable_Date());
                                    MapleInventoryManipulator.addbyItem(c, item);
                                } else {
                                    MapleInventoryManipulator.addById(c, reward.itemid, reward.quantity, "Reward item: " + itemId + " on " + FileoutputUtil.CurrentReadable_Date());
                                }
                                MapleInventoryManipulator.removeById(c, GameConstants.getInventoryType(itemId), itemId, 1, false, false);

                                c.sendPacket(EffectPacket.showRewardItemAnimation(reward.itemid, reward.effect));
                                chr.getMap().broadcastMessage(chr, EffectPacket.showRewardItemAnimation(reward.itemid, reward.effect, chr.getId()), false);
                                return true;
                            }
                        }
                    }
                } else {
                    chr.dropMessage(6, "Unknown error.");
                }
            } else {
                chr.dropMessage(6, "Insufficient inventory slot.");
            }
        }
        return false;
    }

    public static final void UseItem(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || !chr.isAlive() || chr.getMapId() == 749040100 || chr.getMap() == null || chr.hasDisease(MapleDisease.POTION) || chr.hasBlockedInventory() || chr.inPVP()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final long time = System.currentTimeMillis();
        if (chr.getNextConsume() > time) {
            chr.dropMessage(5, "You may not use this item yet.");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        c.getPlayer().updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final Item toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (!FieldLimitType.PotionUse.check(chr.getMap().getFieldLimit())) { //cwk quick hack
            if (MapleItemInformationProvider.getInstance().getItemEffect(toUse.getItemId()).applyTo(chr)) {
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                if (chr.getMap().getConsumeItemCoolTime() > 0) {
                    chr.setNextConsume(time + (chr.getMap().getConsumeItemCoolTime() * 1000));
                }
            }

        } else {
            c.sendPacket(CWvsContext.enableActions());
        }
    }

    public static final void UseCosmetic(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || !chr.isAlive() || chr.getMap() == null || chr.hasBlockedInventory() || chr.inPVP()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final Item toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId || itemId / 10000 != 254 || (itemId / 1000) % 10 != chr.getGender()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (MapleItemInformationProvider.getInstance().getItemEffect(toUse.getItemId()).applyTo(chr)) {
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
        }
    }

    public static final void UseReturnScroll(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (!chr.isAlive() || chr.getMapId() == 749040100 || chr.hasBlockedInventory() || chr.isInBlockedMap() || chr.inPVP()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        c.getPlayer().updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final Item toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (!FieldLimitType.PotionUse.check(chr.getMap().getFieldLimit())) {
            if (MapleItemInformationProvider.getInstance().getItemEffect(toUse.getItemId()).applyReturnScroll(chr)) {
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
            } else {
                c.sendPacket(CWvsContext.enableActions());
            }
        } else {
            c.sendPacket(CWvsContext.enableActions());
        }
    }

    public static final void UseAlienSocket(final LittleEndianAccessor slea, final MapleClient c) {
        c.getPlayer().updateTick(slea.readInt());
        c.getPlayer().setScrolledPosition((short) 0);
        final Item alienSocket = c.getPlayer().getInventory(MapleInventoryType.USE).getItem((byte) slea.readShort());
        final int alienSocketId = slea.readInt();
        final Item toMount = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readShort());
        if (alienSocket == null || alienSocketId != alienSocket.getItemId() || toMount == null || c.getPlayer().hasBlockedInventory()) {
            c.sendPacket(InventoryPacket.getInventoryFull());
            return;
        }
        // Can only use once-> 2nd and 3rd must use NPC.
        final Equip eqq = (Equip) toMount;
        if (eqq.getSocketState() != 0) { // Used before
            c.getPlayer().dropMessage(1, "This item already has a socket.");
        } else {
            eqq.setSocket1(0); // First socket, GMS removed the other 2
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, alienSocket.getPosition(), (short) 1, false);
            c.getPlayer().forceReAddItem(toMount, MapleInventoryType.EQUIP);
        }
        c.sendPacket(MTSCSPacket.useAlienSocket(true));
    }

    public static final void UseNebulite(final LittleEndianAccessor slea, final MapleClient c) {
        c.getPlayer().updateTick(slea.readInt());
        c.getPlayer().setScrolledPosition((short) 0);
        final Item nebulite = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) slea.readShort());
        final int nebuliteId = slea.readInt();
        final Item toMount = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readShort());
        if (nebulite == null || nebuliteId != nebulite.getItemId() || toMount == null || c.getPlayer().hasBlockedInventory()) {
            c.sendPacket(InventoryPacket.getInventoryFull());
            return;
        }
        final Equip eqq = (Equip) toMount;
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        boolean success = false;
        if (eqq.getSocket1() == 0/* || eqq.getSocket2() == 0 || eqq.getSocket3() == 0*/) { // GMS removed 2nd and 3rd sockets, we can put into npc.
            final StructItemOption pot = ii.getSocketInfo(nebuliteId);
            if (pot != null && GameConstants.optionTypeFits(pot.optionType, eqq.getItemId())) {
                //if (eqq.getSocket1() == 0) { // priority comes first
                eqq.setSocket1(pot.opID);
                //}// else if (eqq.getSocket2() == 0) {
                //    eqq.setSocket2(pot.opID);
                //} else if (eqq.getSocket3() == 0) {
                //    eqq.setSocket3(pot.opID);
                //}
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, nebulite.getPosition(), (short) 1, false);
                c.getPlayer().forceReAddItem(toMount, MapleInventoryType.EQUIP);
                success = true;
            }
        }
        c.getPlayer().getMap().broadcastMessage(CField.showNebuliteEffect(c.getPlayer().getId(), success));
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void UseNebuliteFusion(final LittleEndianAccessor slea, final MapleClient c) {
        c.getPlayer().updateTick(slea.readInt());
        c.getPlayer().setScrolledPosition((short) 0);
        final int nebuliteId1 = slea.readInt();
        final Item nebulite1 = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) slea.readShort());
        final int nebuliteId2 = slea.readInt();
        final Item nebulite2 = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) slea.readShort());
        final int mesos = slea.readInt();
        final int premiumQuantity = slea.readInt();
        if (nebulite1 == null || nebulite2 == null || nebuliteId1 != nebulite1.getItemId() || nebuliteId2 != nebulite2.getItemId() || (mesos == 0 && premiumQuantity == 0) || (mesos != 0 && premiumQuantity != 0) || mesos < 0 || premiumQuantity < 0 || c.getPlayer().hasBlockedInventory()) {
            c.getPlayer().dropMessage(1, "Failed to fuse Nebulite.");
            c.sendPacket(InventoryPacket.getInventoryFull());
            return;
        }
        final int grade1 = GameConstants.getNebuliteGrade(nebuliteId1);
        final int grade2 = GameConstants.getNebuliteGrade(nebuliteId2);
        final int highestRank = grade1 > grade2 ? grade1 : grade2;
        if (grade1 == -1 || grade2 == -1 || (highestRank == 3 && premiumQuantity != 2) || (highestRank == 2 && premiumQuantity != 1)
                || (highestRank == 1 && mesos != 5000) || (highestRank == 0 && mesos != 3000) || (mesos > 0 && c.getPlayer().getMeso() < mesos)
                || (premiumQuantity > 0 && c.getPlayer().getItemQuantity(4420000, false) < premiumQuantity) || grade1 >= 4 || grade2 >= 4
                || (c.getPlayer().getInventory(MapleInventoryType.SETUP).getNumFreeSlot() < 1)) { // 4000 + = S, 3000 + = A, 2000 + = B, 1000 + = C, else = D
            c.sendPacket(CField.useNebuliteFusion(c.getPlayer().getId(), 0, false));
            return; // Most of them were done in client, so we just send the unsuccessfull packet, as it is only here when they packet edit.
        }
        final int avg = (grade1 + grade2) / 2; // have to revise more about grades.
        final int rank = Randomizer.nextInt(100) < 4 ? (Randomizer.nextInt(100) < 70 ? (avg != 3 ? (avg + 1) : avg) : (avg != 0 ? (avg - 1) : 0)) : avg;
        // 4 % chance to up/down 1 grade, (70% to up, 30% to down), cannot up to S grade. =)
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final List<StructItemOption> pots = new LinkedList<>(ii.getAllSocketInfo(rank).values());
        int newId = 0;
        while (newId == 0) {
            StructItemOption pot = pots.get(Randomizer.nextInt(pots.size()));
            if (pot != null) {
                newId = pot.opID;
            }
        }
        if (mesos > 0) {
            c.getPlayer().gainMeso(-mesos, true);
        } else if (premiumQuantity > 0) {
            MapleInventoryManipulator.removeById(c, MapleInventoryType.ETC, 4420000, premiumQuantity, false, false);
        }
        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, nebulite1.getPosition(), (short) 1, false);
        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, nebulite2.getPosition(), (short) 1, false);
        MapleInventoryManipulator.addById(c, newId, (short) 1, "Fused from " + nebuliteId1 + " and " + nebuliteId2 + " on " + FileoutputUtil.CurrentReadable_Date());
        c.sendPacket(CField.useNebuliteFusion(c.getPlayer().getId(), newId, true));
    }

    public static final void UseMagnify(final LittleEndianAccessor slea, final MapleClient c) {
//        c.getPlayer().updateTick(slea.readInt());
//        c.getPlayer().setScrolledPosition((short) 0);
//        final byte src = (byte) slea.readShort();
//        final boolean insight = src == 127 && c.getPlayer().getTrait(MapleTraitType.sense).getLevel() >= 30;
//        final Item magnify = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(src);
//        final Item toReveal = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readShort());
//        if ((magnify == null && !insight) || toReveal == null || c.getPlayer().hasBlockedInventory()) {
//            c.sendPacket(InventoryPacket.getInventoryFull());
//            return;
//        }
//        final Equip eqq = (Equip) toReveal;
//        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
//        final int reqLevel = ii.getReqLevel(eqq.getItemId()) / 10;
//        if (eqq.getState() == 1 && (insight || magnify.getItemId() == 2460003 || (magnify.getItemId() == 2460002 && reqLevel <= 12) || (magnify.getItemId() == 2460001 && reqLevel <= 7) || (magnify.getItemId() == 2460000 && reqLevel <= 3))) {
//            final List<List<StructItemOption>> pots = new LinkedList<>(ii.getAllPotentialInfo().values());
//            int new_state = Math.abs(eqq.getPotential1());
//            if (new_state > 20 || new_state < 17) { // incase overflow
//                new_state = 17;
//            }
//            int lines = 2; // default
//            if (eqq.getPotential2() != 0) {
//                lines++;
//            }
//            if (eqq.getPotential3() != 0) {
//                lines++;
//            }
//            if (eqq.getPotential4() != 0) {
//                lines++;
//            }
//            while (eqq.getState() != new_state) {
//                //31001 = haste, 31002 = door, 31003 = se, 31004 = hb, 41005 = combat orders, 41006 = advanced blessing, 41007 = speed infusion
//                for (int i = 0; i < lines; i++) { // minimum 2 lines, max 5
//                    boolean rewarded = false;
//                    while (!rewarded) {
//                        StructItemOption pot = pots.get(Randomizer.nextInt(pots.size())).get(reqLevel);
//                        if (pot != null && pot.reqLevel / 10 <= reqLevel && GameConstants.optionTypeFits(pot.optionType, eqq.getItemId()) && GameConstants.potentialIDFits(pot.opID, new_state, i)) { //optionType
//                            //have to research optionType before making this truely official-like
//                            if (i == 0) {
//                                eqq.setPotential1(pot.opID);
//                            } else if (i == 1) {
//                                eqq.setPotential2(pot.opID);
//                            } else if (i == 2) {
//                                eqq.setPotential3(pot.opID);
//                            } else if (i == 3) {
//                                eqq.setPotential4(pot.opID);
//                            } else if (i == 4) {
//                                eqq.setPotential5(pot.opID);
//                            }
//                            rewarded = true;
//                        }
//                    }
//                }
//            }
//            c.getPlayer().getTrait(MapleTraitType.insight).addExp((insight ? 10 : ((magnify.getItemId() + 2) - 2460000)) * 2, c.getPlayer());
//            c.getPlayer().getMap().broadcastMessage(CField.showMagnifyingEffect(c.getPlayer().getId(), eqq.getPosition()));
//            if (!insight) {
//                c.sendPacket(InventoryPacket.scrolledItem(magnify, toReveal, false, true));
//                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, magnify.getPosition(), (short) 1, false);
//            } else {
//                c.getPlayer().forceReAddItem(toReveal, MapleInventoryType.EQUIP);
//            }
//            c.sendPacket(CWvsContext.enableActions());
//        } else {
//            c.sendPacket(InventoryPacket.getInventoryFull());
//            return;
//        }
    }

    public static final void addToScrollLog(int accountID, int charID, int scrollID, int itemID, byte oldSlots, byte newSlots, byte viciousHammer, String result, boolean ws, boolean ls, int vega) {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("INSERT INTO scroll_log VALUES(DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, accountID);
            ps.setInt(2, charID);
            ps.setInt(3, scrollID);
            ps.setInt(4, itemID);
            ps.setByte(5, oldSlots);
            ps.setByte(6, newSlots);
            ps.setByte(7, viciousHammer);
            ps.setString(8, result);
            ps.setByte(9, (byte) (ws ? 1 : 0));
            ps.setByte(10, (byte) (ls ? 1 : 0));
            ps.setInt(11, vega);
            ps.execute();
            ps.close();
        } catch (SQLException e) {
            FileoutputUtil.outputFileError(FileoutputUtil.PacketEx_Log, e);
        }
    }

    public static final boolean UseUpgradeScroll(final short slot, final short dst, final short ws, final MapleClient c, final MapleCharacter chr, final boolean legendarySpirit) {
        return UseUpgradeScroll(slot, dst, ws, c, chr, 0, legendarySpirit);
    }

    public static final boolean UseUpgradeScroll(final short slot, final short dst, final short ws, final MapleClient c, final MapleCharacter chr, final int vegas, final boolean legendarySpirit) {
        boolean whiteScroll = false; // 祝福捲軸
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        chr.setScrolledPosition((short) 0);
        if ((ws & 2) == 2) {
            whiteScroll = true;
        }
        Equip toScroll = null;
        if (dst < 0) {
            toScroll = (Equip) chr.getInventory(MapleInventoryType.EQUIPPED).getItem(dst);
        } else if (legendarySpirit) {
            toScroll = (Equip) chr.getInventory(MapleInventoryType.EQUIP).getItem(dst);
        }
        if (toScroll == null || c.getPlayer().hasBlockedInventory()) {
            c.sendPacket(CWvsContext.enableActions());
            return false;
        }
        final byte oldLevel = toScroll.getLevel();
        final byte oldEnhance = toScroll.getEnhance();
        final byte oldState = toScroll.getState();
        final short oldFlag = toScroll.getFlag();
        final byte oldSlots = toScroll.getUpgradeSlots();

        Item scroll = chr.getInventory(MapleInventoryType.USE).getItem(slot);
        if (scroll == null) {
            scroll = chr.getInventory(MapleInventoryType.CASH).getItem(slot);
            if (scroll == null) {
                c.sendPacket(InventoryPacket.getInventoryFull());
                c.sendPacket(CWvsContext.enableActions());
                return false;
            }
        }

        //卷軸使用判斷區
        if (!GameConstants.isSpecialScroll(scroll.getItemId()) && !GameConstants.isCleanSlate(scroll.getItemId()) && !GameConstants.isEquipScroll(scroll.getItemId()) && !GameConstants.isPotentialScroll(scroll.getItemId())) {
            if (toScroll.getUpgradeSlots() < 1) {
                c.sendPacket(InventoryPacket.getInventoryFull());
                c.sendPacket(CWvsContext.enableActions());
                return false;
            }
        } else if (GameConstants.isEquipScroll(scroll.getItemId())) {
            if (toScroll.getUpgradeSlots() >= 1 || toScroll.getEnhance() >= 100 || vegas > 0 || ii.isCash(toScroll.getItemId())) {
                c.sendPacket(InventoryPacket.getInventoryFull());
                c.sendPacket(CWvsContext.enableActions());
                return false;
            }
        } else if (GameConstants.isPotentialScroll(scroll.getItemId())) {
            final boolean isEpic = scroll.getItemId() / 100 == 20497;
            if ((!isEpic && toScroll.getState() >= 1) || (isEpic && toScroll.getState() >= 18) || (toScroll.getLevel() == 0 && toScroll.getUpgradeSlots() == 0 && toScroll.getItemId() / 10000 != 135 && !isEpic) || vegas > 0 || ii.isCash(toScroll.getItemId())) {
                c.sendPacket(InventoryPacket.getInventoryFull());
                c.sendPacket(CWvsContext.enableActions());
                return false;
            }
        } else if (GameConstants.isSpecialScroll(scroll.getItemId())) {
            if (ii.isCash(toScroll.getItemId()) || toScroll.getEnhance() >= 8) {
                c.sendPacket(InventoryPacket.getInventoryFull());
                c.sendPacket(CWvsContext.enableActions());
                return false;
            }
        }
        if (!GameConstants.canScroll(toScroll.getItemId()) && !GameConstants.isChaosScroll(toScroll.getItemId())) {
            c.sendPacket(InventoryPacket.getInventoryFull());
            c.sendPacket(CWvsContext.enableActions());
            return false;
        }
        if ((GameConstants.isCleanSlate(scroll.getItemId()) || GameConstants.isTablet(scroll.getItemId()) || GameConstants.isGeneralScroll(scroll.getItemId()) || GameConstants.isChaosScroll(scroll.getItemId())) && (vegas > 0 || ii.isCash(toScroll.getItemId()))) {
            c.sendPacket(InventoryPacket.getInventoryFull());
            c.sendPacket(CWvsContext.enableActions());
            return false;
        }
        if (GameConstants.isTablet(scroll.getItemId()) && toScroll.getDurability() < 0) { //not a durability item
            c.sendPacket(InventoryPacket.getInventoryFull());
            c.sendPacket(CWvsContext.enableActions());
            return false;
        } else if ((!GameConstants.isTablet(scroll.getItemId()) && !GameConstants.isPotentialScroll(scroll.getItemId()) && !GameConstants.isEquipScroll(scroll.getItemId()) && !GameConstants.isCleanSlate(scroll.getItemId()) && !GameConstants.isSpecialScroll(scroll.getItemId()) && !GameConstants.isChaosScroll(scroll.getItemId())) && toScroll.getDurability() >= 0) {
            c.sendPacket(InventoryPacket.getInventoryFull());
            c.sendPacket(CWvsContext.enableActions());
            return false;
        }
        Item wscroll = null;

        // 驗證是否Hack
        List<Integer> scrollReqs = ii.getScrollReqs(scroll.getItemId());
        if (scrollReqs != null && scrollReqs.size() > 0 && !scrollReqs.contains(toScroll.getItemId())) {
            c.sendPacket(InventoryPacket.getInventoryFull());
            c.sendPacket(CWvsContext.enableActions());
            return false;
        }

        if (whiteScroll) {
            wscroll = chr.getInventory(MapleInventoryType.USE).findById(2340000);
            if (wscroll == null) {
                whiteScroll = false;
            }
        }
        if (GameConstants.isTablet(scroll.getItemId()) || GameConstants.isGeneralScroll(scroll.getItemId())) {
            switch (scroll.getItemId() % 1000 / 100) {
                case 0: //1h
                    if (GameConstants.isTwoHanded(toScroll.getItemId()) || !GameConstants.isWeapon(toScroll.getItemId())) {
                        c.sendPacket(CWvsContext.enableActions());
                        return false;
                    }
                    break;
                case 1: //2h
                    if (!GameConstants.isTwoHanded(toScroll.getItemId()) || !GameConstants.isWeapon(toScroll.getItemId())) {
                        c.sendPacket(CWvsContext.enableActions());
                        return false;
                    }
                    break;
                case 2: //armor
                    if (GameConstants.isAccessory(toScroll.getItemId()) || GameConstants.isWeapon(toScroll.getItemId())) {
                        c.sendPacket(CWvsContext.enableActions());
                        return false;
                    }
                    break;
                case 3: //accessory
                    if (!GameConstants.isAccessory(toScroll.getItemId()) || GameConstants.isWeapon(toScroll.getItemId())) {
                        c.sendPacket(CWvsContext.enableActions());
                        return false;
                    }
                    break;
            }
        } else if (!GameConstants.isAccessoryScroll(scroll.getItemId()) && !GameConstants.isChaosScroll(scroll.getItemId()) && !GameConstants.isCleanSlate(scroll.getItemId()) && !GameConstants.isEquipScroll(scroll.getItemId()) && !GameConstants.isPotentialScroll(scroll.getItemId()) && !GameConstants.isSpecialScroll(scroll.getItemId())) {
            if (!ii.canScroll(scroll.getItemId(), toScroll.getItemId())) {
                c.sendPacket(CWvsContext.enableActions());
                return false;
            }
        }
        if (GameConstants.isAccessoryScroll(scroll.getItemId()) && !GameConstants.isAccessory(toScroll.getItemId())) {
            c.sendPacket(CWvsContext.enableActions());
            return false;
        }
        //卷軸數量異常
        if (scroll.getQuantity() <= 0) {
            c.sendPacket(CWvsContext.enableActions());
            return false;
        }

        if (legendarySpirit && vegas == 0) {
            if (chr.getSkillLevel(SkillFactory.getSkill(chr.getStat().getSkillByJob(1003, chr.getJob()))) <= 0) {
                c.sendPacket(CWvsContext.enableActions());
                return false;
            }
        }

        // 卷軸 成功/ 失敗/ 破壞
        String end = "";
        Equip scrolled = (Equip) ii.scrollEquipWithId(toScroll, scroll, whiteScroll, chr, vegas);
        ScrollResult scrollSuccess;
        if (scrolled == null) {
            if (ItemFlag.SHIELD_WARD.check(oldFlag)) {
                scrolled = toScroll;
                scrollSuccess = Equip.ScrollResult.FAIL;
                scrolled.setFlag((short) (oldFlag - ItemFlag.SHIELD_WARD.getValue()));
                end = "失敗";
            } else {
                scrollSuccess = Equip.ScrollResult.CURSE;
                end = "破壞";
            }
        } else if ((scroll.getItemId() / 100 == 20497 && scrolled.getState() == 1) || scrolled.getLevel() > oldLevel || scrolled.getEnhance() > oldEnhance || scrolled.getState() > oldState || scrolled.getFlag() > oldFlag) {
            scrollSuccess = Equip.ScrollResult.SUCCESS;
            end = "成功";
        } else if ((GameConstants.isCleanSlate(scroll.getItemId()) && scrolled.getUpgradeSlots() > oldSlots)) {
            scrollSuccess = Equip.ScrollResult.SUCCESS;
            end = "成功";
        } else {
            scrollSuccess = Equip.ScrollResult.FAIL;
            end = "失敗";
        }
        try {
            String msg = "卷軸: " + MapleItemInformationProvider.getInstance().getName(scroll.getItemId()) + "(" + scroll.getItemId() + ") 裝備:" + MapleItemInformationProvider.getInstance().getName(scroll.getItemId()) + "(" + toScroll.getItemId() + ")[力:" + toScroll.getStr() + "/敏:" + toScroll.getDex() + "/幸:" + toScroll.getLuk() + "/智:" + toScroll.getInt() + "/物攻:" + toScroll.getWatk() + "/魔功:" + toScroll.getMatk() + "/物防:" + toScroll.getWdef() + "/魔防:" + toScroll.getMdef() + "] 祝福卷: " + whiteScroll + " 結果: " + end;
            if (ServerConstants.LOG_SCROLL) {
                FileoutputUtil.logToFile("紀錄/監聽/使用卷軸.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress().split(":")[0] + " 玩家[" + c.getPlayer().getName() + "] " + msg);
            }
            if (c.getPlayer().getDebugMessage()) {
                c.getPlayer().dropMessage(-3, msg);
            }
        } catch (Exception ex) {
            FileoutputUtil.printError("InventoryHandler.txt", ex, "useUpgradeScroll has Exception");
        }
        // 移除卷軸
        chr.getInventory(GameConstants.getInventoryType(scroll.getItemId())).removeItem(scroll.getPosition(), (short) 1, false);
        if (whiteScroll) {
            // 移除祝福卷軸
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, wscroll.getPosition(), (short) 1, false, false);
        } else if (scrollSuccess == Equip.ScrollResult.FAIL && scrolled.getUpgradeSlots() < oldSlots && c.getPlayer().getInventory(MapleInventoryType.CASH).findById(5640000) != null) {
            chr.setScrolledPosition(scrolled.getPosition());
            if (vegas == 0) {
//                c.sendPacket(CWvsContext.pamSongUI());
            }
        }
        //道具更新類別
        if (scrollSuccess == Equip.ScrollResult.CURSE) {
            c.sendPacket(InventoryPacket.scrolledItem(scroll, toScroll, true, false));
            if (dst < 0) {
                chr.getInventory(MapleInventoryType.EQUIPPED).removeItem(toScroll.getPosition());
            } else {
                chr.getInventory(MapleInventoryType.EQUIP).removeItem(toScroll.getPosition());
            }
        } else if (vegas == 0) {
            c.sendPacket(InventoryPacket.scrolledItem(scroll, scrolled, false, false));
        }
        //卷軸特效
        chr.getMap().broadcastMessage(chr, CField.getScrollEffect(c.getPlayer().getId(), scrollSuccess, legendarySpirit, whiteScroll), vegas == 0);
        //addToScrollLog(chr.getAccountID(), chr.getId(), scroll.getItemId(), itemID, oldSlots, (byte)(scrolled == null ? -1 : scrolled.getUpgradeSlots()), oldVH, scrollSuccess.name(), whiteScroll, legendarySpirit, vegas);
        // 裝備中的道具更新
        if (dst < 0 && (scrollSuccess == Equip.ScrollResult.SUCCESS || scrollSuccess == Equip.ScrollResult.CURSE) && vegas == 0) {
            chr.equipChanged();
        }
        return true;
    }

    public static final boolean UseSkillBook(final byte slot, final int itemId, final MapleClient c, final MapleCharacter chr) {
        final Item toUse = chr.getInventory(GameConstants.getInventoryType(itemId)).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId || chr.hasBlockedInventory()) {
            return false;
        }
        final Map<String, Integer> skilldata = MapleItemInformationProvider.getInstance().getEquipStats(toUse.getItemId());
        if (skilldata == null) { // Hacking or used an unknown item
            return false;
        }
        boolean canuse = false, success = false;
        int skill = 0, maxlevel = 0;

        final Integer SuccessRate = skilldata.get("success");
        final Integer ReqSkillLevel = skilldata.get("reqSkillLevel");
        final Integer MasterLevel = skilldata.get("masterLevel");

        byte i = 0;
        Integer CurrentLoopedSkillId;
        while (true) {
            CurrentLoopedSkillId = skilldata.get("skillid" + i);
            i++;
            if (CurrentLoopedSkillId == null || MasterLevel == null) {
                break; // End of data
            }
            final Skill CurrSkillData = SkillFactory.getSkill(CurrentLoopedSkillId);
            if (CurrSkillData != null && CurrSkillData.canBeLearnedBy(chr.getJob()) && (ReqSkillLevel == null || chr.getSkillLevel(CurrSkillData) >= ReqSkillLevel) && chr.getMasterLevel(CurrSkillData) < MasterLevel) {
                canuse = true;
                if (SuccessRate == null || Randomizer.nextInt(100) <= SuccessRate) {
                    success = true;
                    chr.changeSingleSkillLevel(CurrSkillData, chr.getSkillLevel(CurrSkillData), (byte) (int) MasterLevel);
                } else {
                    success = false;
                }
                MapleInventoryManipulator.removeFromSlot(c, GameConstants.getInventoryType(itemId), slot, (short) 1, false);
                break;
            }
        }
        c.getPlayer().getMap().broadcastMessage(CWvsContext.useSkillBook(chr, skill, maxlevel, canuse, success));
        c.sendPacket(CWvsContext.enableActions());
        return canuse;
    }

    public static final void UseCatchItem(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        c.getPlayer().updateTick(slea.readInt());
        c.getPlayer().setScrolledPosition((short) 0);
        final byte slot = (byte) slea.readShort();
        final int itemid = slea.readInt();
        final MapleMonster mob = chr.getMap().getMonsterByOid(slea.readInt());
        final Item toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);
        final MapleMap map = chr.getMap();

        if (toUse != null && toUse.getQuantity() > 0 && toUse.getItemId() == itemid && mob != null && !chr.hasBlockedInventory() && itemid / 10000 == 227 && MapleItemInformationProvider.getInstance().getCardMobId(itemid) == mob.getId()) {
            if (!MapleItemInformationProvider.getInstance().isMobHP(itemid) || mob.getHp() <= mob.getMobMaxHp() / 2) {
                map.broadcastMessage(MobPacket.catchMonster(mob.getObjectId(), itemid, (byte) 1));
                map.killMonster(mob, chr, true, false, (byte) 1);
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false, false);
                if (MapleItemInformationProvider.getInstance().getCreateId(itemid) > 0) {
                    MapleInventoryManipulator.addById(c, MapleItemInformationProvider.getInstance().getCreateId(itemid), (short) 1, "Catch item " + itemid + " on " + FileoutputUtil.CurrentReadable_Date());
                }
            } else {
                map.broadcastMessage(MobPacket.catchMonster(mob.getObjectId(), itemid, (byte) 0));
                c.sendPacket(CWvsContext.catchMob(mob.getId(), itemid, (byte) 0));
            }
        }
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void UseMountFood(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        c.getPlayer().updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemid = slea.readInt(); //2260000 usually
        final Item toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);
        final MapleMount mount = chr.getMount();

        if (itemid / 10000 == 226 && toUse != null && toUse.getQuantity() > 0 && toUse.getItemId() == itemid && mount != null && !c.getPlayer().hasBlockedInventory()) {
            final int fatigue = mount.getFatigue();

            boolean levelup = false;
            mount.setFatigue((byte) -30);

            if (fatigue > 0) {
                mount.increaseExp();
                final int level = mount.getLevel();
                if (level < 30 && mount.getExp() >= GameConstants.getMountExpNeededForLevel(level + 1)) {
                    mount.setLevel((byte) (level + 1));
                    levelup = true;
                }
            }
            chr.getMap().broadcastMessage(CWvsContext.updateMount(chr, levelup));
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
        }
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void UseScriptedNPCItem(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        c.getPlayer().updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final Item toUse = chr.getInventory(GameConstants.getInventoryType(itemId)).getItem(slot);
        long expiration_days = 0;
        int mountid = 0;

        if (toUse != null && toUse.getQuantity() >= 1 && toUse.getItemId() == itemId && !chr.hasBlockedInventory() && !chr.inPVP()) {
            switch (toUse.getItemId()) {
                case 2430007: { // Blank Compass
                    final MapleInventory inventory = chr.getInventory(MapleInventoryType.SETUP);
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);

                    if (inventory.countById(3994102) >= 20 // Compass Letter "North"
                            && inventory.countById(3994103) >= 20 // Compass Letter "South"
                            && inventory.countById(3994104) >= 20 // Compass Letter "East"
                            && inventory.countById(3994105) >= 20) { // Compass Letter "West"
                        MapleInventoryManipulator.addById(c, 2430008, (short) 1, "Scripted item: " + itemId + " on " + FileoutputUtil.CurrentReadable_Date()); // Gold Compass
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.SETUP, 3994102, 20, false, false);
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.SETUP, 3994103, 20, false, false);
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.SETUP, 3994104, 20, false, false);
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.SETUP, 3994105, 20, false, false);
                    } else {
                        MapleInventoryManipulator.addById(c, 2430007, (short) 1, "Scripted item: " + itemId + " on " + FileoutputUtil.CurrentReadable_Date()); // Blank Compass
                    }
                    NPCScriptManager.getInstance().start(c, 2084001);
                    break;
                }
                case 2430008: { // Gold Compass
                    chr.saveLocation(SavedLocationType.RICHIE);
                    MapleMap map;
                    boolean warped = false;

                    for (int i = 390001000; i <= 390001004; i++) {
                        map = c.getChannelServer().getMapFactory().getMap(i);

                        if (map.getCharactersSize() == 0) {
                            chr.changeMap(map, map.getPortal(0));
                            warped = true;
                            break;
                        }
                    }
                    if (warped) { // Removal of gold compass
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                    } else { // Or mabe some other message.
                        c.getPlayer().dropMessage(5, "All maps are currently in use, please try again later.");
                    }
                    break;
                }
                case 2430112: //miracle cube fragment
                    if (c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getInventory(MapleInventoryType.USE).countById(2430112) >= 25) {
                            if (MapleInventoryManipulator.checkSpace(c, 2049400, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 25, true, false)) {
                                MapleInventoryManipulator.addById(c, 2049400, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else if (c.getPlayer().getInventory(MapleInventoryType.USE).countById(2430112) >= 10) {
                            if (MapleInventoryManipulator.checkSpace(c, 2049400, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 10, true, false)) {
                                MapleInventoryManipulator.addById(c, 2049401, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "There needs to be 10 Fragments for a Potential Scroll, 25 for Advanced Potential Scroll.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Please make some space.");
                    }
                    break;
                case 2430481: //super miracle cube fragment
                    if (c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getInventory(MapleInventoryType.USE).countById(2430481) >= 30) {
                            if (MapleInventoryManipulator.checkSpace(c, 2049701, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 30, true, false)) {
                                MapleInventoryManipulator.addById(c, 2049701, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else if (c.getPlayer().getInventory(MapleInventoryType.USE).countById(2430481) >= 20) {
                            if (MapleInventoryManipulator.checkSpace(c, 2049300, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 20, true, false)) {
                                MapleInventoryManipulator.addById(c, 2049300, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "There needs to be 20 Fragments for a Advanced Equip Enhancement Scroll, 30 for Epic Potential Scroll 80%.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Please make some space.");
                    }
                    break;
                case 2430691: // nebulite diffuser fragment
                    if (c.getPlayer().getInventory(MapleInventoryType.CASH).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getInventory(MapleInventoryType.USE).countById(2430691) >= 10) {
                            if (MapleInventoryManipulator.checkSpace(c, 5750001, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 10, true, false)) {
                                MapleInventoryManipulator.addById(c, 5750001, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "There needs to be 10 Fragments for a Nebulite Diffuser.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Please make some space.");
                    }
                    break;
                case 2430748: // premium fusion ticket 
                    if (c.getPlayer().getInventory(MapleInventoryType.ETC).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getInventory(MapleInventoryType.USE).countById(2430748) >= 20) {
                            if (MapleInventoryManipulator.checkSpace(c, 4420000, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 20, true, false)) {
                                MapleInventoryManipulator.addById(c, 4420000, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "There needs to be 20 Fragments for a Premium Fusion Ticket.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Please make some space.");
                    }
                    break;
                case 2430692: // nebulite box
                    if (c.getPlayer().getInventory(MapleInventoryType.SETUP).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getInventory(MapleInventoryType.USE).countById(2430692) >= 1) {
                            final int rank = Randomizer.nextInt(100) < 30 ? (Randomizer.nextInt(100) < 4 ? 2 : 1) : 0;
                            final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                            final List<StructItemOption> pots = new LinkedList<>(ii.getAllSocketInfo(rank).values());
                            int newId = 0;
                            while (newId == 0) {
                                StructItemOption pot = pots.get(Randomizer.nextInt(pots.size()));
                                if (pot != null) {
                                    newId = pot.opID;
                                }
                            }
                            if (MapleInventoryManipulator.checkSpace(c, newId, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 1, true, false)) {
                                MapleInventoryManipulator.addById(c, newId, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                                c.sendPacket(InfoPacket.getShowItemGain(newId, (short) 1, true));
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "You do not have a Nebulite Box.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Please make some space.");
                    }
                    break;
                case 5680019: {//starling hair 
                    //if (c.getPlayer().getGender() == 1) {
                    int hair = 32150 + (c.getPlayer().getHair() % 10);
                    c.getPlayer().setHair(hair);
                    c.getPlayer().updateSingleStat(MapleStat.HAIR, hair);
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, slot, (byte) 1, false);
                    //}
                    break;
                }
                case 5680020: {//starling hair 
                    //if (c.getPlayer().getGender() == 0) {
                    int hair = 32160 + (c.getPlayer().getHair() % 10);
                    c.getPlayer().setHair(hair);
                    c.getPlayer().updateSingleStat(MapleStat.HAIR, hair);
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, slot, (byte) 1, false);
                    //}
                    break;
                }
                case 3994225:
                    c.getPlayer().dropMessage(5, "Please bring this item to the NPC.");
                    break;
                case 2430212: //energy drink
                    MapleQuestStatus marr = c.getPlayer().getQuestNAdd(MapleQuest.getInstance(GameConstants.ENERGY_DRINK));
                    if (marr.getCustomData() == null) {
                        marr.setCustomData("0");
                    }
                    long lastTime = Long.parseLong(marr.getCustomData());
                    if (lastTime + (600000) > System.currentTimeMillis()) {
                        c.getPlayer().dropMessage(5, "You can only use one energy drink per 10 minutes.");
                    } else if (c.getPlayer().getFatigue() > 0) {
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                        c.getPlayer().setFatigue(c.getPlayer().getFatigue() - 5);
                    }
                    break;
                case 2430213: //energy drink
                    marr = c.getPlayer().getQuestNAdd(MapleQuest.getInstance(GameConstants.ENERGY_DRINK));
                    if (marr.getCustomData() == null) {
                        marr.setCustomData("0");
                    }
                    lastTime = Long.parseLong(marr.getCustomData());
                    if (lastTime + (600000) > System.currentTimeMillis()) {
                        c.getPlayer().dropMessage(5, "You can only use one energy drink per 10 minutes.");
                    } else if (c.getPlayer().getFatigue() > 0) {
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                        c.getPlayer().setFatigue(c.getPlayer().getFatigue() - 10);
                    }
                    break;
                case 2430220: //energy drink
                case 2430214: //energy drink
                    if (c.getPlayer().getFatigue() > 0) {
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                        c.getPlayer().setFatigue(c.getPlayer().getFatigue() - 30);
                    }
                    break;
                case 2430227: //energy drink
                    if (c.getPlayer().getFatigue() > 0) {
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                        c.getPlayer().setFatigue(c.getPlayer().getFatigue() - 50);
                    }
                    break;
                case 2430231: //energy drink
                    marr = c.getPlayer().getQuestNAdd(MapleQuest.getInstance(GameConstants.ENERGY_DRINK));
                    if (marr.getCustomData() == null) {
                        marr.setCustomData("0");
                    }
                    lastTime = Long.parseLong(marr.getCustomData());
                    if (lastTime + (600000) > System.currentTimeMillis()) {
                        c.getPlayer().dropMessage(5, "You can only use one energy drink per 10 minutes.");
                    } else if (c.getPlayer().getFatigue() > 0) {
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                        c.getPlayer().setFatigue(c.getPlayer().getFatigue() - 40);
                    }
                    break;
                case 2430144: //smb
                    final int itemid = Randomizer.nextInt(373) + 2290000;
                    if (MapleItemInformationProvider.getInstance().itemExists(itemid) && !MapleItemInformationProvider.getInstance().getName(itemid).contains("Special") && !MapleItemInformationProvider.getInstance().getName(itemid).contains("Event")) {
                        MapleInventoryManipulator.addById(c, itemid, (short) 1, "Reward item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                    }
                    break;
                case 2430370:
                    if (MapleInventoryManipulator.checkSpace(c, 2028062, (short) 1, "")) {
                        MapleInventoryManipulator.addById(c, 2028062, (short) 1, "Reward item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                    }
                    break;
                case 2430158: //lion king
                    if (c.getPlayer().getInventory(MapleInventoryType.ETC).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getInventory(MapleInventoryType.ETC).countById(4000630) >= 100) {
                            if (MapleInventoryManipulator.checkSpace(c, 4310010, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 1, true, false)) {
                                MapleInventoryManipulator.addById(c, 4310010, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else if (c.getPlayer().getInventory(MapleInventoryType.ETC).countById(4000630) >= 50) {
                            if (MapleInventoryManipulator.checkSpace(c, 4310009, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 1, true, false)) {
                                MapleInventoryManipulator.addById(c, 4310009, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "There needs to be 50 Purification Totems for a Noble Lion King Medal, 100 for Royal Lion King Medal.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Please make some space.");
                    }
                    break;
                case 2430159:
                    MapleQuest.getInstance(3182).forceComplete(c.getPlayer(), 2161004);
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                    break;
                case 2430200: //thunder stone
                    if (c.getPlayer().getQuestStatus(31152) != 2) {
                        c.getPlayer().dropMessage(5, "You have no idea how to use it.");
                    } else if (c.getPlayer().getInventory(MapleInventoryType.ETC).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getInventory(MapleInventoryType.ETC).countById(4000660) >= 1 && c.getPlayer().getInventory(MapleInventoryType.ETC).countById(4000661) >= 1 && c.getPlayer().getInventory(MapleInventoryType.ETC).countById(4000662) >= 1 && c.getPlayer().getInventory(MapleInventoryType.ETC).countById(4000663) >= 1) {
                            if (MapleInventoryManipulator.checkSpace(c, 4032923, 1, "") && MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, toUse.getItemId(), 1, true, false) && MapleInventoryManipulator.removeById(c, MapleInventoryType.ETC, 4000660, 1, true, false) && MapleInventoryManipulator.removeById(c, MapleInventoryType.ETC, 4000661, 1, true, false) && MapleInventoryManipulator.removeById(c, MapleInventoryType.ETC, 4000662, 1, true, false) && MapleInventoryManipulator.removeById(c, MapleInventoryType.ETC, 4000663, 1, true, false)) {
                                MapleInventoryManipulator.addById(c, 4032923, (short) 1, "Scripted item: " + toUse.getItemId() + " on " + FileoutputUtil.CurrentReadable_Date());
                            } else {
                                c.getPlayer().dropMessage(5, "Please make some space.");
                            }
                        } else {
                            c.getPlayer().dropMessage(5, "There needs to be 1 of each Stone for a Dream Key.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Please make some space.");
                    }
                    break;
                case 2430130:
                case 2430131: //energy charge
                    if (GameConstants.isResist(c.getPlayer().getJob())) {
                        MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                        c.getPlayer().gainExp(20000 + (c.getPlayer().getLevel() * 50 * c.getChannelServer().getExpRate()), true, true, false);
                    } else {
                        c.getPlayer().dropMessage(5, "You may not use this item.");
                    }
                    break;
                case 2430132:
                case 2430133:
                case 2430134: //resistance box
                case 2430142:
                    if (c.getPlayer().getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() >= 1) {
                        if (c.getPlayer().getJob() == 3200 || c.getPlayer().getJob() == 3210 || c.getPlayer().getJob() == 3211 || c.getPlayer().getJob() == 3212) {
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                            MapleInventoryManipulator.addById(c, 1382101, (short) 1, "Scripted item: " + itemId + " on " + FileoutputUtil.CurrentReadable_Date());
                        } else if (c.getPlayer().getJob() == 3300 || c.getPlayer().getJob() == 3310 || c.getPlayer().getJob() == 3311 || c.getPlayer().getJob() == 3312) {
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                            MapleInventoryManipulator.addById(c, 1462093, (short) 1, "Scripted item: " + itemId + " on " + FileoutputUtil.CurrentReadable_Date());
                        } else if (c.getPlayer().getJob() == 3500 || c.getPlayer().getJob() == 3510 || c.getPlayer().getJob() == 3511 || c.getPlayer().getJob() == 3512) {
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                            MapleInventoryManipulator.addById(c, 1492080, (short) 1, "Scripted item: " + itemId + " on " + FileoutputUtil.CurrentReadable_Date());
                        } else {
                            c.getPlayer().dropMessage(5, "You may not use this item.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "Make some space.");
                    }
                    break;
                case 2430036: //croco 1 day
                    mountid = 1027;
                    expiration_days = 1;
                    break;
                case 2430170: //croco 7 day
                    mountid = 1027;
                    expiration_days = 7;
                    break;
                case 2430037: //black scooter 1 day
                    mountid = 1028;
                    expiration_days = 1;
                    break;
                case 2430038: //pink scooter 1 day
                    mountid = 1029;
                    expiration_days = 1;
                    break;
                case 2430039: //clouds 1 day
                    mountid = 1030;
                    expiration_days = 1;
                    break;
                case 2430040: //balrog 1 day
                    mountid = 1031;
                    expiration_days = 1;
                    break;
                case 2430223: //balrog 1 day
                    mountid = 1031;
                    expiration_days = 15;
                    break;
                case 2430259: //balrog 1 day
                    mountid = 1031;
                    expiration_days = 3;
                    break;
                case 2430242: //motorcycle
                    mountid = 80001018;
                    expiration_days = 10;
                    break;
                case 2430243: //power suit
                    mountid = 80001019;
                    expiration_days = 10;
                    break;
                case 2430261: //power suit
                    mountid = 80001019;
                    expiration_days = 3;
                    break;
                case 2430249: //motorcycle
                    mountid = 80001027;
                    expiration_days = 3;
                    break;
                case 2430225: //balrog 1 day
                    mountid = 1031;
                    expiration_days = 10;
                    break;
                case 2430053: //croco 30 day
                    mountid = 1027;
                    expiration_days = 1;
                    break;
                case 2430054: //black scooter 30 day
                    mountid = 1028;
                    expiration_days = 30;
                    break;
                case 2430055: //pink scooter 30 day
                    mountid = 1029;
                    expiration_days = 30;
                    break;
                case 2430257: //pink
                    mountid = 1029;
                    expiration_days = 7;
                    break;
                case 2430056: //mist rog 30 day
                    mountid = 1035;
                    expiration_days = 30;
                    break;
                case 2430057:
                    mountid = 1033;
                    expiration_days = 30;
                    break;
                case 2430072: //ZD tiger 7 day
                    mountid = 1034;
                    expiration_days = 7;
                    break;
                case 2430073: //lion 15 day
                    mountid = 1036;
                    expiration_days = 15;
                    break;
                case 2430074: //unicorn 15 day
                    mountid = 1037;
                    expiration_days = 15;
                    break;
                case 2430272: //low rider 15 day
                    mountid = 1038;
                    expiration_days = 3;
                    break;
                case 2430275: //spiegelmann
                    mountid = 80001033;
                    expiration_days = 7;
                    break;
                case 2430075: //low rider 15 day
                    mountid = 1038;
                    expiration_days = 15;
                    break;
                case 2430076: //red truck 15 day
                    mountid = 1039;
                    expiration_days = 15;
                    break;
                case 2430077: //gargoyle 15 day
                    mountid = 1040;
                    expiration_days = 15;
                    break;
                case 2430080: //shinjo 20 day
                    mountid = 1042;
                    expiration_days = 20;
                    break;
                case 2430082: //orange mush 7 day
                    mountid = 1044;
                    expiration_days = 7;
                    break;
                case 2430260: //orange mush 7 day
                    mountid = 1044;
                    expiration_days = 3;
                    break;
                case 2430091: //nightmare 10 day
                    mountid = 1049;
                    expiration_days = 10;
                    break;
                case 2430092: //yeti 10 day
                    mountid = 1050;
                    expiration_days = 10;
                    break;
                case 2430263: //yeti 10 day
                    mountid = 1050;
                    expiration_days = 3;
                    break;
                case 2430093: //ostrich 10 day
                    mountid = 1051;
                    expiration_days = 10;
                    break;
                case 2430101: //pink bear 10 day
                    mountid = 1052;
                    expiration_days = 10;
                    break;
                case 2430102: //transformation robo 10 day
                    mountid = 1053;
                    expiration_days = 10;
                    break;
                case 2430103: //chicken 30 day
                    mountid = 1054;
                    expiration_days = 30;
                    break;
                case 2430266: //chicken 30 day
                    mountid = 1054;
                    expiration_days = 3;
                    break;
                case 2430265: //chariot
                    mountid = 1151;
                    expiration_days = 3;
                    break;
                case 2430258: //law officer
                    mountid = 1115;
                    expiration_days = 365;
                    break;
                case 2430117: //lion 1 year
                    mountid = 1036;
                    expiration_days = 365;
                    break;
                case 2430118: //red truck 1 year
                    mountid = 1039;
                    expiration_days = 365;
                    break;
                case 2430119: //gargoyle 1 year
                    mountid = 1040;
                    expiration_days = 365;
                    break;
                case 2430120: //unicorn 1 year
                    mountid = 1037;
                    expiration_days = 365;
                    break;
                case 2430271: //owl 30 day
                    mountid = 1069;
                    expiration_days = 3;
                    break;
                case 2430136: //owl 30 day
                    mountid = 1069;
                    expiration_days = 30;
                    break;
                case 2430137: //owl 1 year
                    mountid = 1069;
                    expiration_days = 365;
                    break;
                case 2430145: //mothership
                    mountid = 1070;
                    expiration_days = 30;
                    break;
                case 2430146: //mothership
                    mountid = 1070;
                    expiration_days = 365;
                    break;
                case 2430147: //mothership
                    mountid = 1071;
                    expiration_days = 30;
                    break;
                case 2430148: //mothership
                    mountid = 1071;
                    expiration_days = 365;
                    break;
                case 2430135: //os4
                    mountid = 1065;
                    expiration_days = 15;
                    break;
                case 2430149: //leonardo 30 day
                    mountid = 1072;
                    expiration_days = 30;
                    break;
                case 2430262: //leonardo 30 day
                    mountid = 1072;
                    expiration_days = 3;
                    break;
                case 2430179: //witch 15 day
                    mountid = 1081;
                    expiration_days = 15;
                    break;
                case 2430264: //witch 15 day
                    mountid = 1081;
                    expiration_days = 3;
                    break;
                case 2430201: //giant bunny 60 day
                    mountid = 1096;
                    expiration_days = 60;
                    break;
                case 2430228: //tiny bunny 60 day
                    mountid = 1101;
                    expiration_days = 60;
                    break;
                case 2430276: //tiny bunny 60 day
                    mountid = 1101;
                    expiration_days = 15;
                    break;
                case 2430277: //tiny bunny 60 day
                    mountid = 1101;
                    expiration_days = 365;
                    break;
                case 2430283: //trojan
                    mountid = 1025;
                    expiration_days = 10;
                    break;
                case 2430291: //hot air
                    mountid = 1145;
                    expiration_days = -1;
                    break;
                case 2430293: //nadeshiko
                    mountid = 1146;
                    expiration_days = -1;
                    break;
                case 2430295: //pegasus
                    mountid = 1147;
                    expiration_days = -1;
                    break;
                case 2430297: //dragon
                    mountid = 1148;
                    expiration_days = -1;
                    break;
                case 2430299: //broom
                    mountid = 1149;
                    expiration_days = -1;
                    break;
                case 2430301: //cloud
                    mountid = 1150;
                    expiration_days = -1;
                    break;
                case 2430303: //chariot
                    mountid = 1151;
                    expiration_days = -1;
                    break;
                case 2430305: //nightmare
                    mountid = 1152;
                    expiration_days = -1;
                    break;
                case 2430307: //rog
                    mountid = 1153;
                    expiration_days = -1;
                    break;
                case 2430309: //mist rog
                    mountid = 1154;
                    expiration_days = -1;
                    break;
                case 2430311: //owl
                    mountid = 1156;
                    expiration_days = -1;
                    break;
                case 2430313: //helicopter
                    mountid = 1156;
                    expiration_days = -1;
                    break;
                case 2430315: //pentacle
                    mountid = 1118;
                    expiration_days = -1;
                    break;
                case 2430317: //frog
                    mountid = 1121;
                    expiration_days = -1;
                    break;
                case 2430319: //turtle
                    mountid = 1122;
                    expiration_days = -1;
                    break;
                case 2430321: //buffalo
                    mountid = 1123;
                    expiration_days = -1;
                    break;
                case 2430323: //tank
                    mountid = 1124;
                    expiration_days = -1;
                    break;
                case 2430325: //viking
                    mountid = 1129;
                    expiration_days = -1;
                    break;
                case 2430327: //pachinko
                    mountid = 1130;
                    expiration_days = -1;
                    break;
                case 2430329: //kurenai
                    mountid = 1063;
                    expiration_days = -1;
                    break;
                case 2430331: //horse
                    mountid = 1025;
                    expiration_days = -1;
                    break;
                case 2430333: //tiger
                    mountid = 1034;
                    expiration_days = -1;
                    break;
                case 2430335: //hyena
                    mountid = 1136;
                    expiration_days = -1;
                    break;
                case 2430337: //ostrich
                    mountid = 1051;
                    expiration_days = -1;
                    break;
                case 2430339: //low rider
                    mountid = 1138;
                    expiration_days = -1;
                    break;
                case 2430341: //napoleon
                    mountid = 1139;
                    expiration_days = -1;
                    break;
                case 2430343: //croking
                    mountid = 1027;
                    expiration_days = -1;
                    break;
                case 2430346: //lovely
                    mountid = 1029;
                    expiration_days = -1;
                    break;
                case 2430348: //retro
                    mountid = 1028;
                    expiration_days = -1;
                    break;
                case 2430350: //f1
                    mountid = 1033;
                    expiration_days = -1;
                    break;
                case 2430352: //power suit
                    mountid = 1064;
                    expiration_days = -1;
                    break;
                case 2430354: //giant rabbit
                    mountid = 1096;
                    expiration_days = -1;
                    break;
                case 2430356: //small rabit
                    mountid = 1101;
                    expiration_days = -1;
                    break;
                case 2430358: //rabbit rickshaw
                    mountid = 1102;
                    expiration_days = -1;
                    break;
                case 2430360: //chicken
                    mountid = 1054;
                    expiration_days = -1;
                    break;
                case 2430362: //transformer
                    mountid = 1053;
                    expiration_days = -1;
                    break;
                case 2430292: //hot air
                    mountid = 1145;
                    expiration_days = 90;
                    break;
                case 2430294: //nadeshiko
                    mountid = 1146;
                    expiration_days = 90;
                    break;
                case 2430296: //pegasus
                    mountid = 1147;
                    expiration_days = 90;
                    break;
                case 2430298: //dragon
                    mountid = 1148;
                    expiration_days = 90;
                    break;
                case 2430300: //broom
                    mountid = 1149;
                    expiration_days = 90;
                    break;
                case 2430302: //cloud
                    mountid = 1150;
                    expiration_days = 90;
                    break;
                case 2430304: //chariot
                    mountid = 1151;
                    expiration_days = 90;
                    break;
                case 2430306: //nightmare
                    mountid = 1152;
                    expiration_days = 90;
                    break;
                case 2430308: //rog
                    mountid = 1153;
                    expiration_days = 90;
                    break;
                case 2430310: //mist rog
                    mountid = 1154;
                    expiration_days = 90;
                    break;
                case 2430312: //owl
                    mountid = 1156;
                    expiration_days = 90;
                    break;
                case 2430314: //helicopter
                    mountid = 1156;
                    expiration_days = 90;
                    break;
                case 2430316: //pentacle
                    mountid = 1118;
                    expiration_days = 90;
                    break;
                case 2430318: //frog
                    mountid = 1121;
                    expiration_days = 90;
                    break;
                case 2430320: //turtle
                    mountid = 1122;
                    expiration_days = 90;
                    break;
                case 2430322: //buffalo
                    mountid = 1123;
                    expiration_days = 90;
                    break;
                case 2430326: //viking
                    mountid = 1129;
                    expiration_days = 90;
                    break;
                case 2430328: //pachinko
                    mountid = 1130;
                    expiration_days = 90;
                    break;
                case 2430330: //kurenai
                    mountid = 1063;
                    expiration_days = 90;
                    break;
                case 2430332: //horse
                    mountid = 1025;
                    expiration_days = 90;
                    break;
                case 2430334: //tiger
                    mountid = 1034;
                    expiration_days = 90;
                    break;
                case 2430336: //hyena
                    mountid = 1136;
                    expiration_days = 90;
                    break;
                case 2430338: //ostrich
                    mountid = 1051;
                    expiration_days = 90;
                    break;
                case 2430340: //low rider
                    mountid = 1138;
                    expiration_days = 90;
                    break;
                case 2430342: //napoleon
                    mountid = 1139;
                    expiration_days = 90;
                    break;
                case 2430344: //croking
                    mountid = 1027;
                    expiration_days = 90;
                    break;
                case 2430347: //lovely
                    mountid = 1029;
                    expiration_days = 90;
                    break;
                case 2430349: //retro
                    mountid = 1028;
                    expiration_days = 90;
                    break;
                case 2430351: //f1
                    mountid = 1033;
                    expiration_days = 90;
                    break;
                case 2430353: //power suit
                    mountid = 1064;
                    expiration_days = 90;
                    break;
                case 2430355: //giant rabbit
                    mountid = 1096;
                    expiration_days = 90;
                    break;
                case 2430357: //small rabit
                    mountid = 1101;
                    expiration_days = 90;
                    break;
                case 2430359: //rabbit rickshaw
                    mountid = 1102;
                    expiration_days = 90;
                    break;
                case 2430361: //chicken
                    mountid = 1054;
                    expiration_days = 90;
                    break;
                case 2430363: //transformer
                    mountid = 1053;
                    expiration_days = 90;
                    break;
                case 2430324: //high way
                    mountid = 1158;
                    expiration_days = -1;
                    break;
                case 2430345: //high way
                    mountid = 1158;
                    expiration_days = 90;
                    break;
                case 2430367: //law off
                    mountid = 1115;
                    expiration_days = 3;
                    break;
                case 2430365: //pony
                    mountid = 1025;
                    expiration_days = 365;
                    break;
                case 2430366: //pony
                    mountid = 1025;
                    expiration_days = 15;
                    break;
                case 2430369: //nightmare
                    mountid = 1049;
                    expiration_days = 10;
                    break;
                case 2430392: //speedy
                    mountid = 80001038;
                    expiration_days = 90;
                    break;
                case 2430476: //red truck? but name is pegasus?
                    mountid = 1039;
                    expiration_days = 15;
                    break;
                case 2430477: //red truck? but name is pegasus?
                    mountid = 1039;
                    expiration_days = 365;
                    break;
                case 2430232: //fortune
                    mountid = 1106;
                    expiration_days = 10;
                    break;
                case 2430511: //spiegel
                    mountid = 80001033;
                    expiration_days = 15;
                    break;
                case 2430512: //rspiegel
                    mountid = 80001033;
                    expiration_days = 365;
                    break;
                case 2430536: //buddy buggy
                    mountid = 80001114;
                    expiration_days = 365;
                    break;
                case 2430537: //buddy buggy
                    mountid = 80001114;
                    expiration_days = 15;
                    break;
                case 2430229: //bunny rickshaw 60 day
                    mountid = 1102;
                    expiration_days = 60;
                    break;
                case 2430199: //santa sled
                    mountid = 1102;
                    expiration_days = 60;
                    break;
                case 2430206: //race
                    mountid = 1089;
                    expiration_days = 7;
                    break;
                case 2430211: //race
                    mountid = 80001009;
                    expiration_days = 30;
                    break;
                default:
                    System.out.println("New scripted item : " + toUse.getItemId());
                    break;
            }
        }
        if (mountid > 0) {
            mountid = c.getPlayer().getStat().getSkillByJob(mountid, c.getPlayer().getJob());
            final int fk = GameConstants.getMountItem(mountid, c.getPlayer());
//            if (GameConstants.GMS && fk > 0 && mountid < 80001000) { //TODO JUMP
//                for (int i = 80001001; i < 80001999; i++) {
//                    final Skill skill = SkillFactory.getSkill(i);
//                    if (skill != null && GameConstants.getMountItem(skill.getId(), c.getPlayer()) == fk) {
//                        mountid = i;
//                        break;
//                    }
//                }
//            }
            if (c.getPlayer().getSkillLevel(mountid) > 0) {
                c.getPlayer().dropMessage(5, "You already have this skill.");
            } else if (SkillFactory.getSkill(mountid) == null || GameConstants.getMountItem(mountid, c.getPlayer()) == 0) {
                c.getPlayer().dropMessage(5, "The skill could not be gained.");
            } else if (expiration_days > 0) {
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (byte) 1, false);
                c.getPlayer().changeSingleSkillLevel(SkillFactory.getSkill(mountid), (byte) 1, (byte) 1, System.currentTimeMillis() + (long) (expiration_days * 24 * 60 * 60 * 1000));
                c.getPlayer().dropMessage(5, "The skill has been attained.");
            }
        }
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void UseSummonBag(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (!chr.isAlive() || chr.hasBlockedInventory() || chr.inPVP()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        c.getPlayer().updateTick(slea.readInt());
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final Item toUse = chr.getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse != null && toUse.getQuantity() >= 1 && toUse.getItemId() == itemId && (c.getPlayer().getMapId() < 910000000 || c.getPlayer().getMapId() > 910000022)) {
            final Map<String, Integer> toSpawn = MapleItemInformationProvider.getInstance().getEquipStats(itemId);

            if (toSpawn == null) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            MapleMonster ht = null;
            int type = 0;
            for (Entry<String, Integer> i : toSpawn.entrySet()) {
                if (i.getKey().startsWith("mob") && Randomizer.nextInt(99) <= i.getValue()) {
                    ht = MapleLifeFactory.getMonster(Integer.parseInt(i.getKey().substring(3)));
                    chr.getMap().spawnMonster_sSack(ht, chr.getPosition(), type);
                }
            }
            if (ht == null) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }

            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
        }
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void UseTreasureChest(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        final short slot = slea.readShort();
        final int itemid = slea.readInt();

        final Item toUse = chr.getInventory(MapleInventoryType.ETC).getItem((byte) slot);
        if (toUse == null || toUse.getQuantity() <= 0 || toUse.getItemId() != itemid || chr.hasBlockedInventory()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        int reward;
        int keyIDforRemoval = 0;
        String box;

        switch (toUse.getItemId()) {
            case 4280000: // Gold box
                reward = RandomRewards.getGoldBoxReward();
                keyIDforRemoval = 5490000;
                box = "Gold";
                break;
            case 4280001: // Silver box
                reward = RandomRewards.getSilverBoxReward();
                keyIDforRemoval = 5490001;
                box = "Silver";
                break;
            default: // Up to no good
                return;
        }

        // Get the quantity
        int amount = 1;
        switch (reward) {
            case 2000004:
                amount = 200; // Elixir
                break;
            case 2000005:
                amount = 100; // Power Elixir
                break;
        }
        if (chr.getInventory(MapleInventoryType.CASH).countById(keyIDforRemoval) > 0) {
            final Item item = MapleInventoryManipulator.addbyId_Gachapon(c, reward, (short) amount);

            if (item == null) {
                chr.dropMessage(5, "Please check your item inventory and see if you have a Master Key, or if the inventory is full.");
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) slot, (short) 1, true);
            MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, keyIDforRemoval, 1, true, false);
            c.sendPacket(InfoPacket.getShowItemGain(reward, (short) amount, true));

//            if (GameConstants.gachaponRareItem(item.getItemId()) > 0) {
//                World.Broadcast.broadcastSmega(CWvsContext.getGachaponMega(c.getPlayer().getName(), " : got a(n)", item, (byte) 2, "[" + box + " Chest]"));
//            }
        } else {
            chr.dropMessage(5, "Please check your item inventory and see if you have a Master Key, or if the inventory is full.");
            c.sendPacket(CWvsContext.enableActions());
        }
    }

    public static final void UseCashItem(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer() == null || c.getPlayer().getMap() == null || c.getPlayer().inPVP()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        c.getPlayer().setScrolledPosition((short) 0);
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();

        final Item toUse = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem(slot);
        if (toUse == null || toUse.getItemId() != itemId || toUse.getQuantity() < 1 || c.getPlayer().hasBlockedInventory()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }

        boolean used = false, cc = false;

        switch (itemId) {
//            case 5043001: // NPC Teleport Rock
//            case 5043000: { // NPC Teleport Rock
//                final short questid = slea.readShort();
//                final int npcid = slea.readInt();
//                final MapleQuest quest = MapleQuest.getInstance(questid);
//
//                if (c.getPlayer().getQuest(quest).getStatus() == 1 && quest.canComplete(c.getPlayer(), npcid)) {
//                    final int mapId = MapleLifeFactory.getNPCLocation(npcid);
//                    if (mapId != -1) {
//                        final MapleMap map = c.getChannelServer().getMapFactory().getMap(mapId);
//                        if (map.containsNPC(npcid) && !FieldLimitType.VipRock.check(c.getPlayer().getMap().getFieldLimit()) && !FieldLimitType.VipRock.check(map.getFieldLimit()) && !c.getPlayer().isInBlockedMap()) {
//                            c.getPlayer().changeMap(map, map.getPortal(0));
//                        }
//                        used = true;
//                    } else {
//                        c.getPlayer().dropMessage(1, "Unknown error has occurred.");
//                    }
//                }
//                break;
//            }
            case 5041001:
            case 5040004:
            case 5040003:
            case 5040002:
            case 2320000: // 順移之石
            case 5041000: // 高級順移之石
            case 5040000: // 順移之石
            case 5040001: { // 可樂翅膀
                used = UseTeleRock(slea, c, itemId);
                break;
            }
            case 5042000: { // 豫園高級瞬移之石
                MapleMap map;
                map = c.getChannelServer().getMapFactory().getMap(701000200);
                c.getPlayer().changeMap(map, map.getPortal(0));
                used = true;
                break;
            }
            case 5042001: { // 不夜城高級瞬移之石
                MapleMap map;
                map = c.getChannelServer().getMapFactory().getMap(741000000);
                c.getPlayer().changeMap(map, map.getPortal(0));
                used = true;
                break;
            }
            case 5450005: {
                c.getPlayer().setConversation(4);
                c.getPlayer().getStorage().sendStorage(c, 1022005);
                break;
            }
            case 5050000: { // AP Reset
                Map<MapleStat, Integer> statupdate = new EnumMap<>(MapleStat.class);
                final int apto = slea.readInt();
                final int apfrom = slea.readInt();

                if (apto == apfrom) {
                    break; // Hack
                } else if (apfrom == 0x8000 && apto != 0x2000) {
                    c.getPlayer().dropMessage(1, "MP無法增加到HP以外的屬性唷");
                    break;
                }
                final int job = c.getPlayer().getJob();
                final PlayerStats playerst = c.getPlayer().getStat();
                used = true;

                switch (apto) { // AP to
                    case 0x100: // str
                        if (playerst.getStr() >= 999) {
                            used = false;
                        }
                        break;
                    case 0x200: // dex
                        if (playerst.getDex() >= 999) {
                            used = false;
                        }
                        break;
                    case 0x400: // int
                        if (playerst.getInt() >= 999) {
                            used = false;
                        }
                        break;
                    case 0x800: // luk
                        if (playerst.getLuk() >= 999) {
                            used = false;
                        }
                        break;
                    case 0x2000: // hp
                        if (playerst.getMaxHp() >= 30000 || c.getPlayer().getHpApUsed() >= 500) {
                            used = false;
                        }
                        break;
                    case 0x8000: // mp
                        if (playerst.getMaxMp() >= 30000 || c.getPlayer().getHpApUsed() >= 500) {
                            used = false;
                        }
                        break;
                }
                switch (apfrom) { // AP to
                    case 0x100: // str
                        if (playerst.getStr() <= 4 || (c.getPlayer().getJob() % 1000 / 100 == 1 && playerst.getStr() <= 35)) {
                            used = false;
                        }
                        break;
                    case 0x200: // dex
                        if (playerst.getDex() <= 4 || (c.getPlayer().getJob() % 1000 / 100 == 3 && playerst.getDex() <= 25) || (c.getPlayer().getJob() % 1000 / 100 == 4 && playerst.getDex() <= 25) || (c.getPlayer().getJob() % 1000 / 100 == 5 && playerst.getDex() <= 20)) {
                            used = false;
                        }
                        break;
                    case 0x400: // int
                        if (playerst.getInt() <= 4 || (c.getPlayer().getJob() % 1000 / 100 == 2 && playerst.getInt() <= 20)) {
                            used = false;
                        }
                        break;
                    case 0x800: // luk
                        if (playerst.getLuk() <= 4) {
                            used = false;
                        }
                        break;
                    case 0x2000: // hp
                        if (c.getPlayer().getHpApUsed() <= 0 || c.getPlayer().getHpApUsed() >= 500) {
                            used = false;
                        }
                        break;
                    case 0x8000: // mp
                        if (c.getPlayer().getHpApUsed() <= 0 || c.getPlayer().getHpApUsed() >= 500) {
                            used = false;
                        }
                        break;
                }
                if (used) {
                    switch (apto) { // AP to
                        case 0x100: { // str
                            final int toSet = playerst.getStr() + 1;
                            playerst.setStr((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.STR, toSet);
                            break;
                        }
                        case 0x200: { // dex
                            final int toSet = playerst.getDex() + 1;
                            playerst.setDex((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.DEX, toSet);
                            break;
                        }
                        case 0x400: { // int
                            final int toSet = playerst.getInt() + 1;
                            playerst.setInt((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.INT, toSet);
                            break;
                        }
                        case 0x800: { // luk
                            final int toSet = playerst.getLuk() + 1;
                            playerst.setLuk((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.LUK, toSet);
                            break;
                        }
                        case 0x2000: // hp
                            int maxhp = playerst.getMaxHp();
                            if (GameConstants.isBeginnerJob(job)) { // Beginner
                                maxhp += Randomizer.rand(4, 8);
                            } else if ((job >= 100 && job <= 132) || (job >= 3200 && job <= 3212) || (job >= 1100 && job <= 1112) || (job >= 3100 && job <= 3112)) { // Warrior
                                maxhp += Randomizer.rand(36, 42);
                            } else if ((job >= 200 && job <= 232) || (GameConstants.isEvan(job)) || (job >= 1200 && job <= 1212)) { // Magician
                                maxhp += Randomizer.rand(10, 12);
                            } else if ((job >= 300 && job <= 322) || (job >= 400 && job <= 434) || (job >= 1300 && job <= 1312) || (job >= 1400 && job <= 1412) || (job >= 3300 && job <= 3312) || (job >= 2300 && job <= 2312)) { // Bowman
                                maxhp += Randomizer.rand(14, 18);
                            } else if ((job >= 510 && job <= 512) || (job >= 1510 && job <= 1512)) {
                                maxhp += Randomizer.rand(24, 28);
                            } else if ((job >= 500 && job <= 532) || (job >= 3500 && job <= 3512) || job == 1500) { // Pirate
                                maxhp += Randomizer.rand(16, 20);
                            } else if (job >= 2000 && job <= 2112) { // Aran
                                maxhp += Randomizer.rand(34, 38);
                            } else { // GameMaster
                                maxhp += Randomizer.rand(50, 100);
                            }
                            maxhp = Math.min(30000, Math.abs(maxhp));
                            c.getPlayer().setHpApUsed((short) (c.getPlayer().getHpApUsed() + 1));
                            playerst.setMaxHp(maxhp, c.getPlayer());
                            statupdate.put(MapleStat.MAXHP, (int) maxhp);
                            break;

                        case 0x8000: // mp
                            int maxmp = playerst.getMaxMp();

                            if (GameConstants.isBeginnerJob(job)) { // Beginner
                                maxmp += Randomizer.rand(6, 8);
                            } else if (job >= 3100 && job <= 3112) {
                                break;
                            } else if ((job >= 100 && job <= 132) || (job >= 1100 && job <= 1112) || (job >= 2000 && job <= 2112)) { // Warrior
                                maxmp += Randomizer.rand(4, 9);
                            } else if ((job >= 200 && job <= 232) || (GameConstants.isEvan(job)) || (job >= 3200 && job <= 3212) || (job >= 1200 && job <= 1212)) { // Magician
                                maxmp += Randomizer.rand(32, 36);
                            } else if ((job >= 300 && job <= 322) || (job >= 400 && job <= 434) || (job >= 500 && job <= 532) || (job >= 3200 && job <= 3212) || (job >= 3500 && job <= 3512) || (job >= 1300 && job <= 1312) || (job >= 1400 && job <= 1412) || (job >= 1500 && job <= 1512) || (job >= 2300 && job <= 2312)) { // Bowman
                                maxmp += Randomizer.rand(8, 10);
                            } else { // GameMaster
                                maxmp += Randomizer.rand(50, 100);
                            }
                            maxmp = Math.min(30000, Math.abs(maxmp));
                            c.getPlayer().setHpApUsed((short) (c.getPlayer().getHpApUsed() + 1));
                            playerst.setMaxMp(maxmp, c.getPlayer());
                            statupdate.put(MapleStat.MAXMP, (int) maxmp);
                            break;
                    }
                    switch (apfrom) { // AP from
                        case 0x100: { // str
                            final int toSet = playerst.getStr() - 1;
                            playerst.setStr((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.STR, toSet);
                            break;
                        }
                        case 0x200: { // dex
                            final int toSet = playerst.getDex() - 1;
                            playerst.setDex((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.DEX, toSet);
                            break;
                        }
                        case 0x400: { // int
                            final int toSet = playerst.getInt() - 1;
                            playerst.setInt((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.INT, toSet);
                            break;
                        }
                        case 0x800: { // luk
                            final int toSet = playerst.getLuk() - 1;
                            playerst.setLuk((short) toSet, c.getPlayer());
                            statupdate.put(MapleStat.LUK, toSet);
                            break;
                        }
                        case 0x2000: // HP
                            int maxhp = playerst.getMaxHp();
                            if (GameConstants.isBeginnerJob(job)) { // Beginner
                                maxhp -= 12;
                            } else if ((job >= 200 && job <= 232) || (job >= 1200 && job <= 1212)) { // Magician
                                maxhp -= 10;
                            } else if ((job >= 300 && job <= 322) || (job >= 400 && job <= 434) || (job >= 1300 && job <= 1312) || (job >= 1400 && job <= 1412) || (job >= 3300 && job <= 3312) || (job >= 3500 && job <= 3512) || (job >= 2300 && job <= 2312)) { // Bowman, Thief
                                maxhp -= 15;
                            } else if ((job >= 500 && job <= 532) || (job >= 1500 && job <= 1512)) { // Pirate
                                maxhp -= 22;
                            } else if (((job >= 100 && job <= 132) || job >= 1100 && job <= 1112) || (job >= 3100 && job <= 3112)) { // Soul Master
                                maxhp -= 32;
                            } else if ((job >= 2000 && job <= 2112) || (job >= 3200 && job <= 3212)) { // Aran
                                maxhp -= 40;
                            } else { // GameMaster
                                maxhp -= 20;
                            }
                            c.getPlayer().setHpApUsed((short) (c.getPlayer().getHpApUsed() - 1));
                            playerst.setMaxHp(maxhp, c.getPlayer());
                            statupdate.put(MapleStat.MAXHP, (int) maxhp);
                            break;
                        case 0x8000: // MP
                            int maxmp = playerst.getMaxMp();
                            if (GameConstants.isBeginnerJob(job)) { // Beginner
                                maxmp -= 8;
                            } else if (job >= 3100 && job <= 3112) {
                                break;
                            } else if ((job >= 100 && job <= 132) || (job >= 1100 && job <= 1112)) { // Warrior
                                maxmp -= 4;
                            } else if ((job >= 200 && job <= 232) || (job >= 1200 && job <= 1212)) { // Magician
                                maxmp -= 30;
                            } else if ((job >= 500 && job <= 532) || (job >= 300 && job <= 322) || (job >= 400 && job <= 434) || (job >= 1300 && job <= 1312) || (job >= 1400 && job <= 1412) || (job >= 1500 && job <= 1512) || (job >= 3300 && job <= 3312) || (job >= 3500 && job <= 3512) || (job >= 2300 && job <= 2312)) { // Pirate, Bowman. Thief
                                maxmp -= 10;
                            } else if (job >= 2000 && job <= 2112) { // Aran
                                maxmp -= 5;
                            } else { // GameMaster
                                maxmp -= 20;
                            }
                            c.getPlayer().setHpApUsed((short) (c.getPlayer().getHpApUsed() - 1));
                            playerst.setMaxMp(maxmp, c.getPlayer());
                            statupdate.put(MapleStat.MAXMP, (int) maxmp);
                            break;
                    }
                    c.sendPacket(CWvsContext.updatePlayerStats(statupdate, true, c.getPlayer()));
                }
                break;
            }
            case 5220083: {//starter pack
                used = true;
                for (Entry<Integer, StructFamiliar> f : MapleItemInformationProvider.getInstance().getFamiliars().entrySet()) {
                    if (f.getValue().itemid == 2870055 || f.getValue().itemid == 2871002 || f.getValue().itemid == 2870235 || f.getValue().itemid == 2870019) {
                        MonsterFamiliar mf = c.getPlayer().getFamiliars().get(f.getKey());
                        if (mf != null) {
                            if (mf.getVitality() >= 3) {
                                mf.setExpiry((long) Math.min(System.currentTimeMillis() + 90 * 24 * 60 * 60000L, mf.getExpiry() + 30 * 24 * 60 * 60000L));
                            } else {
                                mf.setVitality(mf.getVitality() + 1);
                                mf.setExpiry((long) (mf.getExpiry() + 30 * 24 * 60 * 60000L));
                            }
                        } else {
                            mf = new MonsterFamiliar(c.getPlayer().getId(), f.getKey(), (long) (System.currentTimeMillis() + 30 * 24 * 60 * 60000L));
                            c.getPlayer().getFamiliars().put(f.getKey(), mf);
                        }
                        c.sendPacket(CField.registerFamiliar(mf));
                    }
                }
                break;
            }
            case 5220084: {//booster pack
                if (c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() < 3) {
                    c.getPlayer().dropMessage(5, "Make 3 USE space.");
                    break;
                }
                used = true;
                int[] familiars = new int[3];
                while (true) {
                    for (int i = 0; i < familiars.length; i++) {
                        if (familiars[i] > 0) {
                            continue;
                        }
                        for (Map.Entry<Integer, StructFamiliar> f : MapleItemInformationProvider.getInstance().getFamiliars().entrySet()) {
                            if (Randomizer.nextInt(500) == 0 && ((i < 2 && f.getValue().grade == 0 || (i == 2 && f.getValue().grade != 0)))) {
                                MapleInventoryManipulator.addById(c, f.getValue().itemid, (short) 1, "Booster Pack");
                                //c.sendPacket(CField.getBoosterFamiliar(c.getPlayer().getId(), f.getKey(), 0));
                                familiars[i] = f.getValue().itemid;
                                break;
                            }
                        }
                    }
                    if (familiars[0] > 0 && familiars[1] > 0 && familiars[2] > 0) {
                        break;
                    }
                }
                c.sendPacket(MTSCSPacket.getBoosterPack(familiars[0], familiars[1], familiars[2]));
                c.sendPacket(MTSCSPacket.getBoosterPackClick());
                c.sendPacket(MTSCSPacket.getBoosterPackReveal());
                break;
            }
            case 5050001: // SP Reset (1st job)
            case 5050002: // SP Reset (2nd job)
            case 5050003: // SP Reset (3rd job)
            case 5050004:  // SP Reset (4th job)
            case 5050005: //evan sp resets
            case 5050006:
            case 5050007:
            case 5050008:
            case 5050009: {
                if (itemId >= 5050005 && !GameConstants.isEvan(c.getPlayer().getJob())) {
                    c.getPlayer().dropMessage(1, "This reset is only for Evans.");
                    break;
                } //well i dont really care other than this o.o
                if (itemId < 5050005 && GameConstants.isEvan(c.getPlayer().getJob())) {
                    c.getPlayer().dropMessage(1, "This reset is only for non-Evans.");
                    break;
                } //well i dont really care other than this o.o
                int skill1 = slea.readInt();
                int skill2 = slea.readInt();
                for (int i : GameConstants.blockedSkills) {
                    if (skill1 == i) {
                        c.getPlayer().dropMessage(1, "You may not add this skill.");
                        return;
                    }
                }

                Skill skillSPTo = SkillFactory.getSkill(skill1);
                Skill skillSPFrom = SkillFactory.getSkill(skill2);

                if (skillSPTo.isBeginnerSkill() || skillSPFrom.isBeginnerSkill()) {
                    c.getPlayer().dropMessage(1, "You may not add beginner skills.");
                    break;
                }
                if (GameConstants.getSkillBookForSkill(skill1) != GameConstants.getSkillBookForSkill(skill2)) { //resistance evan
                    c.getPlayer().dropMessage(1, "You may not add different job skills.");
                    break;
                }
                //if (GameConstants.getJobNumber(skill1 / 10000) > GameConstants.getJobNumber(skill2 / 10000)) { //putting 3rd job skillpoints into 4th job for example
                //    c.getPlayer().dropMessage(1, "You may not add skillpoints to a higher job.");
                //    break;
                //}
                if ((c.getPlayer().getSkillLevel(skillSPTo) + 1 <= skillSPTo.getMaxLevel()) && c.getPlayer().getSkillLevel(skillSPFrom) > 0 && skillSPTo.canBeLearnedBy(c.getPlayer().getJob())) {
                    if (skillSPTo.isFourthJob() && (c.getPlayer().getSkillLevel(skillSPTo) + 1 > c.getPlayer().getMasterLevel(skillSPTo))) {
                        c.getPlayer().dropMessage(1, "You will exceed the master level.");
                        break;
                    }
                    if (itemId >= 5050005) {
                        if (GameConstants.getSkillBookForSkill(skill1) != (itemId - 5050005) * 2 && GameConstants.getSkillBookForSkill(skill1) != (itemId - 5050005) * 2 + 1) {
                            c.getPlayer().dropMessage(1, "You may not add this job SP using this reset.");
                            break;
                        }
                    } else {
                        int theJob = GameConstants.getJobNumber(skill2 / 10000);
                        switch (skill2 / 10000) {
                            case 430:
                                theJob = 1;
                                break;
                            case 432:
                            case 431:
                                theJob = 2;
                                break;
                            case 433:
                                theJob = 3;
                                break;
                            case 434:
                                theJob = 4;
                                break;
                        }
                        if (theJob != itemId - 5050000) { //you may only subtract from the skill if the ID matches Sp reset
                            c.getPlayer().dropMessage(1, "You may not subtract from this skill. Use the appropriate SP reset.");
                            break;
                        }
                    }
                    final Map<Skill, SkillEntry> sa = new HashMap<>();
                    sa.put(skillSPFrom, new SkillEntry((byte) (c.getPlayer().getSkillLevel(skillSPFrom) - 1), c.getPlayer().getMasterLevel(skillSPFrom), SkillFactory.getDefaultSExpiry(skillSPFrom)));
                    sa.put(skillSPTo, new SkillEntry((byte) (c.getPlayer().getSkillLevel(skillSPTo) + 1), c.getPlayer().getMasterLevel(skillSPTo), SkillFactory.getDefaultSExpiry(skillSPTo)));
                    c.getPlayer().changeSkillsLevel(sa);
                    used = true;
                }
                break;
            }
            case 5500000: { // Magic Hourglass 1 day
                final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slea.readShort());
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final int days = 1;
                if (item != null && !GameConstants.isAccessory(item.getItemId()) && item.getExpiration() > -1 && !ii.isCash(item.getItemId()) && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L) > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                    boolean change = true;
                    for (String z : GameConstants.RESERVED) {
                        if (c.getPlayer().getName().indexOf(z) != -1 || item.getOwner().indexOf(z) != -1) {
                            change = false;
                        }
                    }
                    if (change) {
                        item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                        c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(1, "It may not be used on this item.");
                    }
                }
                break;
            }
            case 5500001: { // Magic Hourglass 7 day
                final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slea.readShort());
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final int days = 7;
                if (item != null && !GameConstants.isAccessory(item.getItemId()) && item.getExpiration() > -1 && !ii.isCash(item.getItemId()) && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L) > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                    boolean change = true;
                    for (String z : GameConstants.RESERVED) {
                        if (c.getPlayer().getName().indexOf(z) != -1 || item.getOwner().indexOf(z) != -1) {
                            change = false;
                        }
                    }
                    if (change) {
                        item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                        c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(1, "It may not be used on this item.");
                    }
                }
                break;
            }
            case 5500002: { // Magic Hourglass 20 day
                final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slea.readShort());
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final int days = 20;
                if (item != null && !GameConstants.isAccessory(item.getItemId()) && item.getExpiration() > -1 && !ii.isCash(item.getItemId()) && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L) > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                    boolean change = true;
                    for (String z : GameConstants.RESERVED) {
                        if (c.getPlayer().getName().indexOf(z) != -1 || item.getOwner().indexOf(z) != -1) {
                            change = false;
                        }
                    }
                    if (change) {
                        item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                        c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(1, "It may not be used on this item.");
                    }
                }
                break;
            }
            case 5500005: { // Magic Hourglass 50 day
                final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slea.readShort());
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final int days = 50;
                if (item != null && !GameConstants.isAccessory(item.getItemId()) && item.getExpiration() > -1 && !ii.isCash(item.getItemId()) && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L) > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                    boolean change = true;
                    for (String z : GameConstants.RESERVED) {
                        if (c.getPlayer().getName().indexOf(z) != -1 || item.getOwner().indexOf(z) != -1) {
                            change = false;
                        }
                    }
                    if (change) {
                        item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                        c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(1, "It may not be used on this item.");
                    }
                }
                break;
            }
            case 5500006: { // Magic Hourglass 99 day
                final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slea.readShort());
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final int days = 99;
                if (item != null && !GameConstants.isAccessory(item.getItemId()) && item.getExpiration() > -1 && !ii.isCash(item.getItemId()) && System.currentTimeMillis() + (100 * 24 * 60 * 60 * 1000L) > item.getExpiration() + (days * 24 * 60 * 60 * 1000L)) {
                    boolean change = true;
                    for (String z : GameConstants.RESERVED) {
                        if (c.getPlayer().getName().indexOf(z) != -1 || item.getOwner().indexOf(z) != -1) {
                            change = false;
                        }
                    }
                    if (change) {
                        item.setExpiration(item.getExpiration() + (days * 24 * 60 * 60 * 1000));
                        c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(1, "It may not be used on this item.");
                    }
                }
                break;
            }
            case 5060000: { // Item Tag
                final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slea.readShort());

                if (item != null && item.getOwner().equals("")) {
                    boolean change = true;
                    for (String z : GameConstants.RESERVED) {
                        if (c.getPlayer().getName().indexOf(z) != -1) {
                            change = false;
                        }
                    }
                    if (change) {
                        item.setOwner(c.getPlayer().getName());
                        c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIPPED);
                        used = true;
                    }
                }
                break;
            }
            case 5680015: {
                if (c.getPlayer().getFatigue() > 0) {
                    c.getPlayer().setFatigue(0);
                    used = true;
                }
                break;
            }
            case 5534000: { //tims lab
                final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readInt());
                if (item != null) {
                    final Equip eq = (Equip) item;
                    if (eq.getState() == 0) {
                        eq.resetPotential();
                        c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), true, itemId));
                        c.sendPacket(InventoryPacket.scrolledItem(toUse, item, false, true));
                        c.getPlayer().forceReAddItem_NoUpdate(item, MapleInventoryType.EQUIP);
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "This item's Potential cannot be reset.");
                    }
                } else {
                    c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), false, itemId));
                }
                break;
            }
            case 5062000: { //miracle cube
                if (c.getPlayer().getLevel() < 50) {
                    c.getPlayer().dropMessage(1, "You may not use this until level 50.");
                } else {
                    final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readInt());
                    if (item != null && c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() >= 1) {
                        final Equip eq = (Equip) item;
                        if (eq.getState() >= 17 && eq.getState() != 20) {
                            eq.renewPotential(0);
                            c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), true, itemId));
                            c.sendPacket(InventoryPacket.scrolledItem(toUse, item, false, true));
                            c.getPlayer().forceReAddItem_NoUpdate(item, MapleInventoryType.EQUIP);
                            MapleInventoryManipulator.addById(c, 2430112, (short) 1, "Cube" + " on " + FileoutputUtil.CurrentReadable_Date());
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(5, "This item's Potential cannot be reset.");
                        }
                    } else {
                        c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), false, itemId));
                    }
                }
                break;
            }
            case 5062100:
            case 5062001: { //premium cube
                if (c.getPlayer().getLevel() < 70) {
                    c.getPlayer().dropMessage(1, "You may not use this until level 70.");
                } else {
                    final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readInt());
                    if (item != null && c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() >= 1) {
                        final Equip eq = (Equip) item;
                        if (eq.getState() >= 17 && eq.getState() != 20) {
                            eq.renewPotential(1);
                            c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), true, itemId));
                            c.sendPacket(InventoryPacket.scrolledItem(toUse, item, false, true));
                            c.getPlayer().forceReAddItem_NoUpdate(item, MapleInventoryType.EQUIP);
                            MapleInventoryManipulator.addById(c, 2430112, (short) 1, "Cube" + " on " + FileoutputUtil.CurrentReadable_Date());
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(5, "This item's Potential cannot be reset.");
                        }
                    } else {
                        c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), false, itemId));
                    }
                }
                break;
            }
            case 5062002: { //super miracle cube
                if (c.getPlayer().getLevel() < 100) {
                    c.getPlayer().dropMessage(1, "You may not use this until level 100.");
                } else {
                    final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readInt());
                    if (item != null && c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() >= 1) {
                        final Equip eq = (Equip) item;
                        if (eq.getState() >= 17) {
                            eq.renewPotential(3);
                            c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), true, itemId));
                            c.sendPacket(InventoryPacket.scrolledItem(toUse, item, false, true));
                            c.getPlayer().forceReAddItem_NoUpdate(item, MapleInventoryType.EQUIP);
                            MapleInventoryManipulator.addById(c, 2430481, (short) 1, "Cube" + " on " + FileoutputUtil.CurrentReadable_Date());
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(5, "This item's Potential cannot be reset.");
                        }
                    } else {
                        c.getPlayer().getMap().broadcastMessage(CField.showPotentialReset(false, c.getPlayer().getId(), false, itemId));
                    }
                }
                break;
            }
            case 5750000: { //alien cube
                if (c.getPlayer().getLevel() < 10) {
                    c.getPlayer().dropMessage(1, "You may not use this until level 10.");
                } else {
                    final Item item = c.getPlayer().getInventory(MapleInventoryType.SETUP).getItem((byte) slea.readInt());
                    if (item != null && c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() >= 1 && c.getPlayer().getInventory(MapleInventoryType.SETUP).getNumFreeSlot() >= 1) {
                        final int grade = GameConstants.getNebuliteGrade(item.getItemId());
                        if (grade != -1 && grade < 4) {
                            final int rank = Randomizer.nextInt(100) < 7 ? (Randomizer.nextInt(100) < 2 ? (grade + 1) : (grade != 3 ? (grade + 1) : grade)) : grade;
                            final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                            final List<StructItemOption> pots = new LinkedList<>(ii.getAllSocketInfo(rank).values());
                            int newId = 0;
                            while (newId == 0) {
                                StructItemOption pot = pots.get(Randomizer.nextInt(pots.size()));
                                if (pot != null) {
                                    newId = pot.opID;
                                }
                            }
                            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.SETUP, item.getPosition(), (short) 1, false);
                            MapleInventoryManipulator.addById(c, newId, (short) 1, "Upgraded from alien cube on " + FileoutputUtil.CurrentReadable_Date());
                            MapleInventoryManipulator.addById(c, 2430691, (short) 1, "Alien Cube" + " on " + FileoutputUtil.CurrentReadable_Date());
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(1, "Grade S Nebulite cannot be added.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "You do not have sufficient inventory slot.");
                    }
                }
                break;
            }
            case 5750001: { // socket diffuser
                if (c.getPlayer().getLevel() < 10) {
                    c.getPlayer().dropMessage(1, "You may not use this until level 10.");
                } else {
                    final Item item = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readInt());
                    if (item != null) {
                        final Equip eq = (Equip) item;
                        if (eq.getSocket1() > 0) { // first slot only.
                            eq.setSocket1(0);
                            c.sendPacket(InventoryPacket.scrolledItem(toUse, item, false, true));
                            c.getPlayer().forceReAddItem_NoUpdate(item, MapleInventoryType.EQUIP);
                            used = true;
                        } else {
                            c.getPlayer().dropMessage(5, "This item do not have a socket.");
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "This item's nebulite cannot be removed.");
                    }
                }
                break;
            }
            case 5521000: { // Karma
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());

                if (item != null && !ItemFlag.KARMA_ACC.check(item.getFlag()) && !ItemFlag.KARMA_ACC_USE.check(item.getFlag())) {
                    if (MapleItemInformationProvider.getInstance().isShareTagEnabled(item.getItemId())) {
                        short flag = item.getFlag();
                        if (ItemFlag.UNTRADEABLE.check(flag)) {
                            flag -= ItemFlag.UNTRADEABLE.getValue();
                        } else if (type == MapleInventoryType.EQUIP) {
                            flag |= ItemFlag.KARMA_ACC.getValue();
                        } else {
                            flag |= ItemFlag.KARMA_ACC_USE.getValue();
                        }
                        item.setFlag(flag);
                        c.getPlayer().forceReAddItem_NoUpdate(item, type);
                        c.sendPacket(InventoryPacket.updateSpecialItemUse(item, type.getType(), item.getPosition(), true, c.getPlayer()));
                        used = true;
                    }
                }
                break;
            }
            case 5520001: //p.karma
            case 5520000: { // Karma
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());

                if (item != null && !ItemFlag.KARMA_EQ.check(item.getFlag()) && !ItemFlag.KARMA_USE.check(item.getFlag())) {
                    if ((itemId == 5520000 && MapleItemInformationProvider.getInstance().isKarmaEnabled(item.getItemId())) || (itemId == 5520001 && MapleItemInformationProvider.getInstance().isPKarmaEnabled(item.getItemId()))) {
                        short flag = item.getFlag();
                        if (ItemFlag.UNTRADEABLE.check(flag)) {
                            flag -= ItemFlag.UNTRADEABLE.getValue();
                        } else if (type == MapleInventoryType.EQUIP) {
                            flag |= ItemFlag.KARMA_EQ.getValue();
                        } else {
                            flag |= ItemFlag.KARMA_USE.getValue();
                        }
                        item.setFlag(flag);
                        c.getPlayer().forceReAddItem_NoUpdate(item, type);
                        c.sendPacket(InventoryPacket.updateSpecialItemUse(item, type.getType(), item.getPosition(), true, c.getPlayer()));
                        used = true;
                    }
                }
                break;
            }
            case 5570000: { // Vicious Hammer
                slea.readInt(); // Inventory type, Hammered eq is always EQ.
                final Equip item = (Equip) c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem((byte) slea.readInt());
                // another int here, D3 49 DC 00
                if (item != null) {
                    if (GameConstants.canHammer(item.getItemId()) && MapleItemInformationProvider.getInstance().getSlots(item.getItemId()) > 0 && item.getViciousHammer() < 2) {
                        item.setViciousHammer((byte) (item.getViciousHammer() + 1));
                        item.setUpgradeSlots((byte) (item.getUpgradeSlots() + 1));
                        c.getPlayer().forceReAddItem(item, MapleInventoryType.EQUIP);
                        c.sendPacket(MTSCSPacket.ViciousHammer(true, (byte) item.getViciousHammer()));
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(5, "You may not use it on this item.");
                        c.sendPacket(MTSCSPacket.ViciousHammer(true, (byte) 0));
                    }
                }

                break;
            }
            case 5610001:
            case 5610000: { // Vega 30
                slea.readInt(); // Inventory type, always eq
                final short dst = (short) slea.readInt();
                slea.readInt(); // Inventory type, always use
                final short src = (short) slea.readInt();
                used = UseUpgradeScroll(src, dst, (short) 2, c, c.getPlayer(), itemId, false); //cannot use ws with vega but we dont care
                cc = used;
                break;
            }
            case 5060001: { // Sealing Lock
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                if (item != null && item.getExpiration() == -1) {
                    short flag = item.getFlag();
                    flag |= ItemFlag.LOCK.getValue();
                    item.setFlag(flag);

                    c.getPlayer().forceReAddItem_Flag(item, type);
                    used = true;
                }
                break;
            }
            case 5061000: { // Sealing Lock 7 days
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                if (item != null && item.getExpiration() == -1) {
                    short flag = item.getFlag();
                    flag |= ItemFlag.LOCK.getValue();
                    item.setFlag(flag);
                    item.setExpiration(System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000));

                    c.getPlayer().forceReAddItem_Flag(item, type);
                    used = true;
                }
                break;
            }
            case 5061001: { // Sealing Lock 30 days
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                if (item != null && item.getExpiration() == -1) {
                    short flag = item.getFlag();
                    flag |= ItemFlag.LOCK.getValue();
                    item.setFlag(flag);

                    item.setExpiration(System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000));

                    c.getPlayer().forceReAddItem_Flag(item, type);
                    used = true;
                }
                break;
            }
            case 5061002: { // Sealing Lock 90 days
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                if (item != null && item.getExpiration() == -1) {
                    short flag = item.getFlag();
                    flag |= ItemFlag.LOCK.getValue();
                    item.setFlag(flag);

                    item.setExpiration(System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000));

                    c.getPlayer().forceReAddItem_Flag(item, type);
                    used = true;
                }
                break;
            }
            case 5061003: { // Sealing Lock 365 days
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                if (item != null && item.getExpiration() == -1) {
                    short flag = item.getFlag();
                    flag |= ItemFlag.LOCK.getValue();
                    item.setFlag(flag);

                    item.setExpiration(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000));

                    c.getPlayer().forceReAddItem_Flag(item, type);
                    used = true;
                }
                break;
            }
            case 5063000: {
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                if (item != null && item.getType() == 1) { //equip
                    short flag = item.getFlag();
                    flag |= ItemFlag.LUCKS_KEY.getValue();
                    item.setFlag(flag);

                    c.getPlayer().forceReAddItem_Flag(item, type);
                    used = true;
                }
                break;
            }
            case 5064000: {
                System.out.println("slea..." + slea.toString());
                final MapleInventoryType type = MapleInventoryType.getByType((byte) slea.readInt());
                final Item item = c.getPlayer().getInventory(type).getItem((byte) slea.readInt());
                // another int here, lock = 5A E5 F2 0A, 7 day = D2 30 F3 0A
                if (item != null && item.getType() == 1) { //equip
                    if (((Equip) item).getEnhance() >= 8) {
                        break; //cannot be used
                    }
                    short flag = item.getFlag();
                    flag |= ItemFlag.SHIELD_WARD.getValue();
                    item.setFlag(flag);

                    c.getPlayer().forceReAddItem_Flag(item, type);
                    used = true;
                }
                break;
            }
            case 5060004:
            case 5060003: {//peanut
                Item item = c.getPlayer().getInventory(MapleInventoryType.ETC).findById(itemId == 5060003 ? 4170023 : 4170024);
                if (item == null || item.getQuantity() <= 0) { // hacking{
                    return;
                }
                if (getIncubatedItems(c, itemId)) {
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, item.getPosition(), (short) 1, false);
                    used = true;
                }
                break;
            }

            case 5070000: { // Megaphone
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    String message = slea.readMapleAsciiString();

                    if (!ServerConstants.isCanTalkText(message)) {
                        c.getPlayer().dropMessage(5, "請勿使用粗言穢語。");
                        break;
                    }

                    if (!c.getPlayer().getCanTalk()) {
                        message = ServerConstants.RandomSorry();
                    }

                    if (message.getBytes().length > 65) {
                        break;
                    }
                    final StringBuilder sb = new StringBuilder();
//                    addMedalString(c.getPlayer(), sb);
                    sb.append(c.getPlayer().getName());
                    sb.append(" : ");
                    sb.append(message);
                    c.getPlayer().getMap().broadcastMessage(CWvsContext.serverNotice(2, sb.toString()));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5071000: { // Megaphone
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    String message = slea.readMapleAsciiString();

                    if (!ServerConstants.isCanTalkText(message)) {
                        c.getPlayer().dropMessage(5, "請勿使用粗言穢語。");
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        message = ServerConstants.RandomSorry();
                    }
                    if (message.getBytes().length > 65) {
                        break;
                    }
                    final StringBuilder sb = new StringBuilder();
//                    addMedalString(c.getPlayer(), sb);
                    sb.append(c.getPlayer().getName());
                    sb.append(" : ");
                    sb.append(message);

                    c.getChannelServer().broadcastSmegaPacket(CWvsContext.serverNotice(2, sb.toString()));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5077000: { // 3 line Megaphone
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    final byte numLines = slea.readByte();
                    if (numLines > 3) {
                        return;
                    }
                    final List<String> messages = new LinkedList<>();
                    String message = "";
                    for (int i = 0; i < numLines; i++) {
                        message = slea.readMapleAsciiString();
                        if (message.getBytes().length > 65) {
                            break;
                        }
                        messages.add(c.getPlayer().getName() + " : " + message);
                    }
                    final boolean ear = slea.readByte() > 0;

                    World.Broadcast.broadcastSmega(CWvsContext.tripleSmega(messages, ear, c.getChannel()));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message.toString());
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5079004: { // Heart Megaphone
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    String message = slea.readMapleAsciiString();

                    if (!ServerConstants.isCanTalkText(message)) {
                        c.getPlayer().dropMessage(5, "請勿使用粗言穢語。");
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        message = ServerConstants.RandomSorry();
                    }
                    if (message.getBytes().length > 65) {
                        break;
                    }
                    World.Broadcast.broadcastSmega(CWvsContext.echoMegaphone(c.getPlayer().getName(), message));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5073000: { // 愛心喇叭
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    String message = slea.readMapleAsciiString();

                    if (!ServerConstants.isCanTalkText(message)) {
                        c.getPlayer().dropMessage(5, "請勿使用粗言穢語。");
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        message = ServerConstants.RandomSorry();
                    }
                    if (message.getBytes().length > 65) {
                        break;
                    }
                    final StringBuilder sb = new StringBuilder();
//                    addMedalString(c.getPlayer(), sb);
                    sb.append(c.getPlayer().getName());
                    sb.append(" : ");
                    sb.append(message);

                    final boolean ear = slea.readByte() != 0;
                    World.Broadcast.broadcastSmega(CWvsContext.serverNotice(8, c.getChannel(), sb.toString(), ear));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5074000: { // 骷髏喇叭
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    String message = slea.readMapleAsciiString();

                    if (!ServerConstants.isCanTalkText(message)) {
                        c.getPlayer().dropMessage(5, "請勿使用粗言穢語。");
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        message = ServerConstants.RandomSorry();
                    }
                    if (message.getBytes().length > 65) {
                        break;
                    }
                    final StringBuilder sb = new StringBuilder();
//                    addMedalString(c.getPlayer(), sb);
                    sb.append(c.getPlayer().getName());
                    sb.append(" : ");
                    sb.append(message);

                    final boolean ear = slea.readByte() != 0;

                    World.Broadcast.broadcastSmega(CWvsContext.serverNotice(9, c.getChannel(), sb.toString(), ear));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5072000: { // Super Megaphone
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    String message = slea.readMapleAsciiString();

                    if (!ServerConstants.isCanTalkText(message)) {
                        c.getPlayer().dropMessage(5, "請勿使用粗言穢語。");
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        message = ServerConstants.RandomSorry();
                    }
                    if (message.getBytes().length > 65) {
                        break;
                    }
                    final StringBuilder sb = new StringBuilder();
                    addMedalString(c.getPlayer(), sb);
                    sb.append(c.getPlayer().getName());
                    sb.append(" : ");
                    sb.append(message);

                    final boolean ear = slea.readByte() != 0;

                    World.Broadcast.broadcastSmega(CWvsContext.serverNotice(3, c.getChannel(), sb.toString(), ear));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5076000: { // Item Megaphone
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    String message = slea.readMapleAsciiString();

                    if (!ServerConstants.isCanTalkText(message)) {
                        c.getPlayer().dropMessage(5, "請勿使用粗言穢語。");
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        message = ServerConstants.RandomSorry();
                    }
                    if (message.getBytes().length > 65) {
                        break;
                    }
                    final StringBuilder sb = new StringBuilder();
                    addMedalString(c.getPlayer(), sb);
                    sb.append(c.getPlayer().getName());
                    sb.append(" : ");
                    sb.append(message);

                    final boolean ear = slea.readByte() > 0;

                    Item item = null;
                    if (slea.readByte() == 1) { //item
                        byte invType = (byte) slea.readInt();
                        byte pos = (byte) slea.readInt();
                        if (pos <= 0) {
                            invType = -1;
                        }
                        item = c.getPlayer().getInventory(MapleInventoryType.getByType(invType)).getItem(pos);
                    }
                    World.Broadcast.broadcastSmega(CWvsContext.itemMegaphone(sb.toString(), ear, c.getChannel(), item));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "The usage of Megaphone is currently disabled.");
                }
                break;
            }
            case 5075000: // MapleTV Messenger
            case 5075001: // MapleTV Star Messenger
            case 5075002: { // MapleTV Heart Messenger
                c.getPlayer().dropMessage(5, "There are no MapleTVs to broadcast the message to.");
                break;
            }
            case 5075003:
            case 5075004:
            case 5075005: {
                if (c.getPlayer().getLevel() < 10) {
                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
                    break;
                }
                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
                    c.getPlayer().dropMessage(5, "Cannot be used here.");
                    break;
                }
                if (!c.getPlayer().getCheatTracker().canSmega()) {
                    c.getPlayer().dropMessage(5, "請耐心等候10秒鐘。");
                    break;
                }
                int tvType = itemId % 10;
                if (tvType == 3) {
                    slea.readByte(); //who knows
                }
                boolean ear = tvType != 1 && tvType != 2 && slea.readByte() > 1; //for tvType 1/2, there is no byte. 
                MapleCharacter victim = tvType == 1 || tvType == 4 ? null : c.getChannelServer().getPlayerStorage().getCharacterByName(slea.readMapleAsciiString()); //for tvType 4, there is no string.
                if (tvType == 0 || tvType == 3) { //doesn't allow two
                    victim = null;
                } else if (victim == null) {
                    c.getPlayer().dropMessage(1, "That character is not in the channel.");
                    break;
                }
                String message = slea.readMapleAsciiString();
                if (!c.getPlayer().getCanTalk()) {
                    message = ServerConstants.RandomSorry();
                }
                World.Broadcast.broadcastSmega(CWvsContext.serverNotice(3, c.getChannel(), c.getPlayer().getName() + " : " + message, ear));
                used = true;
                if (ServerConstants.LOG_MEGA) {
                    FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + message);
                }
                break;
            }
            case 5090100: // Wedding Invitation Card
            case 5090000: { // Note
                final String sendTo = slea.readMapleAsciiString();
                final String msg = slea.readMapleAsciiString();
                if (MapleCharacterUtil.canCreateChar(sendTo, false)) { // Name does not exist 
                    c.sendPacket(MTSCSPacket.OnMemoResult((byte) 4, (byte) 1));
                } else {
                    int ch = World.Find.findChannel(sendTo);
                    if (ch <= 0) { // offline 
                        c.getPlayer().sendNote(sendTo, msg);
                        c.sendPacket(MTSCSPacket.OnMemoResult((byte) 3, (byte) 0));
                        used = true;
                    } else {
                        c.sendPacket(MTSCSPacket.OnMemoResult((byte) 4, (byte) 0));
                    }
                }
                break;
            }
            case 5100000: { // Congratulatory Song
                c.getPlayer().getMap().broadcastMessage(CField.musicChange("Jukebox/Congratulation"));
                used = true;
                break;
            }
            /* 日拋隱形眼鏡 */
            case 5152100:
            case 5152101:
            case 5152102:
            case 5152103:
            case 5152104:
            case 5152105:
            case 5152106:
            case 5152107: {
                int color = (itemId - 5152100) * 100;
                if (c.getPlayer().getLevel() < 30) {
                    c.getPlayer().dropMessage(1, "必須等級30級以上才可以使用.");
                    break;
                }
                if (color >= 0 && c.getPlayer().changeFace(itemId, color)) {
                    used = true;
                } else {
                    c.getPlayer().dropMessage(1, "使用日拋隱形眼鏡出現錯誤。");
                }
                break;
            }
            case 5190001:
            case 5190002:
            case 5190003:
            case 5190004:
            case 5190005:
            case 5190006:
            case 5190007:
            case 5190008:
            case 5190000: { // Pet Flags
                final int uniqueid = (int) slea.readLong();
                MaplePet pet = c.getPlayer().getPet(0);
                int slo = 0;

                if (pet == null) {
                    break;
                }
                if (pet.getUniqueId() != uniqueid) {
                    pet = c.getPlayer().getPet(1);
                    slo = 1;
                    if (pet != null) {
                        if (pet.getUniqueId() != uniqueid) {
                            pet = c.getPlayer().getPet(2);
                            slo = 2;
                            if (pet != null) {
                                if (pet.getUniqueId() != uniqueid) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    } else {
                        break;
                    }
                }
                PetFlag zz = PetFlag.getByAddId(itemId);
                if (zz != null && !zz.check(pet.getFlags())) {
                    pet.setFlags(pet.getFlags() | zz.getValue());
                    c.sendPacket(PetPacket.updatePet(pet, c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) pet.getInventoryPosition()), true));
                    c.sendPacket(CWvsContext.enableActions());
                    c.sendPacket(MTSCSPacket.changePetFlag(uniqueid, true, zz.getValue()));
                    used = true;
                }
                break;
            }
            case 5191001:
            case 5191002:
            case 5191003:
            case 5191004:
            case 5191000: { // Pet Flags
                final int uniqueid = (int) slea.readLong();
                MaplePet pet = c.getPlayer().getPet(0);
                int slo = 0;

                if (pet == null) {
                    break;
                }
                if (pet.getUniqueId() != uniqueid) {
                    pet = c.getPlayer().getPet(1);
                    slo = 1;
                    if (pet != null) {
                        if (pet.getUniqueId() != uniqueid) {
                            pet = c.getPlayer().getPet(2);
                            slo = 2;
                            if (pet != null) {
                                if (pet.getUniqueId() != uniqueid) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    } else {
                        break;
                    }
                }
                PetFlag zz = PetFlag.getByDelId(itemId);
                if (zz != null && zz.check(pet.getFlags())) {
                    pet.setFlags(pet.getFlags() - zz.getValue());
                    c.sendPacket(PetPacket.updatePet(pet, c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) pet.getInventoryPosition()), true));
                    c.sendPacket(CWvsContext.enableActions());
                    c.sendPacket(MTSCSPacket.changePetFlag(uniqueid, false, zz.getValue()));
                    used = true;
                }
                break;
            }
            case 5501001:
            case 5501002: { //expiry mount
                final Skill skil = SkillFactory.getSkill(slea.readInt());
                if (skil == null || skil.getId() / 10000 != 8000 || c.getPlayer().getSkillLevel(skil) <= 0 || !skil.isTimeLimited() || GameConstants.getMountItem(skil.getId(), c.getPlayer()) <= 0) {
                    break;
                }
                final long toAdd = (itemId == 5501001 ? 30 : 60) * 24 * 60 * 60 * 1000L;
                final long expire = c.getPlayer().getSkillExpiry(skil);
                if (expire < System.currentTimeMillis() || (long) (expire + toAdd) >= System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000L)) {
                    break;
                }
                c.getPlayer().changeSingleSkillLevel(skil, c.getPlayer().getSkillLevel(skil), c.getPlayer().getMasterLevel(skil), (long) (expire + toAdd));
                used = true;
                break;
            }
            case 5170000: { // Pet name change
                MaplePet pet = c.getPlayer().getPet(0);
                int slo = 0;

                if (pet == null) {
                    break;
                }
                String nName = slea.readMapleAsciiString();
                for (String z : GameConstants.RESERVED) {
                    if (pet.getName().contains(z) || nName.contains(z)) {
                        break;
                    }
                }
                if (MapleCharacterUtil.canChangePetName(nName)) {
                    pet.setName(nName);
                    c.sendPacket(PetPacket.updatePet(pet, c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) pet.getInventoryPosition()), true));
                    c.sendPacket(CWvsContext.enableActions());
                    c.getPlayer().getMap().broadcastMessage(MTSCSPacket.changePetName(c.getPlayer(), nName, slo));
                    used = true;
                }
                break;
            }
            case 5240000:
            case 5240001:
            case 5240002:
            case 5240003:
            case 5240004:
            case 5240005:
            case 5240006:
            case 5240007:
            case 5240008:
            case 5240009:
            case 5240010:
            case 5240011:
            case 5240012:
            case 5240013:
            case 5240014:
            case 5240015:
            case 5240016:
            case 5240017:
            case 5240018:
            case 5240019:
            case 5240020:
            case 5240021:
            case 5240022:
            case 5240023:
            case 5240024:
            case 5240025:
            case 5240026:
            case 5240027:
            case 5240029:
            case 5240030:
            case 5240031:
            case 5240032:
            case 5240033:
            case 5240034:
            case 5240035:
            case 5240036:
            case 5240037:
            case 5240038:
            case 5240039:
            case 5240040:
            case 5240028: { // Pet food
                MaplePet pet = c.getPlayer().getPet(0);

                if (pet == null) {
                    break;
                }
                if (!pet.canConsume(itemId)) {
                    pet = c.getPlayer().getPet(1);
                    if (pet != null) {
                        if (!pet.canConsume(itemId)) {
                            pet = c.getPlayer().getPet(2);
                            if (pet != null) {
                                if (!pet.canConsume(itemId)) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    } else {
                        break;
                    }
                }
                final byte petindex = c.getPlayer().getPetIndex(pet);
                pet.setFullness(100);
                if (pet.getCloseness() < 30000) {
                    if (pet.getCloseness() + (100 * c.getChannelServer().getTraitRate()) > 30000) {
                        pet.setCloseness(30000);
                    } else {
                        pet.setCloseness(pet.getCloseness() + (100 * c.getChannelServer().getTraitRate()));
                    }
                    if (pet.getCloseness() >= GameConstants.getClosenessNeededForLevel(pet.getLevel() + 1)) {
                        pet.setLevel(pet.getLevel() + 1);
                        c.sendPacket(EffectPacket.showOwnPetLevelUp(c.getPlayer().getPetIndex(pet)));
                        c.getPlayer().getMap().broadcastMessage(PetPacket.showPetLevelUp(c.getPlayer(), petindex));
                    }
                }
                c.sendPacket(PetPacket.updatePet(pet, c.getPlayer().getInventory(MapleInventoryType.CASH).getItem(pet.getInventoryPosition()), true));
                c.getPlayer().getMap().broadcastMessage(c.getPlayer(), PetPacket.commandResponse(c.getPlayer().getId(), (byte) 1, petindex, true, true), true);
                used = true;
                break;
            }
            case 5230001:
            case 5230000: {// owl of minerva
                final int itemSearch = slea.readInt();
                final List<HiredMerchant> hms = c.getChannelServer().searchMerchant(itemSearch);
                if (hms.size() > 0) {
                    c.sendPacket(CWvsContext.getOwlSearched(itemSearch, hms));
                    used = true;
                } else {
                    c.getPlayer().dropMessage(1, "找不到物品。");
                }
                break;
            }
            case 5281001: //idk, but probably
            case 5280001: // Gas Skill
            case 5281000: { // Passed gas
                Rectangle bounds = new Rectangle((int) c.getPlayer().getPosition().getX(), (int) c.getPlayer().getPosition().getY(), 1, 1);
                MapleMist mist = new MapleMist(bounds, c.getPlayer());
                c.getPlayer().getMap().spawnMist(mist, 10000, true);
                c.sendPacket(CWvsContext.enableActions());
                used = true;
                break;
            }
            case 5320000: {
                String name = slea.readMapleAsciiString();
                String otherName = slea.readMapleAsciiString();
                long unk = slea.readInt();
                long unk_2 = slea.readInt();
                int cardId = slea.readByte();
                short unk_3 = slea.readShort();
                byte unk_4 = slea.readByte();
                // int comm = slea.readByte();
                int comm = Randomizer.rand(1, 6);
                PredictCardFactory pcf = PredictCardFactory.getInstance();
                PredictCardFactory.PredictCard Card = pcf.getPredictCard(cardId);
                PredictCardFactory.PredictCardComment Comment = pcf.getPredictCardComment(comm);
                if ((Card == null) || (Comment == null)) {
                    break;
                }

                int love = Randomizer.rand(1, Comment.score) + 5;
                c.getPlayer().getMap().broadcastMessage(CField.startMapEffect("", 0, false)); // 清除當前地圖速配結果
                c.sendPacket(MTSCSPacket.showPredictCard(name, otherName, love, cardId, Comment.effectType));
                c.getPlayer().getMap().broadcastMessage(CField.startMapEffect(name + "和" + otherName + "的速配程度 " + Comment.worldmsg0, 5120009, true));
                used = true;
                break;
            }
            case 5370001:
            case 5370000: { // Chalkboard
                String text = slea.readMapleAsciiString();
                for (MapleEventType t : MapleEventType.values()) {
                    final MapleEvent e = ChannelServer.getInstance(c.getChannel()).getEvent(t);
                    if (e.isRunning()) {
                        for (int i : e.getType().mapids) {
                            if (c.getPlayer().getMapId() == i) {
                                c.getPlayer().dropMessage(5, "此處無法使用.");
                                c.sendPacket(CWvsContext.enableActions());
                                return;
                            }
                        }
                    }
                }
                if (ServerConstants.LOG_CHALKBOARD) {
                    FileoutputUtil.logToFile("紀錄/監聽/黑板.txt", "\r\n" + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 『" + c.getPlayer().getName() + "』 地圖『" + c.getPlayer().getMapId() + "』：  " + text);
                }
                if (!c.getPlayer().getCanTalk()) {
                    text = ServerConstants.RandomSorry();
                }
                c.getPlayer().setChalkboard(text);
                break;
            }
            case 5079000:
            case 5079001:
            case 5390007:
            case 5390008:
            case 5390009:
            case 5390000: // Diablo Messenger
            case 5390001: // Cloud 9 Messenger
            case 5390002: // Loveholic Messenger
            case 5390003: // New Year Megassenger 1
            case 5390004: // New Year Megassenger 2
            case 5390005: // Cute Tiger Messenger
            case 5390006: { // Tiger Roar's Messenger
//                if (c.getPlayer().getLevel() < 10) {
//                    c.getPlayer().dropMessage(5, "Must be level 10 or higher.");
//                    break;
//                }
//                if (c.getPlayer().getMapId() == GameConstants.JAIL) {
//                    c.getPlayer().dropMessage(5, "Cannot be used here.");
//                    break;
//                }
//                if (!c.getPlayer().getCheatTracker().canAvatarSmega()) {
//                    c.getPlayer().dropMessage(5, "You may only use this every 5 minutes.");
//                    break;
//                }
                if (!c.getChannelServer().getMegaphoneMuteState()) {
                    final List<String> lines = new LinkedList<>();
                    String text = slea.readMapleAsciiString();
                    if (text.getBytes().length > 55) {
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        text = ServerConstants.RandomSorry();
                    }
                    lines.add(text);
                    final boolean ear = slea.readByte() != 0;
                    World.Broadcast.broadcastSmega(CWvsContext.getAvatarMega(c.getPlayer(), c.getChannel(), itemId, lines, ear));
                    used = true;
                    if (ServerConstants.LOG_MEGA) {
                        FileoutputUtil.logToFile("紀錄/監聽/廣播頻道.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 玩家『" + c.getPlayer().getName() + "』頻道『" + c.getChannel() + "』廣播道具『" + itemId + "』：" + text);
                    }
                } else {
                    c.getPlayer().dropMessage(5, "喇叭系統關閉中。");
                }
                break;
            }
            case 5452001:
            case 5450003:
            case 5450000: { // Mu Mu the Travelling Merchant
                for (int i : GameConstants.blockedMaps) {
                    if (c.getPlayer().getMapId() == i) {
                        c.getPlayer().dropMessage(5, "You may not use this command here.");
                        c.sendPacket(CWvsContext.enableActions());
                        return;
                    }
                }
                if (c.getPlayer().getLevel() < 10) {
                    c.getPlayer().dropMessage(5, "You must be over level 10 to use this command.");
                } else if (c.getPlayer().hasBlockedInventory() || c.getPlayer().getMap().getSquadByMap() != null || c.getPlayer().getEventInstance() != null || c.getPlayer().getMap().getEMByMap() != null || c.getPlayer().getMapId() >= 990000000) {
                    c.getPlayer().dropMessage(5, "You may not use this command here.");
                } else if ((c.getPlayer().getMapId() >= 680000210 && c.getPlayer().getMapId() <= 680000502) || (c.getPlayer().getMapId() / 1000 == 980000 && c.getPlayer().getMapId() != 980000000) || (c.getPlayer().getMapId() / 100 == 1030008) || (c.getPlayer().getMapId() / 100 == 922010) || (c.getPlayer().getMapId() / 10 == 13003000)) {
                    c.getPlayer().dropMessage(5, "You may not use this command here.");
                } else {
                    MapleShopFactory.getInstance().getShop(61).sendShop(c);
                }
                //used = true;
                break;
            }
            case 5300000:
            case 5300001:
            case 5300002: { // Cash morphs
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                ii.getItemEffect(itemId).applyTo(c.getPlayer());
                used = true;
                break;
            }
            default:
                if (itemId / 10000 == 512) {
                    final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                    String msg = ii.getMsg(itemId);
                    String ourMsg = slea.readMapleAsciiString();
                    if (!msg.contains("%s")) {
                        msg = ourMsg;
                    } else {
                        msg = msg.replaceFirst("%s", ourMsg);
                        if (!msg.contains("%s")) {
                            msg = ii.getMsg(itemId).replaceFirst("%s", ourMsg);
                        } else {
                            try {
                                msg = msg.replaceFirst("%s", ourMsg);
                            } catch (Exception e) {
                                msg = ii.getMsg(itemId).replaceFirst("%s", ourMsg);
                            }
                        }
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        ourMsg = ServerConstants.RandomSorry();
                    }
                    c.getPlayer().getMap().startMapEffect(ourMsg, itemId);

                    final int buff = ii.getStateChangeItem(itemId);
                    if (buff != 0) {
                        for (MapleCharacter mChar : c.getPlayer().getMap().getCharactersThreadsafe()) {
                            ii.getItemEffect(buff).applyTo(mChar);
                        }
                    }
                    used = true;
                } else if (itemId / 10000 == 508) { // 風箏 by Kodan
                    String msg = slea.readMapleAsciiString();
                    if (!c.getPlayer().getCanTalk()) {
                        msg = ServerConstants.RandomSorry();
                    }
                    MapleKite Kite = new MapleKite(c.getPlayer(), c.getPlayer().getPosition(), c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition()).getId(), msg, itemId);
                    c.getPlayer().getMap().spawnKite(Kite);
                    used = true;
                } else if (itemId / 10000 == 510) {
                    c.getPlayer().getMap().startJukebox(c.getPlayer().getName(), itemId);
                    used = true;
                } else if (itemId / 10000 == 520) {
                    final int mesars = MapleItemInformationProvider.getInstance().getMeso(itemId);
                    if (mesars > 0 && c.getPlayer().getMeso() < (Integer.MAX_VALUE - mesars)) {
                        used = true;
                        if (Math.random() > 0.1) {
                            final int gainmes = Randomizer.nextInt(mesars);
                            c.getPlayer().gainMeso(gainmes, false);
                            c.sendPacket(MTSCSPacket.sendMesobagSuccess(gainmes));
                        } else {
                            c.sendPacket(MTSCSPacket.sendMesobagFailed(false)); // not random
                        }
                    }
                } else if (itemId / 10000 == 562) {
                    if (UseSkillBook(slot, itemId, c, c.getPlayer())) {
                        c.getPlayer().gainSP(1);
                    } //this should handle removing
                } else if (itemId / 10000 == 553) {
                    UseRewardItem(slot, itemId, c, c.getPlayer());// this too
                } else if (itemId / 10000 != 519) {
                    System.out.println("Unhandled CS item : " + itemId);
                    System.out.println(slea.toString(true));
                }
                break;
        }

        if (used) {
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, slot, (short) 1, false, true, false);
        }
        c.sendPacket(CWvsContext.enableActions());
        if (cc) {
            if (!c.getPlayer().isAlive() || c.getPlayer().getEventInstance() != null || FieldLimitType.ChannelSwitch.check(c.getPlayer().getMap().getFieldLimit())) {
                c.getPlayer().dropMessage(1, "Auto relog failed.");
                return;
            }
            c.getPlayer().dropMessage(5, "Auto relogging. Please wait.");
            c.getPlayer().fakeRelog();
            if (c.getPlayer().getScrolledPosition() != 0) {
//                c.sendPacket(CWvsContext.pamSongUI());
            }
        }
    }

    public static final void Pickup_Player(final LittleEndianAccessor slea, MapleClient c, final MapleCharacter chr) {
        if (c.getPlayer().hasBlockedInventory()) { //hack
            return;
        }
        chr.updateTick(slea.readInt());
        c.getPlayer().setScrolledPosition((short) 0);
        slea.skip(1); // or is this before tick?
        final Point Client_Reportedpos = slea.readPos();
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final MapleMapObject ob = chr.getMap().getMapObject(slea.readInt(), MapleMapObjectType.ITEM);

        if (ob == null) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final MapleMapItem mapitem = (MapleMapItem) ob;
        final Lock lock = mapitem.getLock();
        lock.lock();
        try {
            if (mapitem.isPickedUp()) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            if (mapitem.getQuest() > 0 && chr.getQuestStatus(mapitem.getQuest()) != 1) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            if (mapitem.getOwner() != chr.getId() && ((!mapitem.isPlayerDrop() && mapitem.getDropType() == 0) || (mapitem.isPlayerDrop() && chr.getMap().getEverlast()))) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            if (!mapitem.isPlayerDrop() && mapitem.getDropType() == 1 && mapitem.getOwner() != chr.getId() && (chr.getParty() == null || chr.getParty().getMemberById(mapitem.getOwner()) == null)) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            final double Distance = Client_Reportedpos.distanceSq(mapitem.getPosition());
            if (Distance > 5000 && (mapitem.getMeso() > 0 || mapitem.getItemId() != 4001025)) {
                chr.getCheatTracker().registerOffense(CheatingOffense.ITEMVAC_CLIENT, String.valueOf(Distance));
            } else if (chr.getPosition().distanceSq(mapitem.getPosition()) > 640000.0) {
                chr.getCheatTracker().registerOffense(CheatingOffense.ITEMVAC_SERVER);
            }
            if (mapitem.getMeso() > 0) {
                if (chr.getParty() != null && mapitem.getOwner() != chr.getId()) {
                    final List<MapleCharacter> toGive = new LinkedList<>();
                    final int splitMeso = mapitem.getMeso() * 40 / 100;
                    for (MaplePartyCharacter z : chr.getParty().getMembers()) {
                        MapleCharacter m = chr.getMap().getCharacterById(z.getId());
                        if (m != null && m.getId() != chr.getId()) {
                            toGive.add(m);
                        }
                    }
                    for (final MapleCharacter m : toGive) {
                        int mesos = splitMeso / toGive.size() + (m.getStat().hasPartyBonus ? (int) (mapitem.getMeso() / 20.0) : 0);
                        if (mapitem.getDropper() instanceof MapleMonster && m.getStat().incMesoProp > 0) {
                            mesos += Math.floor((m.getStat().incMesoProp * mesos) / 100.0f);
                        }
                        m.gainMeso(mesos, true);
                    }
                    int mesos = mapitem.getMeso() - splitMeso;
                    if (mapitem.getDropper() instanceof MapleMonster && chr.getStat().incMesoProp > 0) {
                        mesos += Math.floor((chr.getStat().incMesoProp * mesos) / 100.0f);
                    }
                    chr.gainMeso(mesos, true);
                } else {
                    int mesos = mapitem.getMeso();
                    if (mapitem.getDropper() instanceof MapleMonster && chr.getStat().incMesoProp > 0) {
                        mesos += Math.floor((chr.getStat().incMesoProp * mesos) / 100.0f);
                    }
                    chr.gainMeso(mesos, true);
                }
                removeItem(chr, mapitem, ob);
            } else if (MapleItemInformationProvider.getInstance().isPickupBlocked(mapitem.getItemId())) {
                c.sendPacket(CWvsContext.enableActions());
                c.getPlayer().dropMessage(5, "This item cannot be picked up.");
            } else if (c.getPlayer().inPVP() && Integer.parseInt(c.getPlayer().getEventInstance().getProperty("ice")) == c.getPlayer().getId()) {
                c.sendPacket(InventoryPacket.getInventoryFull());
                c.sendPacket(InventoryPacket.getShowInventoryFull());
                c.sendPacket(CWvsContext.enableActions());
            } else if (useItem(c, mapitem.getItemId())) {
                removeItem(c.getPlayer(), mapitem, ob);
                //another hack
                if (mapitem.getItemId() / 10000 == 291) {
                    c.getPlayer().getMap().broadcastMessage(CField.getCapturePosition(c.getPlayer().getMap()));
                    c.getPlayer().getMap().broadcastMessage(CField.resetCapture());
                }
            } else if (mapitem.getItemId() / 10000 != 291 && MapleInventoryManipulator.checkSpace(c, mapitem.getItemId(), mapitem.getItem().getQuantity(), mapitem.getItem().getOwner())) {
                if (mapitem.getItem().getQuantity() >= 50 && mapitem.getItemId() == 2340000) {
                    c.setMonitored(true); //hack check
                }
                MapleInventoryManipulator.addFromDrop(c, mapitem.getItem(), true, mapitem.getDropper() instanceof MapleMonster, false);
                removeItem(chr, mapitem, ob);
            } else {
                c.sendPacket(InventoryPacket.getInventoryFull());
                c.sendPacket(InventoryPacket.getShowInventoryFull());
                c.sendPacket(CWvsContext.enableActions());
            }
        } finally {
            lock.unlock();
        }
    }

    public static final void Pickup_Pet(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null) {
            return;
        }
        if (c.getPlayer().hasBlockedInventory() || c.getPlayer().inPVP()) { //hack
            return;
        }
        c.getPlayer().setScrolledPosition((short) 0);
        final byte petz = (byte) c.getPlayer().getPetIndex((int) slea.readLong());
        final MaplePet pet = chr.getPet(petz);
        slea.skip(1); // [4] Zero, [4] Seems to be tickcount, [1] Always zero
        chr.updateTick(slea.readInt());
        final Point Client_Reportedpos = slea.readPos();
        final MapleMapObject ob = chr.getMap().getMapObject(slea.readInt(), MapleMapObjectType.ITEM);

        if (ob == null || pet == null) {
            return;
        }
        final MapleMapItem mapitem = (MapleMapItem) ob;
        final Lock lock = mapitem.getLock();
        lock.lock();
        try {
            if (mapitem.isPickedUp()) {
                c.sendPacket(InventoryPacket.getInventoryFull());
                return;
            }
            if (mapitem.getOwner() != chr.getId() && mapitem.isPlayerDrop()) {
                return;
            }
            if (mapitem.getOwner() != chr.getId() && ((!mapitem.isPlayerDrop() && mapitem.getDropType() == 0) || (mapitem.isPlayerDrop() && chr.getMap().getEverlast()))) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            if (!mapitem.isPlayerDrop() && mapitem.getDropType() == 1 && mapitem.getOwner() != chr.getId() && (chr.getParty() == null || chr.getParty().getMemberById(mapitem.getOwner()) == null)) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            final double Distance = Client_Reportedpos.distanceSq(mapitem.getPosition());
            if (Distance > 10000 && (mapitem.getMeso() > 0 || mapitem.getItemId() != 4001025)) {
                chr.getCheatTracker().registerOffense(CheatingOffense.PET_ITEMVAC_CLIENT, String.valueOf(Distance));
            } else if (pet.getPos().distanceSq(mapitem.getPosition()) > 640000.0) {
                chr.getCheatTracker().registerOffense(CheatingOffense.PET_ITEMVAC_SERVER);

            }

            if (mapitem.getMeso() > 0) {
                if (chr.getParty() != null && mapitem.getOwner() != chr.getId()) {
                    final List<MapleCharacter> toGive = new LinkedList<>();
                    final int splitMeso = mapitem.getMeso() * 40 / 100;
                    for (MaplePartyCharacter z : chr.getParty().getMembers()) {
                        MapleCharacter m = chr.getMap().getCharacterById(z.getId());
                        if (m != null && m.getId() != chr.getId()) {
                            toGive.add(m);
                        }
                    }
                    for (final MapleCharacter m : toGive) {
                        m.gainMeso(splitMeso / toGive.size() + (m.getStat().hasPartyBonus ? (int) (mapitem.getMeso() / 20.0) : 0), true);
                    }
                    chr.gainMeso(mapitem.getMeso() - splitMeso, true);
                } else {
                    chr.gainMeso(mapitem.getMeso(), true);
                }
                removeItem_Pet(chr, mapitem, petz);
            } else if (MapleItemInformationProvider.getInstance().isPickupBlocked(mapitem.getItemId()) || mapitem.getItemId() / 10000 == 291) {
                c.sendPacket(CWvsContext.enableActions());
            } else if (useItem(c, mapitem.getItemId())) {
                removeItem_Pet(chr, mapitem, petz);
            } else if (MapleInventoryManipulator.checkSpace(c, mapitem.getItemId(), mapitem.getItem().getQuantity(), mapitem.getItem().getOwner())) {
                if (mapitem.getItem().getQuantity() >= 50 && mapitem.getItemId() == 2340000) {
                    c.setMonitored(true); //hack check
                }
                MapleInventoryManipulator.addFromDrop(c, mapitem.getItem(), true, mapitem.getDropper() instanceof MapleMonster, true);
                removeItem_Pet(chr, mapitem, petz);
            }
        } finally {
            lock.unlock();
        }
    }

    public static final boolean useItem(final MapleClient c, final int id) {
        if (GameConstants.isUse(id)) { // TO prevent caching of everything, waste of mem
            final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            final MapleStatEffect eff = ii.getItemEffect(id);
            if (eff == null) {
                return false;
            }
            //must hack here for ctf
            if (id / 10000 == 291) {
                boolean area = false;
                for (Rectangle rect : c.getPlayer().getMap().getAreas()) {
                    if (rect.contains(c.getPlayer().getTruePosition())) {
                        area = true;
                        break;
                    }
                }
                if (!c.getPlayer().inPVP() || (c.getPlayer().getTeam() == (id - 2910000) && area)) {
                    return false; //dont apply the consume
                }
            }
            final int consumeval = eff.getConsume();

            if (consumeval > 0) {
                consumeItem(c, eff);
                consumeItem(c, ii.getItemEffectEX(id));
                c.sendPacket(InfoPacket.getShowItemGain(id, (byte) 1));
                return true;
            }
        }
        return false;
    }

    public static final void consumeItem(final MapleClient c, final MapleStatEffect eff) {
        if (eff == null) {
            return;
        }
        if (eff.getConsume() == 2) {
            if (c.getPlayer().getParty() != null && c.getPlayer().isAlive()) {
                for (final MaplePartyCharacter pc : c.getPlayer().getParty().getMembers()) {
                    final MapleCharacter chr = c.getPlayer().getMap().getCharacterById(pc.getId());
                    if (chr != null && chr.isAlive()) {
                        eff.applyTo(chr);
                    }
                }
            } else {
                eff.applyTo(c.getPlayer());
            }
        } else if (c.getPlayer().isAlive()) {
            eff.applyTo(c.getPlayer());
        }
    }

    public static final void removeItem_Pet(final MapleCharacter chr, final MapleMapItem mapitem, int pet) {
        mapitem.setPickedUp(true);
        chr.getMap().broadcastMessage(CField.removeItemFromMap(mapitem.getObjectId(), 5, chr.getId(), pet));
        chr.getMap().removeMapObject(mapitem);
    }

    private static final void removeItem(final MapleCharacter chr, final MapleMapItem mapitem, final MapleMapObject ob) {
        mapitem.setPickedUp(true);
        chr.getMap().broadcastMessage(CField.removeItemFromMap(mapitem.getObjectId(), 2, chr.getId()), mapitem.getPosition());
        chr.getMap().removeMapObject(ob);
    }

    private static final void addMedalString(final MapleCharacter c, final StringBuilder sb) {
        final Item medal = c.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -46);
        if (medal != null) { // Medal
            sb.append("<");
            if (medal.getItemId() == 1142257 && GameConstants.isAdventurer(c.getJob())) {
                MapleQuestStatus stat = c.getQuestNoAdd(MapleQuest.getInstance(GameConstants.ULT_EXPLORER));
                if (stat != null && stat.getCustomData() != null) {
                    sb.append(stat.getCustomData());
                    sb.append("'s Successor");
                } else {
                    sb.append(MapleItemInformationProvider.getInstance().getName(medal.getItemId()));
                }
            } else {
                sb.append(MapleItemInformationProvider.getInstance().getName(medal.getItemId()));
            }
            sb.append("> ");
        }
    }

    private static final boolean getIncubatedItems(MapleClient c, int itemId) {
        if (c.getPlayer().getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() < 2 || c.getPlayer().getInventory(MapleInventoryType.USE).getNumFreeSlot() < 2 || c.getPlayer().getInventory(MapleInventoryType.SETUP).getNumFreeSlot() < 2) {
            c.getPlayer().dropMessage(5, "Please make room in your inventory.");
            return false;
        }
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        int id1 = RandomRewards.getPeanutReward(), id2 = RandomRewards.getPeanutReward();
        while (!ii.itemExists(id1)) {
            id1 = RandomRewards.getPeanutReward();
        }
        while (!ii.itemExists(id2)) {
            id2 = RandomRewards.getPeanutReward();
        }
        c.sendPacket(CWvsContext.getPeanutResult(id1, (short) 1, id2, (short) 1, itemId));
        MapleInventoryManipulator.addById(c, id1, (short) 1, ii.getName(itemId) + " on " + FileoutputUtil.CurrentReadable_Date());
        MapleInventoryManipulator.addById(c, id2, (short) 1, ii.getName(itemId) + " on " + FileoutputUtil.CurrentReadable_Date());
        return true;
    }

    public static final void OwlMinerva(final LittleEndianAccessor slea, final MapleClient c) {
        final byte slot = (byte) slea.readShort();
        final int itemid = slea.readInt();
        final Item toUse = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slot);
        if (toUse != null && toUse.getQuantity() > 0 && toUse.getItemId() == itemid && itemid == 2310000 && !c.getPlayer().hasBlockedInventory()) {
            final int itemSearch = slea.readInt();
            final List<HiredMerchant> hms = c.getChannelServer().searchMerchant(itemSearch);
            if (hms.size() > 0) {
                c.sendPacket(CWvsContext.getOwlSearched(itemSearch, hms));
                MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, itemid, 1, true, false);
            } else {
                c.getPlayer().dropMessage(1, "找不到該物品");
            }
        }
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void Owl(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().haveItem(5230000, 1, true, false) || c.getPlayer().haveItem(2310000, 1, true, false)) {
            if (c.getPlayer().getMapId() >= 910000000 && c.getPlayer().getMapId() <= 910000022) {
                c.sendPacket(CWvsContext.getOwlOpen());
            } else {
                c.getPlayer().dropMessage(5, "貓頭鷹只能在自由市場內使用");
                c.sendPacket(CWvsContext.enableActions());
            }
        }
    }
    public static final int OWL_ID = 2; //don't change. 0 = owner ID, 1 = store ID, 2 = object ID

    public static final void OwlWarp(final LittleEndianAccessor slea, final MapleClient c) {
        if (!c.getPlayer().isAlive()) {
            c.sendPacket(CWvsContext.getOwlMessage(4));
            return;
        } else if (c.getPlayer().getTrade() != null) {
            c.sendPacket(CWvsContext.getOwlMessage(7));
            return;
        }
        if (c.getPlayer().getMapId() >= 910000000 && c.getPlayer().getMapId() <= 910000022 && !c.getPlayer().hasBlockedInventory()) {
            final int id = slea.readInt();
            final int map = slea.readInt();
            if (map >= 910000001 && map <= 910000022) {
                c.sendPacket(CWvsContext.getOwlMessage(0));
                final MapleMap mapp = c.getChannelServer().getMapFactory().getMap(map);
                c.getPlayer().changeMap(mapp, mapp.getPortal(0));
                HiredMerchant merchant = null;
                List<MapleMapObject> objects;
                switch (OWL_ID) {
                    case 0:
                        objects = mapp.getAllHiredMerchantsThreadsafe();
                        for (MapleMapObject ob : objects) {
                            if (ob instanceof IMaplePlayerShop) {
                                final IMaplePlayerShop ips = (IMaplePlayerShop) ob;
                                if (ips instanceof HiredMerchant) {
                                    final HiredMerchant merch = (HiredMerchant) ips;
                                    if (merch.getOwnerId() == id) {
                                        merchant = merch;
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                    case 1:
                        objects = mapp.getAllHiredMerchantsThreadsafe();
                        for (MapleMapObject ob : objects) {
                            if (ob instanceof IMaplePlayerShop) {
                                final IMaplePlayerShop ips = (IMaplePlayerShop) ob;
                                if (ips instanceof HiredMerchant) {
                                    final HiredMerchant merch = (HiredMerchant) ips;
                                    if (merch.getStoreId() == id) {
                                        merchant = merch;
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                    default:
                        final MapleMapObject ob = mapp.getMapObject(id, MapleMapObjectType.HIRED_MERCHANT);
                        if (ob instanceof IMaplePlayerShop) {
                            final IMaplePlayerShop ips = (IMaplePlayerShop) ob;
                            if (ips instanceof HiredMerchant) {
                                merchant = (HiredMerchant) ips;
                            }
                        }
                        break;
                }
                if (merchant != null) {
                    if (merchant.isOwner(c.getPlayer())) {
                        merchant.setOpen(false);
                        merchant.removeAllVisitors((byte) 16, (byte) 0);
                        c.getPlayer().setPlayerShop(merchant);
                        c.sendPacket(PlayerShopPacket.getHiredMerch(c.getPlayer(), merchant, false));
                    } else if (!merchant.isOpen() || !merchant.isAvailable()) {
                        c.getPlayer().dropMessage(1, "商店主人正在整理商店物品\r\n請稍後再度光臨！");
                    } else if (merchant.getFreeSlot() == -1) {
                        c.getPlayer().dropMessage(1, "商店已達到最大人數\r\n請稍後再度光臨！");
                    } else if (merchant.isInBlackList(c.getPlayer().getName())) {
                        c.getPlayer().dropMessage(1, "你被禁止進入該商店。");
                    } else {
                        c.getPlayer().setPlayerShop(merchant);
                        merchant.addVisitor(c.getPlayer());
                        c.sendPacket(PlayerShopPacket.getHiredMerch(c.getPlayer(), merchant, false));
                    }
                } else {
                    c.getPlayer().dropMessage(1, "商店主人正在整理商店物品\r\n請稍後再度光臨！");
                }
            } else {
                c.sendPacket(CWvsContext.getOwlMessage(23));
            }
        } else {
            c.sendPacket(CWvsContext.getOwlMessage(23));
        }
    }

    public static final void PamSong(LittleEndianAccessor slea, MapleClient c) {
        final Item pam = c.getPlayer().getInventory(MapleInventoryType.CASH).findById(5640000);
        if (slea.readByte() > 0 && c.getPlayer().getScrolledPosition() != 0 && pam != null && pam.getQuantity() > 0) {
            final MapleInventoryType inv = c.getPlayer().getScrolledPosition() < 0 ? MapleInventoryType.EQUIPPED : MapleInventoryType.EQUIP;
            final Item item = c.getPlayer().getInventory(inv).getItem(c.getPlayer().getScrolledPosition());
            c.getPlayer().setScrolledPosition((short) 0);
            if (item != null) {
                final Equip eq = (Equip) item;
                eq.setUpgradeSlots((byte) (eq.getUpgradeSlots() + 1));
                c.getPlayer().forceReAddItem_Flag(eq, inv);
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.CASH, pam.getPosition(), (short) 1, true, false);
                c.getPlayer().getMap().broadcastMessage(CField.pamsSongEffect(c.getPlayer().getId()));
            }
        } else {
            c.getPlayer().setScrolledPosition((short) 0);
        }
    }

    public static final void TeleRock(LittleEndianAccessor slea, MapleClient c) {
        final byte slot = (byte) slea.readShort();
        final int itemId = slea.readInt();
        final Item toUse = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slot);

        if (toUse == null || toUse.getQuantity() < 1 || toUse.getItemId() != itemId || itemId / 10000 != 232 || c.getPlayer().hasBlockedInventory()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        boolean used = UseTeleRock(slea, c, itemId);
        if (used) {
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
        }
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final boolean UseTeleRock(LittleEndianAccessor slea, MapleClient c, int itemId) {
        boolean used = false;
        if (slea.readByte() == 0) { // Rocktype
            final MapleMap target = c.getChannelServer().getMapFactory().getMap(slea.readInt());
            if (target != null && ((itemId == 5041000 && c.getPlayer().isRockMap(target.getId())) || ((itemId == 5040000 || itemId == 5040001) && c.getPlayer().isRegRockMap(target.getId())) || ((itemId == 5040004 || itemId == 5041001) && (c.getPlayer().isHyperRockMap(target.getId()) || GameConstants.isHyperTeleMap(target.getId()))))) {
                if (!FieldLimitType.VipRock.check(c.getPlayer().getMap().getFieldLimit()) && !FieldLimitType.VipRock.check(target.getFieldLimit()) && !c.getPlayer().isInBlockedMap()) { //Makes sure this map doesn't have a forced return map
                    c.getPlayer().changeMap(target, target.getPortal(0));
                    used = true;
                } else {
                    c.getPlayer().dropMessage(1, "你不能移到這個地方");
                }
            } else {
                c.getPlayer().dropMessage(1, "你不能移到這個地方");
            }
        } else {
            final String name = slea.readMapleAsciiString();
            final MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(name);
            if (victim != null && !victim.isIntern() && c.getPlayer().getEventInstance() == null && victim.getEventInstance() == null) {
                if (!FieldLimitType.VipRock.check(c.getPlayer().getMap().getFieldLimit()) && !FieldLimitType.VipRock.check(c.getChannelServer().getMapFactory().getMap(victim.getMapId()).getFieldLimit()) && !victim.isInBlockedMap() && !c.getPlayer().isInBlockedMap()) {
                    if (itemId == 5041000 || itemId == 5040004 || itemId == 5041001 || (victim.getMapId() / 100000000) == (c.getPlayer().getMapId() / 100000000)) { // Viprock or same continent
                        c.getPlayer().changeMap(victim.getMap(), victim.getMap().findClosestPortal(victim.getTruePosition()));
                        used = true;
                    } else {
                        c.getPlayer().dropMessage(1, "你不能移到這個地方");
                    }
                } else {
                    c.getPlayer().dropMessage(1, "你不能移到這個地方.");
                }
            } else {
                c.getPlayer().dropMessage(1, "在此頻道未找到該玩家。");
            }
        }
        return used;
    }
}
