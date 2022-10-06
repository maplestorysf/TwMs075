package tools.packet;

import client.BuddylistEntry;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.MapleDisease;
import client.MapleQuestStatus;
import client.MapleStat;
import client.Skill;
import client.SkillEntry;
import client.inventory.Item;
import client.inventory.MapleInventoryType;
import client.inventory.MapleMount;
import client.inventory.MaplePet;
import constants.GameConstants;
import handling.SendPacketOpcode;
import handling.channel.MapleGuildRanking.GuildRankingInfo;
import handling.channel.handler.InventoryHandler;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.World;
import handling.world.guild.MapleBBSThread;
import handling.world.guild.MapleBBSThread.MapleBBSReply;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildCharacter;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import server.MapleStatEffect;
import server.life.PlayerNPC;
import server.shops.HiredMerchant;
import server.shops.MaplePlayerShopItem;
import tools.StringUtil;
import tools.data.MaplePacketLittleEndianWriter;

/**
 *
 * @author AlphaEta
 */
public class CWvsContext {

    //<editor-fold defaultstate="collapsed" desc="InventoryPacket">
    public static class InventoryPacket {

        public static byte[] addInventorySlot(final MapleInventoryType type, final Item item) {
            return addInventorySlot(type, item, false);
        }

        public static byte[] addInventorySlot(final MapleInventoryType type, final Item item, final boolean fromDrop) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(fromDrop ? 1 : 0); // update tick
            mplew.write(1); // how many items to add
            mplew.write(0); // used for remove case only. related to 2230000 (EXP Item), if its a 0, function executed.
            mplew.write(type.getType());
            mplew.writeShort(item.getPosition());
            PacketHelper.addItemInfo(mplew, item);
            //mplew.write(0); // only needed here when size is <= 1

            return mplew.getPacket();
        }

        public static byte[] updateInventorySlot(final MapleInventoryType type, final Item item, final boolean fromDrop) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(fromDrop ? 1 : 0);
            mplew.write(1); //how many items to update
            mplew.write(1);
            mplew.write(type.getType()); // iv type
            mplew.writeShort(item.getPosition()); // slot id
            mplew.writeShort(item.getQuantity());

