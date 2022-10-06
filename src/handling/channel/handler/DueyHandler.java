package handling.channel.handler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import client.inventory.Item;
import constants.GameConstants;
import client.inventory.ItemLoader;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.MapleClient;
import client.inventory.ItemFlag;
import client.inventory.MapleInventoryType;
import database.DatabaseConnection;
import handling.channel.ChannelServer;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Map;
import tools.data.LittleEndianAccessor;
import server.MapleDueyActions;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import tools.Pair;
import tools.packet.CField;
import tools.packet.CWvsContext;

public class DueyHandler {

    /*
     * 19 = Successful
     * 18 = One-of-a-kind Item is already in Reciever's delivery
     * 17 = The Character is unable to recieve the parcel
     * 15 = Same account
     * 14 = Name does not exist
     */
    public static final void DueyOperation(final LittleEndianAccessor slea, final MapleClient c) {
//        final byte operation = slea.readByte();
//
//        switch (operation) {
//            case 1: { // Start Duey, 13 digit AS
//                final String AS13Digit = slea.readMapleAsciiString();
//                //		int unk = slea.readInt(); // Theres an int here, value = 1
//                //  9 = error
//                final int conv = c.getPlayer().getConversation();
//
//                if (conv == 2) { // Duey
//                    c.sendPacket(CField.sendDuey((byte) 10, loadItems(c.getPlayer())));
//                }
//                break;
//            }
//            case 3: { // Send Item
//                if (c.getPlayer().getConversation() != 2) {
//                    return;
//                }
//                final byte inventId = slea.readByte();
//                final short itemPos = slea.readShort();
//                final short amount = slea.readShort();
//                final int mesos = slea.readInt();
//                final String recipient = slea.readMapleAsciiString();
//                boolean quickdelivery = slea.readByte() > 0;
//
//                final int finalcost = mesos + GameConstants.getTaxAmount(mesos) + (quickdelivery ? 0 : 5000);
//
//                if (mesos >= 0 && mesos <= 100000000 && c.getPlayer().getMeso() >= finalcost) {
//                    final int accid = MapleCharacterUtil.getIdByName(recipient);
//                    if (accid != -1) {
//                        if (accid != c.getAccID()) {
//                            boolean recipientOn = false;
//                            MapleClient rClient = null;
//                            try {
//                                int channel = c.getChannelServer().getWorldInterface().find(recipient);
//                                if (channel > -1) {
//                                    recipientOn = true;
//                                    ChannelServer rcserv = ChannelServer.getInstance(channel);
//                                    rClient = rcserv.getPlayerStorage().getCharacterByName(recipient).getClient();
//                                }
//                            } catch (RemoteException re) {
//                                c.getChannelServer().reconnectWorld();
//                            }
//
//                            if (inventId > 0) {
//                                final MapleInventoryType inv = MapleInventoryType.getByType(inventId);
//                                final Item item = c.getPlayer().getInventory(inv).getItem((byte) itemPos);
//                                if (item == null) {
//                                    c.sendPacket(CField.sendDuey((byte) 17, null)); // Unsuccessfull
//                                    return;
//                                }
//                                final byte flag = item.getFlag();
//                                if (ItemFlag.UNTRADEABLE.check(flag) || ItemFlag.LOCK.check(flag)) {
//                                    c.sendPacket(CWvsContext.enableActions());
//                                    return;
//                                }
//                                if (c.getPlayer().getItemQuantity(item.getItemId(), false) >= amount) {
//                                    final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
//                                    if (!ii.isDropRestricted(item.getItemId()) && !ii.isAccountShared(item.getItemId())) {
//                                        if (addItemToDB(item, amount, mesos, c.getPlayer().getName(), accid, recipientOn)) {
//                                            if (GameConstants.isThrowingStar(item.getItemId()) || GameConstants.isBullet(item.getItemId())) {
//                                                MapleInventoryManipulator.removeFromSlot(c, inv, (byte) itemPos, item.getQuantity(), true);
//                                            } else {
//                                                MapleInventoryManipulator.removeFromSlot(c, inv, (byte) itemPos, amount, true, false);
//                                            }
//                                            c.getPlayer().gainMeso(-finalcost, false);
//                                            c.sendPacket(CField.sendDuey((byte) 19, null)); // Successfull
//                                        } else {
//                                            c.sendPacket(CField.sendDuey((byte) 17, null)); // Unsuccessful
//                                        }
//                                    } else {
//                                        c.sendPacket(CField.sendDuey((byte) 17, null)); // Unsuccessfull
//                                    }
//                                } else {
//                                    c.sendPacket(CField.sendDuey((byte) 17, null)); // Unsuccessfull
//                                }
//                            } else if (addMesoToDB(mesos, c.getPlayer().getName(), accid, recipientOn)) {
//                                c.getPlayer().gainMeso(-finalcost, false);
//
//                                c.sendPacket(CField.sendDuey((byte) 19, null)); // Successfull
//                            } else {
//                                c.sendPacket(CField.sendDuey((byte) 17, null)); // Unsuccessfull
//                            }
//                            //                            if (recipientOn && rClient != null) {
//                            //                              rClient.sendPacket(CField.sendDueyMSG(Actions.PACKAGE_MSG.getCode()));
//                            //                        }
//                        } else {
//                            c.sendPacket(CField.sendDuey((byte) 15, null)); // Same acc error
//                        }
//                    } else {
//                        c.sendPacket(CField.sendDuey((byte) 14, null)); // Name does not exist
//                    }
//                } else {
//                    c.sendPacket(CField.sendDuey((byte) 12, null)); // Not enough mesos
//                }
//                break;
//            }
//            case 5: { // Recieve Package
//                if (c.getPlayer().getConversation() != 2) {
//                    return;
//                }
//                final int packageid = slea.readInt();
//                //System.out.println("Item attempted : " + packageid);
//                final MapleDueyActions dp = loadSingleItem(packageid, c.getPlayer().getId());
//                if (dp == null) {
//                    return;
//                }
//                if (dp.getItem() != null && !MapleInventoryManipulator.checkSpace(c, dp.getItem().getItemId(), dp.getItem().getQuantity(), dp.getItem().getOwner())) {
//                    c.sendPacket(CField.sendDuey((byte) 16, null)); // Not enough Space
//                    return;
//                } else if (dp.getMesos() < 0 || (dp.getMesos() + c.getPlayer().getMeso()) < 0) {
//                    c.sendPacket(CField.sendDuey((byte) 17, null)); // Unsuccessfull
//                    return;
//                }
//                removeItemFromDB(packageid, c.getPlayer().getId()); // Remove first
//                //System.out.println("Item removed : " + packageid);
//                if (dp.getItem() != null) {
//                    MapleInventoryManipulator.addFromDrop(c, dp.getItem(), false);
//                }
//                if (dp.getMesos() != 0) {
//                    c.getPlayer().gainMeso(dp.getMesos(), false);
//                }
//                c.sendPacket(CField.removeItemFromDuey(false, packageid));
//                break;
//            }
//            case 6: { // Remove package
//                if (c.getPlayer().getConversation() != 2) {
//                    return;
//                }
//                final int packageid = slea.readInt();
//                removeItemFromDB(packageid, c.getPlayer().getId());
//                c.sendPacket(CField.removeItemFromDuey(true, packageid));
//                break;
//            }
//            case 8: { // Close Duey
//                c.getPlayer().setConversation(0);
//                break;
//            }
//            default: {
//                System.out.println("Unhandled Duey operation : " + slea.toString());
//                break;
//            }
//        }
    }

