package handling.cashshop.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

import constants.GameConstants;
import client.MapleClient;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.inventory.MapleInventoryType;
import client.inventory.MapleRing;
import client.inventory.MapleInventoryIdentifier;
import client.inventory.Item;
import client.inventory.MapleInventory;
import constants.ServerConstants;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.CharacterTransfer;
import handling.world.World;
import java.util.List;
import server.CashItemFactory;
import server.CashItemInfo;
import server.CashShop;
import server.MTSCart;
import server.MTSStorage;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import tools.FileoutputUtil;
import tools.packet.CField;
import tools.packet.MTSCSPacket;
import tools.Triple;
import tools.data.LittleEndianAccessor;

public class CashShopOperation {

    public static void LeaveCS(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        CashShopServer.getPlayerStorageMTS().deregisterPlayer(chr);
        CashShopServer.getPlayerStorage().deregisterPlayer(chr);
        c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());

        try {

            World.ChannelChange_Data(new CharacterTransfer(chr), chr.getId(), c.getChannel());
            c.sendPacket(CField.getChannelChange(c, Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1])));
        } finally {
            final String s = c.getSessionIPAddress();
            LoginServer.addIPAuth(s.substring(s.indexOf('/') + 1, s.length()));
            chr.saveToDB(false, true);
            c.setPlayer(null);
            c.setReceiving(false);
            c.getSession().close();
        }
    }

    public static void EnterCS(final int playerid, final MapleClient c) {
        CharacterTransfer transfer = CashShopServer.getPlayerStorage().getPendingCharacter(playerid);
        boolean mts = false;
        if (transfer == null) {
            transfer = CashShopServer.getPlayerStorageMTS().getPendingCharacter(playerid);
            mts = true;
            if (transfer == null) {
                c.getSession().close();
                return;
            }
        }
        MapleCharacter chr = MapleCharacter.ReconstructChr(transfer, c, false);

        c.setPlayer(chr);
        c.setAccID(chr.getAccountID());
        c.loadVip(chr.getAccountID());
        c.loadAccountData(chr.getAccountID());

        if (!c.CheckIPAddress()) { // Remote hack
            c.setPlayer(null);
            c.getSession().close();
            return;
        }

        final int state = c.getLoginState();
        boolean allowLogin = false;
        if (state == MapleClient.CHANGE_CHANNEL) {
            if (!World.isCharacterListConnected(c.loadCharacterNames(c.getWorld()))) {
                allowLogin = true;
            }
        }
        if (!allowLogin) {
            c.setPlayer(null);
            c.getSession().close();
            return;
        }
        c.updateLoginState(MapleClient.LOGIN_LOGGEDIN, c.getSessionIPAddress());
        if (mts) {
            CashShopServer.getPlayerStorageMTS().registerPlayer(chr);
            c.sendPacket(MTSCSPacket.startMTS(chr));
            final MTSCart cart = MTSStorage.getInstance().getCart(c.getPlayer().getId());
            cart.refreshCurrentView();
            MTSOperation.MTSUpdate(cart, c);
        } else {
            CashShopServer.getPlayerStorage().registerPlayer(chr);
            c.sendPacket(MTSCSPacket.warpCS(c));
            CSUpdate(c);
        }

    }

    public static void CSUpdate(final MapleClient c) {
        c.sendPacket(MTSCSPacket.getCSGifts(c));
        doCSPackets(c);
        c.sendPacket(MTSCSPacket.sendWishList(c.getPlayer(), false));
    }

    public static void CouponCode(final String code, final MapleClient c) {
        if (code.length() <= 0) {
            return;
        }
        Triple<Boolean, Integer, Integer> info = null;
        try {
            info = MapleCharacterUtil.getNXCodeInfo(code);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (info != null && info.left) {
            int type = info.mid, item = info.right;
            try {
                MapleCharacterUtil.setNXCodeUsed(c.getPlayer().getName(), code);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            /*
             * Explanation of type!
             * Basically, this makes coupon codes do
             * different things!
             *
             * Type 1: Gash
             * Type 2: 楓葉點數
             * Type 3: 物品(SN)
             * Type 4: 楓幣
             */
            Map<Integer, Item> itemz = new HashMap<>();
            int maplePoints = 0, mesos = 0;
            switch (type) {
                case 1:
                case 2:
                    c.getPlayer().modifyCSPoints(type, item, false);
                    maplePoints = item;
                    break;
                case 3:
                    CashItemInfo itez = CashItemFactory.getInstance().getItem(item);
                    if (itez == null) {
                        c.sendPacket(MTSCSPacket.sendCSFail(0));
                        return;
                    }
                    byte slot = MapleInventoryManipulator.addId(c, itez.getId(), (short) 1, "", "Cash shop: coupon code" + " on " + FileoutputUtil.CurrentReadable_Date());
                    if (slot <= -1) {
                        c.sendPacket(MTSCSPacket.sendCSFail(0));
                        return;
                    } else {
                        itemz.put(item, c.getPlayer().getInventory(GameConstants.getInventoryType(item)).getItem(slot));
                    }
                    break;
                case 4:
                    c.getPlayer().gainMeso(item, false);
                    mesos = item;
                    break;
            }
            c.sendPacket(MTSCSPacket.showCouponRedeemedItem(itemz, mesos, maplePoints, c));
        } else {
            c.sendPacket(MTSCSPacket.sendCSFail(info == null ? 0x9C : 0x84)); //A1, 9F
        }
    }

    public static final void BuyCashItem(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        final int action = slea.readByte();
        switch (action) {
            case 0:
                slea.skip(2);
                CouponCode(slea.readMapleAsciiString(), c);
                break;
            case 3: { // 購買
                final int toCharge = slea.readByte() + 1;
                final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
                if (item != null && chr.getCSPoints(toCharge) >= item.getPrice()) {
                    if (!item.genderEquals(c.getPlayer().getGender())) {
                        c.sendPacket(MTSCSPacket.sendCSFail(0x89));
                        doCSPackets(c);
                        return;
                    } else if (c.getPlayer().getCashInventory().getItemsSize() >= 100) {
                        c.sendPacket(MTSCSPacket.sendCSFail(0x80));
                        doCSPackets(c);
                        return;
                    }
                    for (int i : GameConstants.cashBlock) {
                        if (item.getId() == i) {
                            c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                            doCSPackets(c);
                            return;
                        }
                    }
                    chr.modifyCSPoints(toCharge, -item.getPrice(), false);
                    Item itemz = chr.getCashInventory().toItem(item, chr);
                    if (itemz != null && itemz.getUniqueId() > 0 && itemz.getItemId() == item.getId() && itemz.getQuantity() == item.getCount()) {
                        chr.getCashInventory().addToInventory(itemz);
                        //c.sendPacket(MTSCSPacket.confirmToCSInventory(itemz, c.getAccID(), item.getSN()));
                        c.sendPacket(MTSCSPacket.showBoughtCSItem(itemz, item.getSN(), c.getAccID()));
                        if (ServerConstants.LOG_CSBUY) {
                            FileoutputUtil.logToFile("紀錄/監聽/商城/商城購買.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 帳號: " + c.getAccountName() + " 玩家: " + c.getPlayer().getName() + " 使用了" + (toCharge == 1 ? "點券" : "楓葉點數") + item.getPrice() + "點 來購買" + item.getId() + "x" + item.getCount());
                        }
                    } else {
                        c.sendPacket(MTSCSPacket.sendCSFail(0));
                    }
                } else {
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                }
                break;
            }
            case 4: // 一般送禮
            case 29: { // 套裝送禮
                String secondPassword = slea.readMapleAsciiString();

//                if (!c.check2ndPassword(secondPassword)) {
//                    c.getPlayer().dropMessage(1, "第二組密碼錯誤。");
//                    doCSPackets(c);
//                    return;
//                }
                final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
                String partnerName = slea.readMapleAsciiString();
                String msg = slea.readMapleAsciiString();
                if (item == null || c.getPlayer().getCSPoints(1) < item.getPrice() || msg.getBytes().length > 73 || msg.getBytes().length < 1) { //dont want packet editors gifting random stuff =P
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                    doCSPackets(c);
                    return;
                }
                Triple<Integer, Integer, Integer> info = MapleCharacterUtil.getInfoByName(partnerName, c.getPlayer().getWorld());
                if (info == null || info.getLeft() <= 0 || info.getLeft() == c.getPlayer().getId() || info.getMid() == c.getAccID()) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0xA2)); //9E v75
                    doCSPackets(c);
                    return;
                } else if (!item.genderEquals(info.getRight())) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0x89));
                    doCSPackets(c);
                    return;
                } else {
                    for (int i : GameConstants.cashBlock) {
                        if (item.getId() == i) {
                            c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                            doCSPackets(c);
                            return;
                        }
                    }
                    c.getPlayer().getCashInventory().gift(info.getLeft(), c.getPlayer().getName(), msg, item.getSN(), MapleInventoryIdentifier.getInstance());
                    c.getPlayer().modifyCSPoints(1, -item.getPrice(), false);
                    c.sendPacket(MTSCSPacket.sendGift(item.getPrice(), item.getId(), item.getCount(), partnerName, action == 34));
                    if (ServerConstants.LOG_CSBUY) {
                        FileoutputUtil.logToFile("紀錄/監聽/商城/商城送禮.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 帳號: " + c.getAccountName() + " 玩家: " + c.getPlayer().getName() + " 使用了點券" + item.getPrice() + "點 贈送了" + item.getId() + "x" + item.getCount() + " 給" + partnerName);
                    }
                }
                break;
            }
            case 5: // 購物車
                chr.clearWishlist();
                if (slea.available() < 40) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                    doCSPackets(c);
                    return;
                }
                int[] wishlist = new int[10];
                for (int i = 0; i < 10; i++) {
                    wishlist[i] = slea.readInt();
                }
                chr.setWishlist(wishlist);
                c.sendPacket(MTSCSPacket.sendWishList(chr, true));
                break;
            case 6: { // 擴充欄位
                final int toCharge = slea.readByte() + 1;
                final boolean coupon = slea.readByte() > 0;
                if (coupon) {
//                    final MapleInventoryType type = getInventoryType(slea.readInt());
//                    if (chr.getCSPoints(toCharge) >= (GameConstants.GMS ? 6000 : 12000) && chr.getInventory(type).getSlotLimit() < 89) {
//                        chr.modifyCSPoints(toCharge, (GameConstants.GMS ? -6000 : -12000), false);
//                        chr.getInventory(type).addSlot((byte) 8);
//                        chr.dropMessage(1, "欄位擴充至 " + chr.getInventory(type).getSlotLimit() + " 格");
//                    } else {
//                        c.sendPacket(MTSCSPacket.sendCSFail(0xA4));
//                    }
                } else {
                    final MapleInventoryType type = MapleInventoryType.getByType(slea.readByte());
                    if (chr.getCSPoints(toCharge) >= 100 && chr.getInventory(type).getSlotLimit() < 45) {
                        chr.modifyCSPoints(toCharge, -100, false);
                        chr.getInventory(type).addSlot((byte) 4);
                        chr.dropMessage(1, "欄位擴充至 " + chr.getInventory(type).getSlotLimit() + " 格");
                        if (ServerConstants.LOG_CSBUY) {
                            FileoutputUtil.logToFile("紀錄/監聽/商城/商城擴充.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 帳號: " + c.getAccountName() + " 玩家: " + c.getPlayer().getName() + " 使用了" + (toCharge == 1 ? "點券" : "楓葉點數") + "100點 來購買擴充欄位" + type.name() + "8格 目前共有" + chr.getInventory(type).getSlotLimit() + "格");
                        }
                    } else {
                        c.sendPacket(MTSCSPacket.sendCSFail(0xA4));
                    }
                }
                break;
            }
            case 7: { // 擴充倉庫欄位
                final int toCharge = slea.readByte() + 1;
                final int coupon = slea.readByte() > 0 ? 2 : 1;
                if (chr.getCSPoints(toCharge) >= 100 * coupon && chr.getStorage().getSlots() < (49 - (4 * coupon))) {
                    chr.modifyCSPoints(toCharge, -100 * coupon, false);
                    chr.getStorage().increaseSlots((byte) (4 * coupon));
                    chr.getStorage().saveToDB();
                    chr.dropMessage(1, "欄位擴充至 " + chr.getStorage().getSlots() + " 格");
                    if (ServerConstants.LOG_CSBUY) {
                        FileoutputUtil.logToFile("紀錄/監聽/商城/商城擴充.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 帳號: " + c.getAccountName() + " 玩家: " + c.getPlayer().getName() + " 使用了" + (toCharge == 1 ? "點券" : "楓葉點數") + "100點 來購買擴充欄位倉庫4格 目前共有" + chr.getStorage().getSlots() + "格");
                    }
                } else {
                    c.sendPacket(MTSCSPacket.sendCSFail(0xA4));
                }
                break;
            }
            case 8: { //...9 = pendant slot expansion
                final int toCharge = slea.readByte() + 1;
                CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
                int slots = c.getCharacterSlots();
                if (item == null || c.getPlayer().getCSPoints(toCharge) < item.getPrice() || slots > 15 || item.getId() != 5430000) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                    doCSPackets(c);
                    return;
                }
                if (c.gainCharacterSlot()) {
                    c.getPlayer().modifyCSPoints(toCharge, -item.getPrice(), false);
                    chr.dropMessage(1, "角色欄位擴充至 " + (slots + 1) + " 格");
                    if (ServerConstants.LOG_CSBUY) {
                        FileoutputUtil.logToFile("紀錄/監聽/商城/商城擴充.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 帳號: " + c.getAccountName() + " 玩家: " + c.getPlayer().getName() + " 使用了" + (toCharge == 1 ? "點券" : "楓葉點數") + item.getPrice() + "點 來購買擴充角色欄位 目前共有" + c.getCharacterSlots() + "格");
                    }
                } else {
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                }
                break;
            }
            case 12: { // 購物商城→道具欄位
                //uniqueid, 00 01 01 00, type->position(short)
                Item item = c.getPlayer().getCashInventory().findByCashId((int) slea.readLong());
                if (item != null && item.getQuantity() > 0 && MapleInventoryManipulator.checkSpace(c, item.getItemId(), item.getQuantity(), item.getOwner())) {
                    Item item_ = item.copy();
                    short pos = MapleInventoryManipulator.addbyItem(c, item_, true);
                    if (pos >= 0) {
                        if (item_.getPet() != null) {
                            item_.getPet().setInventoryPosition(pos);
                            c.getPlayer().addPet(item_.getPet());
                        }
                        c.getPlayer().getCashInventory().removeFromInventory(item);
                        c.sendPacket(MTSCSPacket.confirmFromCSInventory(item_, pos));
                    } else {
                        c.sendPacket(MTSCSPacket.sendCSFail(0xB1));
                    }
                } else {
                    c.sendPacket(MTSCSPacket.sendCSFail(0xB1));
                }
                break;
            }
            case 13: { // 道具欄位→購物商城
                CashShop cs = chr.getCashInventory();
                int cashId = (int) slea.readLong();
                byte type = slea.readByte();
                MapleInventory mi = chr.getInventory(MapleInventoryType.getByType(type));
                Item item1 = mi.findByUniqueId(cashId);
                if (item1 == null) {
                    c.sendPacket(MTSCSPacket.showNXMapleTokens(chr));
                    return;
                }
                if (cs.getItemsSize() < 100) {
                    int sn = CashItemFactory.getInstance().getItemSN(item1.getItemId());
                    cs.addToInventory(item1);
                    mi.removeSlot(item1.getPosition());
                    c.sendPacket(MTSCSPacket.confirmToCSInventory(item1, c.getAccID(), sn));
                } else {
                    chr.dropMessage(1, "移動失敗。");
                }
                break;
            }
            case 24: { // 販售
                final String secondPassword = slea.readMapleAsciiString();
                chr.dropMessage(1, "目前無法使用。");
                break;
            }
            case 27:
            case 33: {
                final String secondPassword = slea.readMapleAsciiString(); // as13
                final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
                final String partnerName = slea.readMapleAsciiString();
                final String msg = slea.readMapleAsciiString();
                if (item == null || !GameConstants.isEffectRing(item.getId()) || c.getPlayer().getCSPoints(1) < item.getPrice() || msg.getBytes().length > 73 || msg.getBytes().length < 1) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                    doCSPackets(c);
                    return;
                } else if (!item.genderEquals(c.getPlayer().getGender())) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0x89));
                    doCSPackets(c);
                    return;
                } else if (c.getPlayer().getCashInventory().getItemsSize() >= 100) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0x80));
                    doCSPackets(c);
                    return;
                }
                for (int i : GameConstants.cashBlock) { //just incase hacker
                    if (item.getId() == i) {
                        c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                        doCSPackets(c);
                        return;
                    }
                }
                Triple<Integer, Integer, Integer> info = MapleCharacterUtil.getInfoByName(partnerName, c.getPlayer().getWorld());
                if (info == null || info.getLeft() <= 0 || info.getLeft() == c.getPlayer().getId()) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0xB4)); //9E v75
                    doCSPackets(c);
                    return;
                } else if (info.getMid() == c.getAccID()) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0xA3)); //9D v75
                    doCSPackets(c);
                    return;
                } else {
                    if (info.getRight() == c.getPlayer().getGender() && action == 30) {
                        c.sendPacket(MTSCSPacket.sendCSFail(0x89)); //9B v75
                        doCSPackets(c);
                        return;
                    }

                    int err = MapleRing.createRing(item.getId(), c.getPlayer(), partnerName, msg, info.getLeft(), item.getSN());

                    if (err != 1) {
                        c.sendPacket(MTSCSPacket.sendCSFail(0)); //9E v75
                        doCSPackets(c);
                        return;
                    }
                    c.getPlayer().modifyCSPoints(1, -item.getPrice(), false);
                }
                break;
            }
            case 28: {
                final int toCharge = slea.readByte() + 1;
                final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
                List<CashItemInfo> ccc = null;
                if (item != null) {
                    ccc = CashItemFactory.getInstance().getPackageItems(item.getId());
                }
                if (item == null || ccc == null || c.getPlayer().getCSPoints(toCharge) < item.getPrice()) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                    doCSPackets(c);
                    return;
                } else if (!item.genderEquals(c.getPlayer().getGender())) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0x89));
                    doCSPackets(c);
                    return;
                } else if (c.getPlayer().getCashInventory().getItemsSize() >= (100 - ccc.size())) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0x80));
                    doCSPackets(c);
                    return;
                }
                for (int iz : GameConstants.cashBlock) {
                    if (item.getId() == iz) {
                        c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                        doCSPackets(c);
                        return;
                    }
                }
                Map<Integer, Item> ccz = new HashMap<>();
                for (CashItemInfo i : ccc) {
//                    final CashItemInfo cii = CashItemFactory.getInstance().getSimpleItem(i);
//                    if (cii == null) {
//                        continue;
//                    }
                    Item itemz = c.getPlayer().getCashInventory().toItem(i);
                    if (itemz == null || itemz.getUniqueId() <= 0) {
                        continue;
                    }
//                    for (int iz : GameConstants.cashBlock) {
//                        if (itemz.getItemId() == iz) {
//                            continue;
//                        }
//                    }
                    ccz.put(i.getSN(), itemz);
                    c.getPlayer().getCashInventory().addToInventory(itemz);
                }
                chr.modifyCSPoints(toCharge, -item.getPrice(), false);
                c.sendPacket(MTSCSPacket.showBoughtCSPackage(ccz, c.getAccID()));
                if (ServerConstants.LOG_CSBUY) {
                    FileoutputUtil.logToFile("紀錄/監聽/商城/商城購買.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 帳號: " + c.getAccountName() + " 玩家: " + c.getPlayer().getName() + " 使用了" + (toCharge == 1 ? "點券" : "楓葉點數") + item.getPrice() + "點 來購買" + item.getId() + "x" + item.getCount());
                }
                break;
            }

            case 30: {
                final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
                if (item == null || !MapleItemInformationProvider.getInstance().isQuestItem(item.getId())) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0));
                    doCSPackets(c);
                    return;
                } else if (c.getPlayer().getMeso() < item.getPrice()) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0xB8));
                    doCSPackets(c);
                    return;
                } else if (c.getPlayer().getInventory(GameConstants.getInventoryType(item.getId())).getNextFreeSlot() < 0) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0x80));
                    doCSPackets(c);
                    return;
                }
                for (int iz : GameConstants.cashBlock) {
                    if (item.getId() == iz) {
                        c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                        doCSPackets(c);
                        return;
                    }
                }
                byte pos = MapleInventoryManipulator.addId(c, item.getId(), (short) item.getCount(), null, "Cash shop: quest item" + " on " + FileoutputUtil.CurrentReadable_Date());
                if (pos < 0) {
                    c.sendPacket(MTSCSPacket.sendCSFail(0x80));
                    doCSPackets(c);
                    return;
                }
                chr.gainMeso(-item.getPrice(), false);
                c.sendPacket(MTSCSPacket.showBoughtCSQuestItem(item.getPrice(), (short) item.getCount(), pos, item.getId()));
                break;
            }
            default:
                System.out.println("New Action: " + action + " Remaining: " + slea.toString());
                c.sendPacket(MTSCSPacket.sendCSFail(0));
                break;
        }
        doCSPackets(c);
    }

    private static final MapleInventoryType getInventoryType(final int id) {
        switch (id) {
            case 50200093:
                return MapleInventoryType.EQUIP;
            case 50200094:
                return MapleInventoryType.USE;
            case 50200197:
                return MapleInventoryType.SETUP;
            case 50200095:
                return MapleInventoryType.ETC;
            default:
                return MapleInventoryType.UNDEFINED;
        }
    }

    public static final void doCSPackets(MapleClient c) {
        c.sendPacket(MTSCSPacket.getCSInventory(c));
        c.sendPacket(MTSCSPacket.showNXMapleTokens(c.getPlayer()));
        c.sendPacket(MTSCSPacket.enableCSUse());
        c.getPlayer().getCashInventory().checkExpire(c);
    }
}