            return mplew.getPacket();
        }

        public static byte[] moveInventoryItem(final MapleInventoryType type, final short src, final short dst, final boolean bag, final boolean bothBag) {
            return moveInventoryItem(type, src, dst, (byte) -1, bag, bothBag);
        }

        public static byte[] moveInventoryItem(final MapleInventoryType type, final short src, final short dst, final short equipIndicator, final boolean bag, final boolean bothBag) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1);
            mplew.write(1); //how many items to update
            mplew.write(2);
            mplew.write(type.getType());
            mplew.writeShort(src);
            mplew.writeShort(dst);
            if (equipIndicator != -1) {
                mplew.write(equipIndicator);
            }

            return mplew.getPacket();
        }

        public static byte[] moveAndMergeInventoryItem(final MapleInventoryType type, final short src, final short dst, final short total, final boolean bag, final boolean switchSrcDst, final boolean bothBag) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1);
            mplew.write(2); //how many items to update
            mplew.write(3);
            mplew.write(type.getType());
            mplew.writeShort(src);
            mplew.write(1); // merge mode?
            mplew.write(type.getType());
            mplew.writeShort(dst);
            mplew.writeShort(total);

            return mplew.getPacket();
        }

        public static byte[] moveAndMergeWithRestInventoryItem(final MapleInventoryType type, final short src, final short dst, final short srcQ, final short dstQ, final boolean bag, final boolean switchSrcDst, final boolean bothBag) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1);
            mplew.write(2); //how many items to update
            mplew.write(1);
            mplew.write(type.getType());
            mplew.writeShort(src);
            mplew.writeShort(srcQ);
            mplew.write(1);
            mplew.write(type.getType());
            mplew.writeShort(dst);
            mplew.writeShort(dstQ);

            return mplew.getPacket();
        }

        public static byte[] clearInventoryItem(final MapleInventoryType type, final short slot, final boolean fromDrop) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(fromDrop ? 1 : 0);
            mplew.write(1);
            mplew.write(3); //bag
            mplew.write(type.getType());
            mplew.writeShort(slot);

            return mplew.getPacket();
        }

        public static byte[] updateSpecialItemUse(final Item item, final byte invType, final MapleCharacter chr) {
            return updateSpecialItemUse(item, invType, item.getPosition(), false, chr);
        }

        public static byte[] updateSpecialItemUse(final Item item, final byte invType, final short pos, final boolean theShort, final MapleCharacter chr) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(0);
            mplew.write(2); // always 2
            mplew.write(0);

            //clears the slot and puts item in same slot in one packet
            mplew.write(invType); // Inventory type
            mplew.writeShort(pos); // item slot

            mplew.write(0);
            mplew.write(invType);
            if (item.getType() == 1 || theShort) {
                mplew.writeShort(pos);
            } else {
                mplew.write(pos);
            }
            PacketHelper.addItemInfo(mplew, item, chr);
            if (pos < 0) {
                mplew.write(2);
            }

            return mplew.getPacket();
        }

        public static byte[] updateSpecialItemUse_(final Item item, final byte invType, final MapleCharacter chr) {
            return updateSpecialItemUse_(item, invType, item.getPosition(), chr);
        }

        public static byte[] updateSpecialItemUse_(final Item item, final byte invType, final short pos, final MapleCharacter chr) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(0); // could be from drop
            mplew.write(1);
            mplew.write(0); // quantity > 0 (?)
            mplew.write(invType); // Inventory type
            if (item.getType() == 1) {
                mplew.writeShort(pos);
            } else {
                mplew.write(pos);
            }
            PacketHelper.addItemInfo(mplew, item, chr);
            if (pos < 0) {
                mplew.write(1);
            }

            return mplew.getPacket();
        }

        public static byte[] scrolledItem(final Item scroll, final Item item, final boolean destroyed, final boolean potential) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1); // fromdrop always true
            mplew.write(destroyed ? 2 : 3);
            mplew.write(scroll.getQuantity() > 0 ? 1 : 3);
            mplew.write(GameConstants.getInventoryType(scroll.getItemId()).getType()); //can be cash
            mplew.writeShort(scroll.getPosition());
            if (scroll.getQuantity() > 0) {
                mplew.writeShort(scroll.getQuantity());
            }
            mplew.write(3);
            mplew.write(MapleInventoryType.EQUIP.getType());
            mplew.writeShort(item.getPosition());
            if (!destroyed) {
                mplew.write(0);
                mplew.write(MapleInventoryType.EQUIP.getType());
                mplew.writeShort(item.getPosition());
                PacketHelper.addItemInfo(mplew, item);
            }
            mplew.write(1);

            return mplew.getPacket();
        }

        public static byte[] moveAndUpgradeItem(final MapleInventoryType type, final Item item, final short oldpos, final short newpos, final MapleCharacter chr) {//equipping some items
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1);
            mplew.write(3);
            mplew.write(0);

            mplew.write(type.getType());
            mplew.writeShort(oldpos);

            mplew.write(0);
            mplew.write(1);
            mplew.writeShort(oldpos);
            PacketHelper.addItemInfo(mplew, item, chr);

            mplew.write(2);
            mplew.write(type.getType());
            mplew.writeShort(oldpos);//oldslot
            mplew.writeShort(newpos);//new slot
            mplew.write(0);

            return mplew.getPacket();
        }

        public static byte[] dropInventoryItem(final MapleInventoryType type, final short src) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1);
            mplew.write(1); //how many items to update
            mplew.write(3);
            mplew.write(type.getType());
            mplew.writeShort(src);
            if (src < 0) {
                mplew.write(1);
            }

            return mplew.getPacket();
        }

        public static byte[] dropInventoryItemUpdate(final MapleInventoryType type, final Item item) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1);
            mplew.write(1); //how many items to update
            mplew.write(1);
            mplew.write(type.getType());
            mplew.writeShort(item.getPosition());
            mplew.writeShort(item.getQuantity());

            return mplew.getPacket();
        }

        public static byte[] getInventoryFull() {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(1);
            mplew.write(0);

            return mplew.getPacket();
        }

        public static byte[] getInventoryStatus() { // EnableActions
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_OPERATION.getValue());
            mplew.write(0);
            mplew.write(0);
            mplew.write(0);

            return mplew.getPacket();
        }

        public static byte[] getSlotUpdate(final byte invType, final byte newSlots) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.INVENTORY_GROW.getValue());
            mplew.write(invType);
            mplew.write(newSlots);

            return mplew.getPacket();
        }

        public static byte[] getShowInventoryFull() {
            return InfoPacket.getShowInventoryStatus(0xFF);
        }

        public static byte[] showItemUnavailable() {
            return InfoPacket.getShowInventoryStatus(0xFE);
        }
    }
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="BuffPacket">

    public static class BuffPacket {

        public static byte[] giveDice(int buffid, int skillid, int duration, Map<MapleBuffStat, Integer> statups) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            PacketHelper.writeBuffMask(mplew, statups);
            mplew.writeShort(Math.max(buffid / 100, Math.max(buffid / 10, buffid % 10))); // 1-6
            mplew.writeInt(skillid); // skillid
            mplew.writeInt(duration);
            mplew.writeShort(0);
            mplew.write(0);
            mplew.writeInt(GameConstants.getDiceStat(buffid, 3));
            mplew.writeInt(GameConstants.getDiceStat(buffid, 3));
            mplew.writeInt(GameConstants.getDiceStat(buffid, 4));
            mplew.writeZeroBytes(20); //idk
            mplew.writeInt(GameConstants.getDiceStat(buffid, 2));
            mplew.writeZeroBytes(12); //idk
            mplew.writeInt(GameConstants.getDiceStat(buffid, 5));
            mplew.writeZeroBytes(16); //idk
            mplew.writeInt(GameConstants.getDiceStat(buffid, 6));
            mplew.writeZeroBytes(16);
            mplew.write(1);
            mplew.write(4); // Total buffed times

            return mplew.getPacket();
        }

        public static byte[] giveHoming(int skillid, int mobid, int x) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            PacketHelper.writeSingleMask(mplew, MapleBuffStat.HOMING_BEACON);
            mplew.write(0);
            mplew.write(0);
            mplew.writeInt(1);
            mplew.writeInt(skillid);
            mplew.writeInt(0);
            mplew.writeInt(mobid);
            mplew.writeShort(0);

            return mplew.getPacket();
        }

        public static byte[] giveMount(int buffid, int skillid, Map<MapleBuffStat, Integer> statups) {
            return showMonsterRiding(-1, statups, buffid, skillid);
        }

        public static byte[] showMonsterRiding(int cid, Map<MapleBuffStat, Integer> statups, int buffid, int skillId) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            if (cid == -1) {
                mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            } else {
                mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
                mplew.writeInt(cid);
            }
            PacketHelper.writeBuffMask(mplew, statups);
            mplew.writeShort(0);
            mplew.writeInt(buffid);
            mplew.writeInt(skillId);
            mplew.writeInt(0);
            mplew.writeShort(0);
            mplew.write(0);
            mplew.write(0);

            return mplew.getPacket();
        }

        public static byte[] givePirate(Map<MapleBuffStat, Integer> statups, int duration, int skillid) {
            return giveForeignPirate(statups, duration, -1, skillid);
        }

        public static byte[] giveForeignPirate(Map<MapleBuffStat, Integer> statups, int duration, int cid, int skillid) {
            final boolean infusion = skillid == 5121009 || skillid == 15111005 || (skillid % 10000 == 8006);
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            if (cid == -1) {
                mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            } else {
                mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
                mplew.writeInt(cid);
            }
            PacketHelper.writeBuffMask(mplew, statups);
            mplew.writeShort(0);
            for (Integer stat : statups.values()) {
                mplew.writeInt(stat);
                mplew.writeInt(skillid);
                mplew.writeInt(0);
//                mplew.writeZeroBytes(infusion ? 6 : 1);
                mplew.writeShort(duration); //duration... seconds
            }
            mplew.writeShort(0);
            mplew.write(2);

            return mplew.getPacket();
        }

        public static byte[] giveArcane(Map<Integer, Integer> statups, int duration) { //monster oid, %damage increase
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            PacketHelper.writeSingleMask(mplew, MapleBuffStat.ARCANE_AIM);
            mplew.writeShort(0);
            mplew.write(0);
            mplew.writeInt(statups.size());
            for (Entry<Integer, Integer> stat : statups.entrySet()) {
                mplew.writeInt(stat.getKey());
                mplew.writeLong(stat.getValue());
                mplew.writeInt(duration);
            }
            mplew.writeShort(0);
            mplew.writeShort(0);
            mplew.write(1);
            mplew.write(1);

            return mplew.getPacket();
        }

        public static byte[] giveEnergyChargeTest(int bar, int bufflength) {
            return giveEnergyChargeTest(-1, bar, bufflength);
        }

        public static byte[] giveEnergyChargeTest(int cid, int bar, int bufflength) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            if (cid == -1) {
                mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            } else {
                mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
                mplew.writeInt(cid);
            }
            PacketHelper.writeSingleMask(mplew, MapleBuffStat.ENERGY_CHARGE);
            mplew.writeShort(0);
            mplew.write(0);
            mplew.writeInt(Math.min(bar, 10000)); // 0 = no bar, 10000 = full bar
            mplew.writeLong(0); //skillid, but its 0 here
            mplew.write(0);
            mplew.writeInt(bar >= 10000 ? bufflength : 0);//short - bufflength...50

            return mplew.getPacket();
        }

        public static byte[] giveBuff(int buffid, int bufflength, Map<MapleBuffStat, Integer> statups, MapleStatEffect effect) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            PacketHelper.writeBuffMask(mplew, statups);
            for (Entry<MapleBuffStat, Integer> stat : statups.entrySet()) {
                mplew.writeShort(stat.getValue());
                mplew.writeInt(buffid);
                mplew.writeInt(bufflength);
            }
            mplew.write(0);
            mplew.write(0);
            mplew.writeShort(0);
            mplew.write(0);

            return mplew.getPacket();
        }

        public static byte[] giveDebuff(MapleDisease statups, int x, int skillid, int level, int duration) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
            PacketHelper.writeSingleMask(mplew, statups);
            mplew.writeShort(x);
            mplew.writeShort(skillid);
            mplew.writeShort(level);
            mplew.writeInt(duration);
            mplew.writeShort(0); // ??? wk charges have 600 here o.o
            mplew.writeShort(900); //Delay
            mplew.write(1);

            return mplew.getPacket();
        }

        public static byte[] cancelBuff(List<MapleBuffStat> statups) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
            PacketHelper.writeMask(mplew, statups);
            for (MapleBuffStat z : statups) {
                if (z.canStack()) {
                    mplew.writeInt(0); //amount of buffs still in the stack? dunno mans
                }
            }
            mplew.write(3);

            return mplew.getPacket();
        }

        public static byte[] cancelDebuff(MapleDisease mask) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
            PacketHelper.writeSingleMask(mplew, mask);
            mplew.write(3);

            return mplew.getPacket();
        }

        public static byte[] cancelHoming() {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
            PacketHelper.writeSingleMask(mplew, MapleBuffStat.HOMING_BEACON);

            return mplew.getPacket();
        }

        public static byte[] giveForeignBuff(int cid, Map<MapleBuffStat, Integer> statups, MapleStatEffect effect) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
            mplew.writeInt(cid);
            PacketHelper.writeBuffMask(mplew, statups);
            for (Entry<MapleBuffStat, Integer> statup : statups.entrySet()) {
                if (effect.isMorph() && !effect.isPirateMorph()) {
                    mplew.write(statup.getValue());
                } else {
                    mplew.writeShort(statup.getValue());
                }
            }
            mplew.write(0);
            mplew.write(0);
            mplew.writeShort(0); // same as give_buff
            if (effect.isMorph()) {
                mplew.writeShort(0);
            }
            mplew.write(0);
            // 暫時防止玩家 error 38
            mplew.writeZeroBytes(10);

            return mplew.getPacket();
        }

        public static byte[] giveForeignDebuff(int cid, final MapleDisease statups, int skillid, int level, int x) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
            mplew.writeInt(cid);
            PacketHelper.writeSingleMask(mplew, statups);
            if (skillid == 125) {
                mplew.writeShort(0);
                mplew.write(0); //todo test
            }
            //mplew.writeShort(x);
            mplew.writeShort(skillid);
            mplew.writeShort(level);
            mplew.writeShort(0); // same as give_buff
            mplew.writeShort(900); //Delay
            //mplew.write(1);
            //mplew.write(1);

            return mplew.getPacket();
        }

        public static byte[] cancelForeignBuff(int cid, List<MapleBuffStat> statups) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
            mplew.writeInt(cid);
            PacketHelper.writeMask(mplew, statups);
            mplew.write(3);
            mplew.write(1);

            return mplew.getPacket();
        }

        public static byte[] cancelForeignDebuff(int cid, MapleDisease mask) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
            mplew.writeInt(cid);
            PacketHelper.writeSingleMask(mplew, mask);
            mplew.write(3);
            mplew.write(1);

            return mplew.getPacket();
        }
    }
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="InfoPacket">

    public static class InfoPacket {

        public static byte[] showMesoGain(final int gain, final boolean inChat) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            if (!inChat) {
                mplew.write(0);
                mplew.write(1);
                mplew.writeInt(gain);
                mplew.writeShort(0); // Internet Cafe Meso Bonus
            } else {
                mplew.write(5);
                mplew.writeInt(gain);
                mplew.writeShort(0);
            }

            return mplew.getPacket();
        }

        public static byte[] getShowInventoryStatus(int mode) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(0);
            mplew.write(mode);
            mplew.writeInt(0);
            mplew.writeInt(0);

            return mplew.getPacket();
        }

        public static byte[] getShowItemGain(int itemId, short quantity) {
            return getShowItemGain(itemId, quantity, false);
        }

        public static byte[] getShowItemGain(int itemId, short quantity, boolean inChat) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            if (inChat) {
                mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
                mplew.write(3);
                mplew.write(1); // item count // if this is 0, then after quantity extra 1 string
                mplew.writeInt(itemId);
                mplew.writeInt(quantity);
            } else {
                mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
                mplew.writeShort(0);
                mplew.writeInt(itemId);
                mplew.writeInt(quantity);
            }

            return mplew.getPacket();
        }

        public static byte[] updateQuest(final MapleQuestStatus quest) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(1);
            mplew.writeShort(quest.getQuest().getId());
            mplew.write(quest.getStatus());
            switch (quest.getStatus()) {
                case 0:
                    mplew.write(0);
                    break;
                case 1:
                    mplew.writeMapleAsciiString(quest.getCustomData() != null ? quest.getCustomData() : "");
                    break;
                case 2:
                    mplew.writeLong(PacketHelper.getTime(System.currentTimeMillis()));
                    break;
            }

            return mplew.getPacket();
        }

        public static byte[] updateQuestMobKills(final MapleQuestStatus status) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(1);
            mplew.writeShort(status.getQuest().getId());
            mplew.write(1);
            final StringBuilder sb = new StringBuilder();
            for (final int kills : status.getMobKills().values()) {
                sb.append(StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3));
            }
            mplew.writeMapleAsciiString(sb.toString());
            mplew.writeLong(0);

            return mplew.getPacket();
        }

        public static byte[] itemExpired(int itemid) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(2);
            mplew.writeInt(itemid);

            return mplew.getPacket();
        }

        public static byte[] GainEXP_Monster(final int gain, final boolean white, final int partyinc, final int Class_Bonus_EXP, final int Equipment_Bonus_EXP, final int Premium_Bonus_EXP) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(3); // 3 = exp, 4 = fame, 5 = mesos, 6 = guildpoints
            mplew.write(white ? 1 : 0);
            mplew.writeInt(gain);
            mplew.write(0); // Not in chat
            mplew.writeInt(0); // 活動組隊獎勵經驗值 (+%d) x%d
            mplew.write(0);
            mplew.write(0); // (A bonus EXP %d%% is awarded for every 3rd monster defeated.)
            mplew.writeInt(0); // 結婚紅利經驗值(+%d)
            mplew.write(0); // Party EXP bonus. -1 for default +30, or value / 100.0 = result

            return mplew.getPacket();
        }

        public static byte[] GainEXP_Others(final int gain, final boolean inChat, final boolean white) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(3); // 3 = exp, 4 = fame, 5 = mesos, 6 = guildpoints
            mplew.write(white ? 1 : 0);
            mplew.writeInt(gain);
            mplew.write(inChat ? 1 : 0);
            mplew.writeInt(0);
            mplew.write(0);
            mplew.write(0);
            mplew.writeInt(0);
            if (inChat) {
                mplew.write(0);
            }
            mplew.write(0);

            return mplew.getPacket();
        }

        public static byte[] getSPMsg(byte sp, short job) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(4);
            mplew.writeShort(job);
            mplew.write(sp);

            return mplew.getPacket();
        }

        public static byte[] getShowFameGain(final int gain) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(4);
            mplew.writeInt(gain);

            return mplew.getPacket();
        }

        public static byte[] getGPMsg(int itemid) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(6);
            mplew.writeInt(itemid);

            return mplew.getPacket();
        }

        public static byte[] getGPContribution(int itemid) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(7);
            mplew.writeInt(itemid);

            return mplew.getPacket();
        }

        public static byte[] getStatusMsg(int itemid) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(7);
            mplew.writeInt(itemid);

            return mplew.getPacket();
        }

        public static byte[] updateInfoQuest(final int quest, final String data) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(11);
            mplew.writeShort(quest);
            mplew.writeMapleAsciiString(data);

            return mplew.getPacket();
        }

        public static byte[] showItemReplaceMessage(final List<String> message) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(13);
            mplew.write(message.size());
            for (final String x : message) {
                mplew.writeMapleAsciiString(x);
            }

            return mplew.getPacket();
        }

        public static byte[] getBPMsg(int amount) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(21);
            mplew.writeInt(amount); // Battle Points
            mplew.writeInt(0); // Battle EXP

            return mplew.getPacket();
        }

        public static byte[] showExpireMessage(final byte type, final List<Integer> item) {
            final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(4 + (item.size() * 4));

            // normal = 10, seal = 13, skill = 15;
            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(type);
            mplew.write(item.size());
            for (final Integer it : item) {
                mplew.writeInt(it);
            }

            return mplew.getPacket();
        }

        public static byte[] showStatusMessage(final int mode, final String info, final String data) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            // 19: The Android is not powered. Please insert a Mechanical Heart.
            // 20: You recovered some fatigue by resting.
            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(mode);
            if (mode == 22) {
                mplew.writeMapleAsciiString(info); //name got Shield.
                mplew.writeMapleAsciiString(data); //Shield applied to name.
            }

            return mplew.getPacket();
        }

        public static byte[] showReturnStone(final int act) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            // 0: You can't use that item during a divorce.
            // 1: The location of your spouse is unknown.
            // 2: Your spouse is in an area where Return Stones cannot be used.
            // 3: Return Stones cannot be used here.
            mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
            mplew.write(23);
            mplew.write(act);

            return mplew.getPacket();
        }
    }
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="GuildPacket">

    public static class GuildPacket {

        public static byte[] guildInvite(int gid, String charName, int levelFrom, int jobFrom) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(5);
            mplew.writeInt(gid);
            mplew.writeMapleAsciiString(charName);

            return mplew.getPacket();
        }

        public static byte[] showGuildInfo(MapleCharacter c) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x1A);
            if (c == null || c.getMGC() == null) { //show empty guild (used for leaving, expelled)
                mplew.write(0);
                return mplew.getPacket();
            }
            MapleGuild g = World.Guild.getGuild(c.getGuildId());
            if (g == null) { //failed to read from DB - don't show a guild
                mplew.write(0);
                return mplew.getPacket();
            }
            mplew.write(1); //bInGuild
            getGuildInfo(mplew, g);

            return mplew.getPacket();
        }

        public static void getGuildInfo(MaplePacketLittleEndianWriter mplew, MapleGuild guild) {
            mplew.writeInt(guild.getId());
            mplew.writeMapleAsciiString(guild.getName());
            for (int i = 1; i <= 5; i++) {
                mplew.writeMapleAsciiString(guild.getRankTitle(i));
            }
            guild.addMemberData(mplew);
            mplew.writeInt(guild.getCapacity());
            mplew.writeShort(guild.getLogoBG());
            mplew.write(guild.getLogoBGColor());
            mplew.writeShort(guild.getLogo());
            mplew.write(guild.getLogoColor());
            mplew.writeMapleAsciiString(guild.getNotice());
            mplew.writeInt(guild.getGP());
        }

        public static byte[] newGuildInfo(final MapleCharacter c) {
            // Congratulation~  %s guild has been registered to our guild office...  I wish the best luck for you guys~
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(38);
            if (c == null || c.getMGC() == null) { // Show empty guild (used for leaving, expelled)
                return genericGuildMessage((byte) 37);
            }
            MapleGuild g = World.Guild.getGuild(c.getGuildId());
            if (g == null) {
                return genericGuildMessage((byte) 37);
            }
            getGuildInfo(mplew, g); // All empty data

            return mplew.getPacket();
        }

        public static byte[] newGuildMember(MapleGuildCharacter mgc) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x27);
            mplew.writeInt(mgc.getGuildId());
            mplew.writeInt(mgc.getId());
            mplew.writeAsciiString(mgc.getName(), 13);
            mplew.writeInt(mgc.getJobId());
            mplew.writeInt(mgc.getLevel());
            mplew.writeInt(mgc.getGuildRank()); //should be always 5 but whatevs
            mplew.writeInt(mgc.isOnline() ? 1 : 0); //should always be 1 too
            mplew.writeInt(1); //? could be guild signature, but doesn't seem to matter
            mplew.writeInt(3); //should always 3

            return mplew.getPacket();
        }

        public static byte[] memberLeft(MapleGuildCharacter mgc, boolean bExpelled) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(bExpelled ? 0x2F : 0x2C);
            mplew.writeInt(mgc.getGuildId());
            mplew.writeInt(mgc.getId());
            mplew.writeMapleAsciiString(mgc.getName());

            return mplew.getPacket();
        }

        public static byte[] guildDisband(int gid) {
            // The guild has been disbanded..  Please come back to me when you want to make a guild, again..
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x32);
            mplew.writeInt(gid);
            mplew.write(1);

            return mplew.getPacket();
        }

        public static byte[] guildCapacityChange(int gid, int capacity) {
            // Congratulation~  The number of guld members has now increased to %d ...  Please come back to me whenever you need to..
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x3A);
            mplew.writeInt(gid);
            mplew.write(capacity);

            return mplew.getPacket();
        }

        public static byte[] guildContribution(int gid, int cid, int c) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(72);
            mplew.writeInt(gid);
            mplew.writeInt(cid);
            mplew.writeInt(c);

            return mplew.getPacket();
        }

        public static byte[] changeRank(MapleGuildCharacter mgc) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x40);
            mplew.writeInt(mgc.getGuildId());
            mplew.writeInt(mgc.getId());
            mplew.write(mgc.getGuildRank());

            return mplew.getPacket();
        }

        public static byte[] rankTitleChange(int gid, String[] ranks) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x3E);
            mplew.writeInt(gid);
            for (String r : ranks) {
                mplew.writeMapleAsciiString(r);
            }

            return mplew.getPacket();
        }

        public static byte[] guildEmblemChange(int gid, short bg, byte bgcolor, short logo, byte logocolor) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x42);
            mplew.writeInt(gid);
            mplew.writeShort(bg);
            mplew.write(bgcolor);
            mplew.writeShort(logo);
            mplew.write(logocolor);

            return mplew.getPacket();
        }

        public static byte[] updateGP(int gid, int GP, int glevel) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x48);
            mplew.writeInt(gid);
            mplew.writeInt(GP);

            return mplew.getPacket();
        }

        public static byte[] guildNotice(int gid, String notice) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x44);
            mplew.writeInt(gid);
            mplew.writeMapleAsciiString(notice);

            return mplew.getPacket();
        }

        public static byte[] guildMemberLevelJobUpdate(MapleGuildCharacter mgc) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x3C);
            mplew.writeInt(mgc.getGuildId());
            mplew.writeInt(mgc.getId());
            mplew.writeInt(mgc.getLevel());
            mplew.writeInt(mgc.getJobId());

            return mplew.getPacket();
        }

        public static byte[] guildMemberOnline(int gid, int cid, boolean bOnline) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x3D);
            mplew.writeInt(gid);
            mplew.writeInt(cid);
            mplew.write(bOnline ? 1 : 0);

            return mplew.getPacket();
        }

        public static byte[] showGuildRanks(int npcid, List<GuildRankingInfo> all) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x49);
            mplew.writeInt(npcid);
            mplew.writeInt(all.size());
            for (GuildRankingInfo info : all) {
                mplew.writeMapleAsciiString(info.getName());
                mplew.writeInt(info.getGP());
                mplew.writeInt(info.getLogo());
                mplew.writeInt(info.getLogoColor());
                mplew.writeInt(info.getLogoBg());
                mplew.writeInt(info.getLogoBgColor());
            }

            return mplew.getPacket();
        }

        public static byte[] guildSkillPurchased(int gid, int sid, int level, long expiration, String purchase, String activate) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(85);
            mplew.writeInt(gid);
            mplew.writeInt(sid);
            mplew.writeShort(level);
            mplew.writeLong(PacketHelper.getTime(expiration));
            mplew.writeMapleAsciiString(purchase);
            mplew.writeMapleAsciiString(activate);

            return mplew.getPacket();
        }

        public static byte[] guildLeaderChanged(int gid, int oldLeader, int newLeader, int allianceId) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(89);
            mplew.writeInt(gid);
            mplew.writeInt(oldLeader);
            mplew.writeInt(newLeader);
            mplew.write(1); //new rank lol
            mplew.writeInt(allianceId);

            return mplew.getPacket();
        }

        public static byte[] denyGuildInvitation(String charname) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(0x37);
            mplew.writeMapleAsciiString(charname);

            return mplew.getPacket();
        }

        public static byte[] genericGuildMessage(byte code) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
            mplew.write(code);
            if (code == 87) {
                mplew.writeInt(0);
            }
            if (code == 3 || code == 59 || code == 60 || code == 61 || code == 84 || code == 87) {
                mplew.writeMapleAsciiString("");
            }
            // 1: Request for Guild Name
            // 18: Set Emblem
            // 3: Would you like to create %s Guild?
            // 34: The name is already in use... Please try other ones....
            // 39: Already joined the guild.
            // 41: You cannot make a guild, due to the limitation of minimum level requirement.
            // 42: Somebody has disagreed to form a guild...  Please come back to me when you meet with the right people...  You can only make a guild after getting agreement from all...
            // 46: Already joined the guild.
            // 47: The guild, you are trying to join, has already reached the max number of users.
            // 48: The character cannot be found in the current channel.
            // 37/44/58: The problem has happened during the process of forming the guild... Plese try again later...
            // 51: You are not in the guild.
            // 54: You are not in the guild.
            // 59: %s is currently not accepting guild invite message.
            // 60: '%s' is taking care of another invitation.
            // 61: %s has denied your guild invitation.
            // 62: Admin cannot make a guild.
            // 86: Extending Guild Skill failed.		
            // 81: There are less than 6 members remaining, so the quest cannot continue. Your Guild Quest will end in 5 seconds.
            // 82: The user that registered has disconnected, so the quest cannot continue. Your Guild Quest will end in 5 seconds.
            // default: The guild request has not been accepted, due to unknown reason.
            // 84/87: idk

            return mplew.getPacket();
        }

        public static byte[] BBSThreadList(final List<MapleBBSThread> bbs, int start) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
            mplew.write(6);
            if (bbs == null) {
                mplew.write(0);
                mplew.writeLong(0);
                return mplew.getPacket();
            }
            int threadCount = bbs.size();
            MapleBBSThread notice = null;
            for (MapleBBSThread b : bbs) {
                if (b.isNotice()) { //notice
                    notice = b;
                    break;
                }
            }
            mplew.write(notice == null ? 0 : 1);
            if (notice != null) { //has a notice
                addThread(mplew, notice);
            }
            if (threadCount < start) { //seek to the thread before where we start
                start = 0; //uh, we're trying to start at a place past possible
            }
            mplew.writeInt(threadCount); //each page has 10 threads, start = page # in packet but not here
            final int pages = Math.min(10, threadCount - start);
            mplew.writeInt(pages);
            for (int i = 0; i < pages; i++) {
                addThread(mplew, bbs.get(start + i)); //because 0 = notice
            }

            return mplew.getPacket();
        }

        private static void addThread(MaplePacketLittleEndianWriter mplew, MapleBBSThread rs) {
            mplew.writeInt(rs.localthreadID);
            mplew.writeInt(rs.ownerID);
            mplew.writeMapleAsciiString(rs.name);
            mplew.writeLong(PacketHelper.getKoreanTimestamp(rs.timestamp));
            mplew.writeInt(rs.icon);
            mplew.writeInt(rs.getReplyCount());
        }

        public static byte[] showThread(MapleBBSThread thread) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
            mplew.write(7);
            mplew.writeInt(thread.localthreadID);
            mplew.writeInt(thread.ownerID);
            mplew.writeLong(PacketHelper.getKoreanTimestamp(thread.timestamp));
            mplew.writeMapleAsciiString(thread.name);
            mplew.writeMapleAsciiString(thread.text);
            mplew.writeInt(thread.icon);
            mplew.writeInt(thread.getReplyCount());
            for (MapleBBSReply reply : thread.replies.values()) {
                mplew.writeInt(reply.replyid);
                mplew.writeInt(reply.ownerID);
                mplew.writeLong(PacketHelper.getKoreanTimestamp(reply.timestamp));
                mplew.writeMapleAsciiString(reply.content);
            }

            return mplew.getPacket();
        }
    }
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="PartyPacket">

    public static class PartyPacket {

        public static byte[] partyCreated(int partyid) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
            mplew.write(8);
            mplew.writeInt(partyid);
            mplew.writeInt(999999999);
            mplew.writeInt(999999999);
            mplew.writeInt(0);

            return mplew.getPacket();
        }

        public static byte[] partyInvite(MapleCharacter from) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
            mplew.write(4);
            mplew.writeInt(from.getParty() == null ? 0 : from.getParty().getId());
            mplew.writeMapleAsciiString(from.getName());
            mplew.write(0); // > 0 then won't send..

            return mplew.getPacket();
        }

        public static byte[] partyStatusMessage(int message, String charname) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            // 13: Already have joined a party.
            // 14: A beginner can't create a party.
            // 17: You have yet to join a party.
            // 20: You have joined the party.
            // 21: Already have joined a party.
            // 22: The party you're trying to join is already in full capacity.
            // 26: You have invited '%s' to your party.
            // 33: Cannot kick another user in this map
            // 36: This can only be given to a party member within the vicinity.
            // 37: Unable to hand over the leadership post; No party member is currently within the vicinity of the party leader.
            // 38: You may only change with the party member that's on the same channel.
            // 40: As a GM, you're forbidden from creating a party.
            // 45: The party leader has changed the party join request acceptance setting.
            // 46: Party settings could not be changed. Please try again later.
            // 51: Cannot be done in the current map.
            // 52: You've requested to join %s's party.
            // default: Your request for a party didn't work due to an unexpected error.
            mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
            mplew.write(message);
            if (message == 22 || message == 52) {
                mplew.writeMapleAsciiString(charname);
            } else if (message == 45) {
                mplew.write(0); // some mode?..
            }

            return mplew.getPacket();
        }

        public static void addPartyStatus(int forchannel, MapleParty party, MaplePacketLittleEndianWriter lew, boolean leaving) {
            addPartyStatus(forchannel, party, lew, leaving, false);
        }

        // 202 + 24 + 120
        public static void addPartyStatus(int forchannel, MapleParty party, MaplePacketLittleEndianWriter lew, boolean leaving, boolean exped) {
            List<MaplePartyCharacter> partymembers;
            if (party == null) {
                partymembers = new ArrayList<>();
            } else {
                partymembers = new ArrayList<>(party.getMembers());
            }
            while (partymembers.size() < 6) {
                partymembers.add(new MaplePartyCharacter());
            }
            for (MaplePartyCharacter partychar : partymembers) {
                lew.writeInt(partychar.getId()); // 24
            }
            for (MaplePartyCharacter partychar : partymembers) {
                lew.writeAsciiString(partychar.getName(), 13); // 78
            }
            for (MaplePartyCharacter partychar : partymembers) {
                lew.writeInt(partychar.getJobId()); // 24
            }
            for (MaplePartyCharacter partychar : partymembers) {
                lew.writeInt(partychar.getLevel()); // 24
            }
            for (MaplePartyCharacter partychar : partymembers) {
                lew.writeInt(partychar.isOnline() ? (partychar.getChannel() - 1) : -2); //24
            }
            lew.writeInt(party == null ? 0 : party.getLeader().getId());
            for (MaplePartyCharacter partychar : partymembers) {
                lew.writeInt(partychar.getChannel() == forchannel ? partychar.getMapid() : 0);  // 24
            }
            for (MaplePartyCharacter partychar : partymembers) { // 120
                if (partychar.getChannel() == forchannel && !leaving) {
                    lew.writeInt(partychar.getDoorTown());
                    lew.writeInt(partychar.getDoorTarget());
                    lew.writeInt(partychar.getDoorPosition().x);
                    lew.writeInt(partychar.getDoorPosition().y);
                } else {
                    lew.writeInt(leaving ? 999999999 : 0);
                    lew.writeInt(leaving ? 999999999 : 0);
                    lew.writeInt(leaving ? -1 : 0);
                    lew.writeInt(leaving ? -1 : 0);
                }
            }
        }

        public static byte[] updateParty(int forChannel, MapleParty party, PartyOperation op, MaplePartyCharacter target) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
            switch (op) {
                case DISBAND:
                case EXPEL:
                case LEAVE:
                    mplew.write(0x0C);
                    mplew.writeInt(party.getId());
                    mplew.writeInt(target.getId());
                    mplew.write(op == PartyOperation.DISBAND ? 0 : 1);
                    if (op != PartyOperation.DISBAND) {
                        mplew.write(op == PartyOperation.EXPEL ? 1 : 0);
                        mplew.writeMapleAsciiString(target.getName());
                        addPartyStatus(forChannel, party, mplew, op == PartyOperation.LEAVE);
                    }
                    break;
                case JOIN:
                    mplew.write(0x0F);
                    mplew.writeInt(party.getId());
                    mplew.writeMapleAsciiString(target.getName());
                    addPartyStatus(forChannel, party, mplew, false);
                    break;
                case SILENT_UPDATE:
                case LOG_ONOFF:
                    mplew.write(0x07);
                    mplew.writeInt(party.getId());
                    addPartyStatus(forChannel, party, mplew, op == PartyOperation.LOG_ONOFF);
                    break;
                case CHANGE_LEADER:
                case CHANGE_LEADER_DC:
                    mplew.write(0x19);
                    mplew.writeInt(target.getId());
                    mplew.write(op == PartyOperation.CHANGE_LEADER_DC ? 1 : 0);
                    break;
            }

            return mplew.getPacket();
        }

        public static byte[] partyPortal(int townId, int targetId, int skillId, Point position, boolean animation) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
            mplew.write(0x21);
            mplew.write(animation ? 0 : 1);
            mplew.writeInt(townId);
            mplew.writeInt(targetId);
            //mplew.writeInt(skillId);
            mplew.writePos(position);

            return mplew.getPacket();
        }
    }
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="BuddyListPacket">

    public static class BuddylistPacket {

        public static byte[] updateBuddylist(Collection<BuddylistEntry> buddylist) {
            return updateBuddylist(buddylist, 7);
        }

        public static byte[] updateBuddylist(Collection<BuddylistEntry> buddylist, int deleted) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
            mplew.write(deleted); // 7, 10, 18, // 8 = update, diff structure
            mplew.write(buddylist.size());
            for (BuddylistEntry buddy : buddylist) {
                mplew.writeInt(buddy.getCharacterId());
                mplew.writeAsciiString(buddy.getName(), 13);
                mplew.write(buddy.isVisible() ? 0 : 1);
                mplew.writeInt(buddy.getChannel() == -1 ? -1 : (buddy.getChannel() - 1));
            }
            for (int x = 0; x < buddylist.size(); x++) {
                mplew.writeInt(0);
            }

            return mplew.getPacket();
        }

        public static byte[] updateBuddyChannel(int characterid, int channel) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
            mplew.write(20);
            mplew.writeInt(characterid);
            mplew.write(0);
            mplew.writeInt(channel);

            return mplew.getPacket();
        }

        public static byte[] requestBuddylistAdd(int cidFrom, String nameFrom, int levelFrom, int jobFrom) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
            mplew.write(9);
            mplew.writeInt(cidFrom);
            mplew.writeMapleAsciiString(nameFrom);
            mplew.writeInt(cidFrom);
            mplew.writeAsciiString(nameFrom, 13);
            mplew.write(1);
            mplew.write(31);
            mplew.writeInt(0);

            return mplew.getPacket();
        }

        public static byte[] updateBuddyCapacity(int capacity) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
            mplew.write(21);
            mplew.write(capacity);

            return mplew.getPacket();
        }

        public static byte[] buddylistMessage(byte message) {
            MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

            // 11: Your buddy list is full.
            // 12: The user's buddy list is full
            // 13: That character is already registered as your buddy.
            // 14: Gamemaster is not available as a buddy.
            // 15: That character is not registered.
            // 23: You've already made the Friend Request. Please try again later.
            mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
            mplew.write(message);

            return mplew.getPacket();
        }
    }
    //</editor-fold>

    public static byte[] enableActions() {
        return updatePlayerStats(new EnumMap<>(MapleStat.class), true, null);
    }

    public static byte[] updatePlayerStats(final Map<MapleStat, Integer> stats, final MapleCharacter chr) {
        return updatePlayerStats(stats, false, chr);
    }

    public static byte[] updatePlayerStats(final Map<MapleStat, Integer> mystats, final boolean itemReaction, final MapleCharacter chr) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
        mplew.write(itemReaction ? 1 : 0);
        mplew.write(0);
        int updateMask = 0;
        for (MapleStat statupdate : mystats.keySet()) {
            updateMask |= statupdate.getValue();
        }
        mplew.writeInt(updateMask);
        for (final Entry<MapleStat, Integer> statupdate : mystats.entrySet()) {
            switch (statupdate.getKey()) {
                case SKIN:
                case LEVEL:
                    mplew.write(statupdate.getValue().byteValue());
                    break;
                case JOB:
                case STR:
                case DEX:
                case INT:
                case LUK:
                case HP:
                case MAXHP:
                case MP:
                case MAXMP:
                case AVAILABLEAP:
                case FAME:
                    mplew.writeShort(statupdate.getValue().shortValue());
                    break;
                case AVAILABLESP:
                    mplew.writeShort(chr.getRemainingSp());
                    break;
                case PET:
                    mplew.writeLong(statupdate.getValue()); //uniqueID of 3 pets
                    mplew.writeLong(statupdate.getValue());
                    mplew.writeLong(statupdate.getValue());
                    break;
                default:
                    mplew.writeInt(statupdate.getValue());
                    break;
            }
        }