    private static final boolean addMesoToDB(final int mesos, final String sName, final int recipientID, final boolean isOn) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO dueypackages (RecieverId, SenderName, Mesos, TimeStamp, Checked, Type) VALUES (?, ?, ?, ?, ?, ?)");
            ps.setInt(1, recipientID);
            ps.setString(2, sName);
            ps.setInt(3, mesos);
            ps.setLong(4, System.currentTimeMillis());
            ps.setInt(5, isOn ? 0 : 1);
            ps.setInt(6, 3);

            ps.executeUpdate();
            ps.close();

            return true;
        } catch (SQLException se) {
            se.printStackTrace();
            return false;
        }
    }

    private static final boolean addItemToDB(final Item item, final int quantity, final int mesos, final String sName, final int recipientID, final boolean isOn) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO dueypackages (RecieverId, SenderName, Mesos, TimeStamp, Checked, Type) VALUES (?, ?, ?, ?, ?, ?)", DatabaseConnection.RETURN_GENERATED_KEYS);
            ps.setInt(1, recipientID);
            ps.setString(2, sName);
            ps.setInt(3, mesos);
            ps.setLong(4, System.currentTimeMillis());
            ps.setInt(5, isOn ? 0 : 1);

            ps.setInt(6, item.getType());
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                Item it = item;
                it.setQuantity((short) quantity);
                ItemLoader.DUEY.saveItems(Collections.singletonList(new Pair<Item, MapleInventoryType>(it, GameConstants.getInventoryType(it.getItemId()))), rs.getInt(1));
            }
            rs.close();
            ps.close();

            return true;
        } catch (SQLException se) {
            se.printStackTrace();
            return false;
        }
    }

    public static final List<MapleDueyActions> loadItems(final MapleCharacter chr) {
        List<MapleDueyActions> packages = new LinkedList<>();
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT * FROM dueypackages WHERE RecieverId = ?");
            ps.setInt(1, chr.getId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MapleDueyActions dueypack = getItemByPID(rs.getInt("packageid"));
                dueypack.setSender(rs.getString("SenderName"));
                dueypack.setMesos(rs.getInt("Mesos"));
                dueypack.setSentTime(rs.getLong("TimeStamp"));
                packages.add(dueypack);
            }
            rs.close();
            ps.close();
            return packages;
        } catch (SQLException se) {
            se.printStackTrace();
            return null;
        }
    }

    public static final MapleDueyActions loadSingleItem(final int packageid, final int charid) {
        List<MapleDueyActions> packages = new LinkedList<>();
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT * FROM dueypackages WHERE PackageId = ? and RecieverId = ?");
            ps.setInt(1, packageid);
            ps.setInt(2, charid);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                MapleDueyActions dueypack = getItemByPID(packageid);
                dueypack.setSender(rs.getString("SenderName"));
                dueypack.setMesos(rs.getInt("Mesos"));
                dueypack.setSentTime(rs.getLong("TimeStamp"));
                packages.add(dueypack);
                rs.close();
                ps.close();
                return dueypack;
            } else {
                rs.close();
                ps.close();
                return null;
            }
        } catch (SQLException se) {
//	    se.printStackTrace();
            return null;
        }
    }

    public static final void reciveMsg(final MapleClient c, final int recipientId) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE dueypackages SET Checked = 0 where RecieverId = ?");
            ps.setInt(1, recipientId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
    }

    private static final void removeItemFromDB(final int packageid, final int charid) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("DELETE FROM dueypackages WHERE PackageId = ? and RecieverId = ?");
            ps.setInt(1, packageid);
            ps.setInt(2, charid);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
    }

    private static final MapleDueyActions getItemByPID(final int packageid) {
        try {
            Map<Long, Pair<Item, MapleInventoryType>> iter = ItemLoader.DUEY.loadItems(false, packageid);
            if (iter != null && iter.size() > 0) {
                for (Pair<Item, MapleInventoryType> i : iter.values()) {
                    return new MapleDueyActions(packageid, i.getLeft());
                }
            }
        } catch (Exception se) {
            se.printStackTrace();
        }
        return new MapleDueyActions(packageid);
    }
}
