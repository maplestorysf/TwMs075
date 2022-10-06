package handling.channel.handler;

import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import client.inventory.Item;
import client.inventory.MapleInventoryType;
import client.MapleClient;
import client.MapleCharacter;
import constants.GameConstants;
import client.inventory.ItemLoader;
import constants.ServerConstants;
import database.DatabaseConnection;
import handling.world.World;
import java.util.Map;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MerchItemPackage;
import tools.FileoutputUtil;
import tools.Pair;
import tools.StringUtil;
import tools.packet.PlayerShopPacket;
import tools.data.LittleEndianAccessor;

public class HiredMerchantHandler {

    public static final boolean UseHiredMerchant(final MapleClient c, final boolean packet) {
        if (c.getPlayer().getMap() != null && c.getPlayer().getMap().allowPersonalShop()) {
            final byte state = checkExistance(c.getPlayer().getAccountID(), c.getPlayer().getId());

            switch (state) {
                case 1:
                    c.getPlayer().dropMessage(1, "請先去找富蘭德里領取你之前擺的東西");
                    break;
                case 0:
                    boolean merch = World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId());
                    if (!merch) {
                        if (c.getChannelServer().isShutdown()) {
                            c.getPlayer().dropMessage(1, "伺服器即將關閉，無法使用精靈商人.");
                            return false;
                        }
                        if (packet) {
                            c.sendPacket(PlayerShopPacket.sendTitleBox());
                        }
                        return true;
                    } else {
                        c.getPlayer().dropMessage(1, "請先關閉在" + World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId()) + "頻的精靈商人。");
                    }
                    break;
                default:
                    c.getPlayer().dropMessage(1, "An unknown error occured.");
                    break;
            }
        } else {
            c.getSession().close();
        }
        return false;
    }

    private static final byte checkExistance(final int accid, final int cid) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT * from hiredmerch where accountid = ? OR characterid = ?");
            ps.setInt(1, accid);
            ps.setInt(2, cid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ps.close();
                rs.close();
                return 1;
            }
            rs.close();
            ps.close();
            return 0;
        } catch (SQLException se) {
            return -1;
        }
    }

    public static final void displayMerch(MapleClient c) {
        final int conv = c.getPlayer().getConversation();
        boolean merch = World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId());
        if (merch) {
            c.getPlayer().dropMessage(1, "Please close the existing store and try again.");
            c.getPlayer().setConversation(0);
        } else if (c.getChannelServer().isShutdown()) {
            c.getPlayer().dropMessage(1, "伺服器關閉中，目前無法操作。");
            c.getPlayer().setConversation(0);
        } else if (conv == 3) { // Hired Merch
            final MerchItemPackage pack = loadItemFrom_Database(c.getPlayer().getAccountID());
            if (pack == null) {
                c.getPlayer().dropMessage(1, "找不到物品。");
                c.getPlayer().setConversation(0);
            } else if (pack.getItems().size() <= 0) { //error fix for complainers.
                if (!check(c.getPlayer(), pack)) {
                    c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 27));
                    return;
                }
                if (deletePackage(c.getPlayer().getAccountID(), pack.getPackageid(), c.getPlayer().getId())) {
                    c.getPlayer().gainMeso(pack.getMesos(), false);
                    c.sendPacket(PlayerShopPacket.merchItem_Message((byte) 26));
                } else {
                    c.getPlayer().dropMessage(1, "未知的錯誤");
                }
                c.getPlayer().setConversation(0);
            } else {
                c.sendPacket(PlayerShopPacket.merchItemStore_ItemData(pack));
            }
        }
    }

    public static final void MerchantItemStore(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer() == null) {
            return;
        }
        final byte operation = slea.readByte();
        if (operation == 22 || operation == 23) { // Request, Take out
            requestItems(c, operation == 22);
        } else if (operation == 24) { // Exit
            c.getPlayer().setConversation(0);
        }
    }

    private static void requestItems(final MapleClient c, final boolean request) {
        if (c.getPlayer().getConversation() != 3) {
            return;
        }
        boolean merch = World.hasMerchant(c.getPlayer().getAccountID(), c.getPlayer().getId());
        if (merch) {
            // TODO: 改用封包
            c.getPlayer().dropMessage(1, "請關閉商店後在試一次.");
            c.getPlayer().setConversation(0);
            return;
        }
        final MerchItemPackage pack = loadItemFrom_Database(c.getPlayer().getAccountID());
        if (pack == null) {
            c.getPlayer().dropMessage(1, "未知的錯誤。");
            c.getPlayer().setConversation(0);
            return;
        } else if (c.getChannelServer().isShutdown()) {
            c.getPlayer().dropMessage(1, "伺服器關閉中，目前無法操作。");
            c.getPlayer().setConversation(0);
            return;
        } else if (c.getPlayer().getMeso() + pack.getMesos() >= 2147483647) {
            c.getPlayer().dropMessage(1, "您的錢領取過後將會過多，請先將多餘的錢放置倉庫!");
            c.getPlayer().setConversation(0);
            return;
        }
        final int days = StringUtil.getDaysAmount(pack.getSavedTime(), System.currentTimeMillis()); // max 100%
        final double percentage = days / 100.0;
        final int fee = (int) Math.ceil(percentage * pack.getMesos()); // if no mesos = no tax
        if (request && days > 0 && percentage > 0 && pack.getMesos() > 0 && fee > 0) {
            c.sendPacket(PlayerShopPacket.merchItemStore((byte) 32, days, fee));
            return;
        }
        if (fee < 0) { // impossible
            c.sendPacket(PlayerShopPacket.merchItem_Message(27));
            return;
        }
        if (c.getPlayer().getMeso() < fee) {
            c.sendPacket(PlayerShopPacket.merchItem_Message(29));
            return;
        }
        if (!check(c.getPlayer(), pack)) {
            c.sendPacket(PlayerShopPacket.merchItem_Message(30));
            return;
        }
        if (deletePackage(c.getPlayer().getAccountID(), pack.getPackageid(), c.getPlayer().getId())) {
            String output = "";
            if (fee > 0) {
                c.getPlayer().gainMeso(-fee, true);
            }
            c.getPlayer().gainMeso(pack.getMesos(), false);
            for (Item item : pack.getItems()) {
                MapleInventoryManipulator.addFromDrop(c, item, false);
                output += item.getItemId() + "(" + item.getQuantity() + "), ";
            }
            if (ServerConstants.LOG_MERCHANT) {
                FileoutputUtil.logToFile("紀錄/監聽/購買紀錄/精靈商人_領回.txt", FileoutputUtil.CurrentReadable_TimeGMT() + "角色名字:" + c.getPlayer().getName() + " 從精靈商人取回楓幣: " + pack.getMesos() + " 和" + pack.getItems().size() + "件物品[" + output + "]\r\n");
            }
            c.sendPacket(PlayerShopPacket.merchItem_Message(26));
        } else {
            c.getPlayer().dropMessage(1, "未知的錯誤。");
        }
    }

    private static final boolean check(final MapleCharacter chr, final MerchItemPackage pack) {
        if (chr.getMeso() + pack.getMesos() < 0) {
            return false;
        }
        byte eq = 0, use = 0, setup = 0, etc = 0, cash = 0;
        for (Item item : pack.getItems()) {
            final MapleInventoryType invtype = GameConstants.getInventoryType(item.getItemId());
            if (null != invtype) switch (invtype) {
                case EQUIP:
                    eq++;
                    break;
                case USE:
                    use++;
                    break;
                case SETUP:
                    setup++;
                    break;
                case ETC:
                    etc++;
                    break;
                case CASH:
                    cash++;
                    break;
                default:
                    break;
            }
            if (MapleItemInformationProvider.getInstance().isPickupRestricted(item.getItemId()) && chr.haveItem(item.getItemId(), 1)) {
                return false;
            }
        }
//        if (chr.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() < eq || chr.getInventory(MapleInventoryType.USE).getNumFreeSlot() < use || chr.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() < setup || chr.getInventory(MapleInventoryType.ETC).getNumFreeSlot() < etc || chr.getInventory(MapleInventoryType.CASH).getNumFreeSlot() < cash) {
//            return false;
//        }

        boolean slot = true;
        if (chr.getInventory(MapleInventoryType.EQUIP).getNumFreeSlot() <= eq && eq != 0) {
            slot = false;
        }
        if (chr.getInventory(MapleInventoryType.USE).getNumFreeSlot() <= use && use != 0) {
            slot = false;
        }
        if (chr.getInventory(MapleInventoryType.SETUP).getNumFreeSlot() <= setup && setup != 0) {
            slot = false;
        }
        if (chr.getInventory(MapleInventoryType.ETC).getNumFreeSlot() <= etc && etc != 0) {
            slot = false;
        }
        if (chr.getInventory(MapleInventoryType.CASH).getNumFreeSlot() <= cash && cash != 0) {
            slot = false;
        }

        return slot;
    }

    private static final boolean deletePackage(final int accid, final int packageid, final int chrId) {
        final Connection con = DatabaseConnection.getConnection();

        try {
            PreparedStatement ps = con.prepareStatement("DELETE from hiredmerch where accountid = ? OR packageid = ? OR characterid = ?");
            ps.setInt(1, accid);
            ps.setInt(2, packageid);
            ps.setInt(3, chrId);
            ps.executeUpdate();
            ps.close();
            ItemLoader.HIRED_MERCHANT.saveItems(null, packageid);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static final MerchItemPackage loadItemFrom_Database(final int accountid) {
        final Connection con = DatabaseConnection.getConnection();

        try {
            PreparedStatement ps = con.prepareStatement("SELECT * from hiredmerch where accountid = ?");
            ps.setInt(1, accountid);

            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                ps.close();
                rs.close();
                return null;
            }
            final int packageid = rs.getInt("PackageId");

            final MerchItemPackage pack = new MerchItemPackage();
            pack.setPackageid(packageid);
            pack.setMesos(rs.getInt("Mesos"));
            pack.setSavedTime(rs.getLong("time"));

            ps.close();
            rs.close();

            Map<Long, Pair<Item, MapleInventoryType>> items = ItemLoader.HIRED_MERCHANT.loadItems(false, packageid);
            if (items != null) {
                List<Item> iters = new ArrayList<>();
                for (Pair<Item, MapleInventoryType> z : items.values()) {
                    iters.add(z.left);
                }
                pack.setItems(iters);
            }

            return pack;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