//        if (updateMask == 0 && !itemReaction) {
//            mplew.write(1); //O_o
//        }
//        mplew.write(0); // SetSecondaryStatChangedPoint [byte]
//        mplew.write(0); // SetBattleRecoveryInfo [int][int]

        return mplew.getPacket();
    }

    public static byte[] temporaryStats_Aran() { // used for mercedes tutorial also
        final Map<MapleStat.Temp, Integer> stats = new EnumMap<>(MapleStat.Temp.class);

        stats.put(MapleStat.Temp.STR, 999);
        stats.put(MapleStat.Temp.DEX, 999);
        stats.put(MapleStat.Temp.INT, 999);
        stats.put(MapleStat.Temp.LUK, 999);
        stats.put(MapleStat.Temp.WATK, 255);
        stats.put(MapleStat.Temp.ACC, 999);
        stats.put(MapleStat.Temp.AVOID, 999);
        stats.put(MapleStat.Temp.SPEED, 140);
        stats.put(MapleStat.Temp.JUMP, 120);

        return temporaryStats(stats);
    }

    public static byte[] temporaryStats_Balrog(final MapleCharacter chr) {
        final Map<MapleStat.Temp, Integer> stats = new EnumMap<>(MapleStat.Temp.class);

        int offset = 1 + (chr.getLevel() - 90) / 20; //every 20 levels above 90, +1
        stats.put(MapleStat.Temp.STR, chr.getStat().getTotalStr() / offset);
        stats.put(MapleStat.Temp.DEX, chr.getStat().getTotalDex() / offset);
        stats.put(MapleStat.Temp.INT, chr.getStat().getTotalInt() / offset);
        stats.put(MapleStat.Temp.LUK, chr.getStat().getTotalLuk() / offset);
        stats.put(MapleStat.Temp.WATK, chr.getStat().getTotalWatk() / offset);
        stats.put(MapleStat.Temp.MATK, chr.getStat().getTotalMagic() / offset);

        return temporaryStats(stats);
    }

    public static byte[] temporaryStats(final Map<MapleStat.Temp, Integer> mystats) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TEMP_STATS.getValue());
        int updateMask = 0;
        for (MapleStat.Temp statupdate : mystats.keySet()) {
            updateMask |= statupdate.getValue();
        }
        mplew.writeInt(updateMask);
        for (final Entry<MapleStat.Temp, Integer> statupdate : mystats.entrySet()) {
            switch (statupdate.getKey()) {
                case SPEED:
                case JUMP:
                case UNKNOWN:
                    mplew.write(statupdate.getValue().byteValue());
                    break;
                default:
                    mplew.writeShort(statupdate.getValue().shortValue());
                    break;
            }
        }

        return mplew.getPacket();
    }

    public static byte[] temporaryStats_Reset() {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TEMP_STATS_RESET.getValue());

        return mplew.getPacket();
    }

    public static byte[] updateSkills(final Map<Skill, SkillEntry> update) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(7 + (update.size() * 20));

        mplew.writeShort(SendPacketOpcode.UPDATE_SKILLS.getValue());
        mplew.write(1);
        mplew.writeShort(update.size());
        for (final Entry<Skill, SkillEntry> z : update.entrySet()) {
            mplew.writeInt(z.getKey().getId());
            mplew.writeInt(z.getValue().skillevel);
            mplew.writeInt(z.getValue().masterlevel);
        }
        mplew.write(4);

        return mplew.getPacket();
    }

    public static byte[] giveFameErrorResponse(final int op) {
        return OnFameResult(op, null, true, 0);
    }

    public static byte[] OnFameResult(final int op, final String charname, final boolean raise, final int newFame) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        // 1: The user name is incorrectly entered.
        // 2: Users under level l5 are unable to toggle with fame.
        // 3: You can't raise or drop a level anymore for today.
        // 4: You can't raise or drop a level of fame of that character anymore for this month.
        // default: The level of fame has neither been raise or dropped due to an unexpected error.
        mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
        mplew.write(op);
        if (op == 0 || op == 5) { // Give / Receive Fame
            mplew.writeMapleAsciiString(charname == null ? "" : charname);
            mplew.write(raise ? 1 : 0); // 1 raise, 0 drop
            if (op == 0) { // Give				
                mplew.writeInt(newFame);
            }
        }

        return mplew.getPacket();
    }

    public static byte[] BombLieDetector(final boolean error, final int mapid, final int channel) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOMB_LIE_DETECTOR.getValue());
        mplew.write(error ? 2 : 1);
        mplew.writeInt(mapid);
        mplew.writeInt(channel); // 255 for all channels

        return mplew.getPacket();
    }

    public static byte[] report(final int mode) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        // 3: You have been reported by a user.
        // 65: Please try again later.
        // 66: Please re-check the character name, then try again.
        // 67: You do not have enough mesos to report.
        // 68: Unable to connect to the server.
        // 69: You have exceeded the number of reports available.
        // 71: You may only report from 0 to 0. -> Based on last packet.
        // 72: Unable to report due to previously being cited for a false report.
        mplew.writeShort(SendPacketOpcode.REPORT_RESPONSE.getValue());
        mplew.write(mode);
        if (mode == 2) { // You have successfully registered.
            mplew.write(0); // 0 or 1 only
            mplew.writeInt(1); // ?
        }

        return mplew.getPacket();
    }

    public static byte[] OnSetClaimSvrAvailableTime(final int from, final int to) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(4);

        // You may only report from 8 to 10.
        mplew.writeShort(SendPacketOpcode.REPORT_TIME.getValue());
        mplew.write(from);
        mplew.write(to);

        return mplew.getPacket();
    }

    public static byte[] OnClaimSvrStatusChanged(final boolean enable) { // Enable Report
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        mplew.writeShort(SendPacketOpcode.REPORT_STATUS.getValue());
        mplew.write(enable ? 1 : 0);

        return mplew.getPacket();
    }

    public static byte[] updateMount(MapleCharacter chr, boolean levelup) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_MOUNT.getValue());
        mplew.writeInt(chr.getId());
        mplew.writeInt(chr.getMount().getLevel());
        mplew.writeInt(chr.getMount().getExp());
        mplew.writeInt(chr.getMount().getFatigue());
        mplew.write(levelup ? 1 : 0);

        return mplew.getPacket();
    }

    public static byte[] getShowQuestCompletion(int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_QUEST_COMPLETION.getValue());
        mplew.writeShort(id);

        return mplew.getPacket();
    }

    public static byte[] useSkillBook(MapleCharacter chr, int skillid, int maxlevel, boolean canuse, boolean success) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.USE_SKILL_BOOK.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(1);
        mplew.writeInt(skillid);
        mplew.writeInt(maxlevel);
        mplew.write(canuse ? 1 : 0);
        mplew.write(success ? 1 : 0);

        return mplew.getPacket();
    }

    public static byte[] useAPSPReset(boolean spReset, int cid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(spReset ? SendPacketOpcode.SP_RESET.getValue() : SendPacketOpcode.AP_RESET.getValue());
        mplew.write(1); // update tick
        mplew.writeInt(cid);
        mplew.write(1); // 0 = fail

        return mplew.getPacket();
    }

    public static byte[] expandCharacterSlots(final int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        // -1: Your characters slots have already been expanded.
        // 0: Failed to expand character slots.
        // 1: You've increased your number of character slots.
        mplew.writeShort(SendPacketOpcode.EXPAND_CHARACTER_SLOTS.getValue());
        mplew.writeInt(mode);
        mplew.write(0); // idk, a boolean

        return mplew.getPacket();
    }

    public static byte[] finishedGather(int type) {
        return gatherSortItem(true, type);
    }

    public static byte[] finishedSort(int type) {
        return gatherSortItem(false, type);
    }

    public static byte[] gatherSortItem(boolean gather, int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(gather ? SendPacketOpcode.FINISH_GATHER.getValue() : SendPacketOpcode.FINISH_SORT.getValue());
        mplew.write(1);
        mplew.write(type);

        return mplew.getPacket();
    }

    public static byte[] updateGender(MapleCharacter chr) { // Send this upon entering cs
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_GENDER.getValue());
        mplew.write(chr.getGender());

        return mplew.getPacket();
    }

    public static byte[] charInfo(final MapleCharacter chr, final boolean isSelf) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHAR_INFO.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(chr.getLevel());
        mplew.writeShort(chr.getJob());
        mplew.writeShort(chr.getFame());
        mplew.write(chr.getMarriageId() > 0 ? 1 : 0);
        if (chr.getGuildId() <= 0) {
            mplew.writeMapleAsciiString("");
        } else {
            final MapleGuild gs = World.Guild.getGuild(chr.getGuildId());
            mplew.writeMapleAsciiString(gs != null ? gs.getName() : "");
        }
        mplew.writeMapleAsciiString(chr.getMessage());
        mplew.write(chr.getExpression());
        mplew.write(chr.getConstellation());
        mplew.write(chr.getBlood());
        mplew.write(chr.getMonth());
        mplew.write(chr.getDay());
        //mplew.write(0);

        byte index = 1;
        for (final MaplePet pet : chr.getPets()) {
            if (pet.getSummoned()) {
                mplew.write(index);
                mplew.writeInt(pet.getPetItemId());
                mplew.writeMapleAsciiString(pet.getName());
                mplew.write(pet.getLevel());
                mplew.writeShort(pet.getCloseness());
                mplew.write(pet.getFullness());
                mplew.writeShort(pet.getFlags());
                Item inv = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) (pet.getSummonedValue() == 2 ? -124 : pet.getSummonedValue() == 1 ? -114 : -126));
                mplew.writeInt(inv == null ? 0 : inv.getItemId());
                index++;
            }
        }
        mplew.write(0);

        if (chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18) != null && chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -19) != null) {
            final MapleMount mount = chr.getMount();
            mplew.write(1);
            mplew.writeInt(mount.getLevel());
            mplew.writeInt(mount.getExp());
            mplew.writeInt(mount.getFatigue());
        } else {
            mplew.write(0);
        }

        final int wishlistSize = chr.getWishlistSize();
        mplew.write(wishlistSize);
        if (wishlistSize > 0) {
            final int[] wishlist = chr.getWishlist();
            for (int x = 0; x < wishlistSize; x++) {
                mplew.writeInt(wishlist[x]);
            }
        }

        return mplew.getPacket();
    }

    public static byte[] getMonsterBookInfo(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOOK_INFO.getValue());
        mplew.writeInt(chr.getId());
        mplew.writeInt(chr.getLevel());
        chr.getMonsterBook().writeCharInfoPacket(mplew);

        return mplew.getPacket();
    }

    public static byte[] spawnPortal(final int townId, final int targetId, final int skillId, final Point pos) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
        mplew.writeInt(townId);
        mplew.writeInt(targetId);
        if (townId != 999999999 && targetId != 999999999) {
            //mplew.writeInt(skillId);
            mplew.writePos(pos);
        }

        return mplew.getPacket();
    }

    public static byte[] mechPortal(Point pos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MECH_PORTAL.getValue());
        mplew.writePos(pos);

        return mplew.getPacket();
    }

    public static byte[] echoMegaphone(String name, String message) { // RAWR, removed by nexon
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ECHO_MESSAGE.getValue());
        mplew.write(0); //1 = Your echo message has been successfully sent
        mplew.writeLong(PacketHelper.getTime(System.currentTimeMillis()));
        mplew.writeMapleAsciiString(name); //name
        mplew.writeMapleAsciiString(message); //message

        return mplew.getPacket();
    }

    public static byte[] showQuestMsg(final String msg) {
        return serverNotice(5, msg);
    }

    public static byte[] Mulung_Pts(int recv, int total) {
        return showQuestMsg("You have received " + recv + " training points, for the accumulated total of " + total + " training points.");
    }

    public static byte[] serverMessage(String message) {
        return serverMessage(4, 0, message, false);
    }

    public static byte[] serverNotice(int type, String message) {
        return serverMessage(type, 0, message, false);
    }

    public static byte[] serverNotice(int type, int channel, String message) {
        return serverMessage(type, channel, message, false);
    }

    public static byte[] serverNotice(int type, int channel, String message, boolean smegaEar) {
        return serverMessage(type, channel, message, smegaEar);
    }

    private static byte[] serverMessage(int type, int channel, String message, boolean megaEar) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        // 0: [Notice] <Msg>
        // 1: Popup <Msg>
        // 2: Megaphone
        // 3: Super Megaphone 
        // 4: Server Message
        // 5: Pink Text
        // 6: LightBlue Text ({} as Item)
        // 7: [int] -> Keep Wz Error
        // 8: Item Megaphone
        // 9: Item Megaphone
        // 10: Three Line Megaphone
        // 11: Item Megaphone
        // 12: Weather Effect
        // 13: Green Gachapon Message
        // 14: Orange Text
        // 15: Twin Dragon's Egg (got)
        // 16: Twin Dragon's Egg (duplicated)
        // 17: Lightblue Text
        // 18: Lightblue Text
        // 20: LightBlue Text ({} as Item)
        // 22: Skull Megaphone
        // 23: Ani Message
        // 25: Cake Pink Message
        // 26: Pie Yellow Message
        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
        mplew.write(type);
        if (type == 4) {
            mplew.write(1);
        }
        if (type != 23 && type != 24) {
            mplew.writeMapleAsciiString(message);
        }
        switch (type) {
            case 3: // Super Megaphone
            case 8:
            case 9:
                mplew.write(channel - 1);
                mplew.write(megaEar ? 1 : 0);
                break;
//            case 9: // Like Item Megaphone (Without Item)
//                mplew.write(channel - 1);
//                break;
            case 12: // Weather Effect
                mplew.writeInt(channel); // item id
                break;
            case 6:
            case 11:
            case 20:
                mplew.writeInt(channel >= 1000000 && channel < 6000000 ? channel : 0); // Item Id
                //E.G. All new EXP coupon {Ruby EXP Coupon} is now available in the Cash Shop!
                break;
            case 24:
                mplew.writeShort(0); // ?
                break;
        }

        return mplew.getPacket();
    }

    public static byte[] getGachaponMega(final String name, final String message, final Item item, final byte rareness) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
        mplew.write(13); // 15, 16 = twin dragon egg
        mplew.writeMapleAsciiString(name + message);
//        if (!dragon) { // only for gachapon
        mplew.writeInt(0); // 0/1 = light blue
//            mplew.writeInt(item.getItemId()); // item id
//        }
        PacketHelper.addItemInfo(mplew, item);

        return mplew.getPacket();
    }

    public static byte[] getAniMsg(final int questID, final int time) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
        mplew.write(23);
        mplew.writeShort(questID);
        mplew.writeInt(time);

        return mplew.getPacket();
    }

    public static byte[] tripleSmega(List<String> message, boolean ear, int channel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
        mplew.write(10);
        if (message.get(0) != null) {
            mplew.writeMapleAsciiString(message.get(0));
        }
        mplew.write(message.size());
        for (int i = 1; i < message.size(); i++) {
            if (message.get(i) != null) {
                mplew.writeMapleAsciiString(message.get(i));
            }
        }
        mplew.write(channel - 1);
        mplew.write(ear ? 1 : 0);

        return mplew.getPacket();
    }

    public static byte[] itemMegaphone(String msg, boolean whisper, int channel, Item item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
        mplew.write(8);
        mplew.writeMapleAsciiString(msg);
        mplew.write(channel - 1);
        mplew.write(whisper ? 1 : 0);
        PacketHelper.addItemPosition(mplew, item, true, false);
        if (item != null) {
            PacketHelper.addItemInfo(mplew, item);
        }

        return mplew.getPacket();
    }

    public static byte[] getPeanutResult(int itemId, short quantity, int itemId2, short quantity2, int ourItem) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PIGMI_REWARD.getValue());
        mplew.writeInt(itemId);
        mplew.writeShort(quantity);
        mplew.writeInt(ourItem);
        mplew.writeInt(itemId2);
        mplew.writeInt(quantity2);

        return mplew.getPacket();
    }

    public static byte[] getOwlOpen() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OWL_OF_MINERVA.getValue());
        mplew.write(9);
        mplew.write(GameConstants.owlItems.length);
        for (int i : GameConstants.owlItems) {
            mplew.writeInt(i); //these are the most searched items. too lazy to actually make
        }

        return mplew.getPacket();
    }

    public static byte[] getOwlSearched(final int itemSearch, final List<HiredMerchant> hms) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OWL_OF_MINERVA.getValue());
        mplew.write(6);
        mplew.writeInt(itemSearch);
        int size = 0;

        for (HiredMerchant hm : hms) {
            size += hm.searchItem(itemSearch).size();
        }
        mplew.writeInt(size);
        for (HiredMerchant hm : hms) {
            final List<MaplePlayerShopItem> items = hm.searchItem(itemSearch);
            for (MaplePlayerShopItem item : items) {
                mplew.writeMapleAsciiString(hm.getOwnerName());
                mplew.writeInt(hm.getMap().getId());
                mplew.writeMapleAsciiString(hm.getDescription());
                mplew.writeInt(item.item.getQuantity()); //I THINK.
                mplew.writeInt(item.bundles); //I THINK.
                mplew.writeInt(item.price);
                switch (InventoryHandler.OWL_ID) {
                    case 0:
                        mplew.writeInt(hm.getOwnerId()); //store ID
                        break;
                    case 1:
                        mplew.writeInt(hm.getStoreId());
                        break;
                    default:
                        mplew.writeInt(hm.getObjectId());
                        break;
                }
                mplew.write(hm.getFreeSlot() == -1 ? 1 : 0);
                mplew.write(GameConstants.getInventoryType(itemSearch).getType()); //position?
                if (GameConstants.getInventoryType(itemSearch) == MapleInventoryType.EQUIP) {
                    PacketHelper.addItemInfo(mplew, item.item);
                }
            }
        }

        return mplew.getPacket();
    }

    public static byte[] getOwlMessage(final int msg) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        // 0: Success
        // 1: The room is already closed.
        // 2: You can't enter the room due to full capacity.
        // 3: Other requests are being fulfilled this minute.
        // 4: You can't do it while you're dead.
        // 7: You are not allowed to trade other items at this point.
        // 17: You may not enter this store.
        // 18: The owner of the store is currently undergoing store maintenance. Please try again in a bit.
        // 23: This can only be used inside the Free Market.
        // default: This character is unable to do it.		
        mplew.writeShort(SendPacketOpcode.OWL_RESULT.getValue());
        mplew.write(msg);

        return mplew.getPacket();
    }

    public static byte[] sendEngagementRequest(String name, int cid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ENGAGE_REQUEST.getValue());
        mplew.write(0); //mode, 0 = engage, 1 = cancel, 2 = answer.. etc
        mplew.writeMapleAsciiString(name); // name
        mplew.writeInt(cid); // playerid

        return mplew.getPacket();
    }

    public static byte[] sendEngagement(final byte msg, final int item, final MapleCharacter male, final MapleCharacter female) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        // 11: You are now engaged.
        // 12: You are now married!
        // 13: Your engagement has been broken.
        // 14: You are no longer married.
        // 16: Congratulations!\r\nYour reservation was successfully made!
        // 18: You have entered the wrong character name.
        // 19: Your partner has to be in the same map.
        // 20: Your ETC slot is full.\r\nPlease remove some items.
        // 21: Your partner's ETC slots are full.
        // 22: You cannot be engaged to the same gender.
        // 23: You are already engaged.
        // 25: You are already married.
        // 24: She is already engaged.
        // 26: This person is already married.
        // 27: You're already in middle or proposing a person.
        // 28: She is currently being asked by another suitor.
        // 29: Unfortunately, the man who proposed to you has withdrawn his request for an engagement.
        // 30: She has politely declined your engagement request.
        // 31: The reservation has been canceled. Please try again later.
        // 32: You can't break the engagement after making reservations.
        // 34: This invitation is not valid.
        // 36: POPUP
        mplew.writeShort(SendPacketOpcode.ENGAGE_RESULT.getValue());
        mplew.write(msg); // 1103 custom quest
        if (msg == 11 || msg == 12) { // engage = 11, married = 12
            mplew.writeInt(0); // ringid or uniqueid
            mplew.writeInt(male.getId());
            mplew.writeInt(female.getId());
            mplew.writeShort(1); //always
            mplew.writeInt(item);
            mplew.writeInt(item); // wtf?repeat?
            mplew.writeAsciiString(male.getName(), 13);
            mplew.writeAsciiString(female.getName(), 13);
        } else if (msg == 15) { // Open Wedding invitation card
            mplew.writeAsciiString("Male", 13);
            mplew.writeAsciiString("Female", 13);
            mplew.writeShort(0); // type (Cathedral = 2, Vegas = other)
        }

        return mplew.getPacket();
    }

    public static byte[] sendWeddingGive() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WEDDING_GIFT.getValue());
        mplew.write(9);
        mplew.write(0); // item size, for each, additempos and additeminfo

        return mplew.getPacket();
    }

    public static byte[] sendWeddingReceive() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WEDDING_GIFT.getValue());
        mplew.write(10);
        mplew.writeLong(-1); // ?
        mplew.writeInt(0); // ?
        mplew.write(0);  // item size, for each, additempos and additeminfo		

        return mplew.getPacket();
    }

    public static byte[] giveWeddingItem() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WEDDING_GIFT.getValue());
        mplew.write(11); // 12: You cannot give more than one present for each wishlist. 13/14: Failed to send the gift.
        mplew.write(0); // for each : String
        mplew.writeLong(0); // could this be time?
        mplew.write(0); // size: For each: additeminfo (without pos)

        return mplew.getPacket();
    }

    public static byte[] receiveWeddingItem() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WEDDING_GIFT.getValue());
        mplew.write(15); // 16: idk.. 17: Item could not be retrieved\r\nbecause there was an item that\r\ncould only be acquired once.
        mplew.writeLong(0); // could this be time?
        mplew.write(0); // size: For each: additeminfo (without pos)

        return mplew.getPacket();
    }

    public static byte[] sendCashPetFood(final boolean success, final byte index) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3 + (success ? 1 : 0));

        mplew.writeShort(SendPacketOpcode.USE_CASH_PET_FOOD.getValue());
        mplew.write(success ? 0 : 1);
        if (success) {
            mplew.write(index);
        }

        return mplew.getPacket();
    }

    public static byte[] yellowChat(String msg) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.YELLOW_CHAT.getValue());
        mplew.write(-1); //could be something like mob displaying message.
        mplew.writeMapleAsciiString(msg);

        return mplew.getPacket();
    }

    public static byte[] shopDiscount(int percent) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOP_DISCOUNT.getValue());
        mplew.write(percent);

        return mplew.getPacket();
    }

    public static byte[] catchMob(int mobid, int itemid, byte success) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CATCH_MOB.getValue());
        mplew.write(success);
        mplew.writeInt(itemid);
        mplew.writeInt(mobid);

        return mplew.getPacket();
    }

    public static byte[] spawnPlayerNPC(PlayerNPC npc) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_NPC.getValue());
        mplew.write(1); // Size
        mplew.writeInt(npc.getId());
        mplew.writeMapleAsciiString(npc.getName());
        PacketHelper.addCharLook(mplew, npc, true); // remove npc.getPet(i), npc.getF()?

        return mplew.getPacket();
    }

    public static byte[] disabledNPC(final List<Integer> ids) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3 + (ids.size() * 4));

        mplew.writeShort(SendPacketOpcode.DISABLE_NPC.getValue());
        mplew.write(ids.size());
        for (final Integer i : ids) {
            mplew.writeInt(i);
        }

        return mplew.getPacket();
    }

    public static byte[] getCard(int itemid, int level) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GET_CARD.getValue());
        mplew.write(itemid > 0 ? 1 : 0);
        if (itemid > 0) {
            mplew.writeInt(itemid);
            mplew.writeInt(level);
        }
        return mplew.getPacket();
    }

    public static byte[] changeCardSet(int set) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CARD_SET.getValue());
        mplew.writeInt(set);

        return mplew.getPacket();
    }

    public static byte[] upgradeBook(Item book, MapleCharacter chr) { //slot -55
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOOK_STATS.getValue());
        mplew.writeInt(book.getPosition()); //negative or not
        PacketHelper.addItemInfo(mplew, book, chr);

        return mplew.getPacket();
    }

    public static byte[] getCardDrops(int cardid, final List<Integer> drops) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CARD_DROPS.getValue());
        mplew.writeInt(cardid);
        mplew.writeShort(drops == null ? 0 : drops.size());
        if (drops != null) {
            for (final Integer de : drops) {
                mplew.writeInt(de);
            }
        }

        return mplew.getPacket();
    }

    public static byte[] MulungEnergy(int energy) {
        return sendPyramidEnergy("energy", String.valueOf(energy));
    }

    public static byte[] sendPyramidEnergy(final String type, final String amount) {
        return sendString(1, type, amount); // energy, massacre_hit, massacre_miss, massacre_cool, massacre_skill, PRaid_Team, balloon_Team, redTeam, blueTeam, kill_count
    }

    public static byte[] sendGhostPoint(final String type, final String amount) {
        return sendString(2, type, amount); //PRaid_Point (0-1500???)
    }

    public static byte[] sendGhostStatus(final String type, final String amount) {
        return sendString(3, type, amount); //Red_Stage(1-5), Blue_Stage, blueTeamDamage, redTeamDamage, Bamboo_Used
    }

    public static byte[] sendString(final int type, final String object, final String amount) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        switch (type) {
            case 1:
                mplew.writeShort(SendPacketOpcode.SESSION_VALUE.getValue());
                break;
            case 2:
                mplew.writeShort(SendPacketOpcode.PARTY_VALUE.getValue());
                break;
            case 3:
                mplew.writeShort(SendPacketOpcode.MAP_VALUE.getValue());
                break;
        }
        mplew.writeMapleAsciiString(object);
        mplew.writeMapleAsciiString(amount);

        return mplew.getPacket();
    }

    public static byte[] fairyPendantMessage(final int termStart, final int incExpR) { // bonusExp, <= 0x3D
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(14);

        mplew.writeShort(SendPacketOpcode.EXP_BONUS.getValue());
        mplew.writeInt(0x11); // 0x11 = pendant, 0x31 = evan medal
        mplew.writeInt(0/*termStart*/); // hour = 0 upon equipping
        mplew.writeInt(incExpR);

        return mplew.getPacket();
    }

    public static byte[] potionDiscountMessage(final int type, final int potionDiscR) { // PotionDiscount, <= 0x3D
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(10);

        mplew.writeShort(SendPacketOpcode.POTION_BONUS.getValue());
        mplew.writeInt(type); // 0x11 = pendant, 0x31 = evan medal
        mplew.writeInt(potionDiscR);

        return mplew.getPacket();
    }

    public static byte[] sendLevelup(boolean family, int level, String name) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.LEVEL_UPDATE.getValue());
        mplew.write(family ? 1 : 2);
        mplew.writeInt(level);
        mplew.writeMapleAsciiString(name);

        return mplew.getPacket();
    }

    public static byte[] sendMarriage(boolean family, String name) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MARRIAGE_UPDATE.getValue());
        mplew.write(family ? 1 : 0);
        mplew.writeMapleAsciiString(name);

        return mplew.getPacket();
    }

    public static byte[] sendJobup(boolean family, int jobid, String name) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.JOB_UPDATE.getValue());
        mplew.write(family ? 1 : 0);
        mplew.writeInt(jobid); //or is this a short
        mplew.writeMapleAsciiString((!family ? "> " : "") + name);

        return mplew.getPacket();
    }

    public static byte[] getAvatarMega(final MapleCharacter chr, final int channel, final int itemId, final List<String> text, final boolean ear) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.AVATAR_MEGA.getValue());
        mplew.writeInt(itemId);
        mplew.writeMapleAsciiString(chr.getName());
        for (final String i : text) {
            mplew.writeMapleAsciiString(i);
        }
        mplew.writeInt(channel - 1); // channel
        mplew.write(ear ? 1 : 0);
        PacketHelper.addCharLook(mplew, chr, true);

        return mplew.getPacket();
    }

    public static byte[] GMPoliceMessage(final boolean dc) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        mplew.writeShort(SendPacketOpcode.GM_POLICE.getValue());
        mplew.write(dc ? 10 : 0);
        return mplew.getPacket();
    }

    public static byte[] pendantSlot(boolean p) { //slot -59
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PENDANT_SLOT.getValue());
        mplew.write(p ? 1 : 0);

        return mplew.getPacket();
    }

    public static byte[] followRequest(int chrid) {
        final MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.FOLLOW_REQUEST.getValue());
        mplew.writeInt(chrid);

        return mplew.getPacket();
    }

    public static byte[] getTopMsg(String msg) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TOP_MSG.getValue());
        mplew.writeMapleAsciiString(msg);

        return mplew.getPacket();
    }

    public static byte[] showMidMsg(String s, int l) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MID_MSG.getValue());
        mplew.write(l); // boolean
        mplew.writeMapleAsciiString(s);
        mplew.write(s.length() > 0 ? 0 : 1); //boolean remove?

        return mplew.getPacket();
    }

    public static byte[] getMidMsg(String msg, boolean keep, int index) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MID_MSG.getValue());
        mplew.write(index); //where the message should appear on the screen
        mplew.writeMapleAsciiString(msg);
        mplew.write(keep ? 0 : 1);

        return mplew.getPacket();
    }

    public static byte[] clearMidMsg() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CLEAR_MID_MSG.getValue());

        return mplew.getPacket();
    }
}
