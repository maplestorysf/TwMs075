package handling.channel.handler;

import static client.BuddyList.BuddyOperation.ADDED;
import static client.BuddyList.BuddyOperation.DELETED;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import client.BuddyList;
import client.BuddylistEntry;
import client.CharacterNameAndId;
import client.MapleCharacter;
import client.MapleClient;
import client.BuddyList.BuddyAddResult;
import client.BuddyList.BuddyOperation;
import database.DatabaseConnection;
import handling.channel.ChannelServer;
import handling.world.World;
import tools.data.LittleEndianAccessor;
import tools.packet.CWvsContext.BuddylistPacket;

public class BuddyListHandler {

    private static final class CharacterIdNameBuddyCapacity extends CharacterNameAndId {

        private int buddyCapacity;

        public CharacterIdNameBuddyCapacity(int id, String name, String group, int buddyCapacity) {
            super(id, name, group);
            this.buddyCapacity = buddyCapacity;
        }

        public int getBuddyCapacity() {
            return buddyCapacity;
        }
    }

    private static final CharacterIdNameBuddyCapacity getCharacterIdAndNameFromDatabase(final String name, final String group) throws SQLException {
        Connection con = DatabaseConnection.getConnection();

        PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE name LIKE ?");
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        CharacterIdNameBuddyCapacity ret = null;
        if (rs.next()) {
            if (rs.getInt("gm") < 3) {
                ret = new CharacterIdNameBuddyCapacity(rs.getInt("id"), rs.getString("name"), group, rs.getInt("buddyCapacity"));
            }
        }
        rs.close();
        ps.close();

        return ret;
    }

    public static final void BuddyOperation(final LittleEndianAccessor slea, final MapleClient c) {
        final int mode = slea.readByte();
        final BuddyList buddylist = c.getPlayer().getBuddylist();

        switch (mode) {
            case 1: {
                // add
                final String addName = slea.readMapleAsciiString();
                final String groupName = "";
                final BuddylistEntry ble = buddylist.get(addName);
                if (addName.length() > 13) {
                    return;
                }
                if (ble != null && ble.isVisible()) {
                    c.sendPacket(BuddylistPacket.updateBuddylist(buddylist.getBuddies(), 13));
                } else if (buddylist.isFull()) {
                    c.sendPacket(BuddylistPacket.buddylistMessage((byte) 11));
                } else {
                    try {
                        CharacterIdNameBuddyCapacity charWithId = null;
                        int channel = World.Find.findChannel(addName);
                        MapleCharacter otherChar = null;
                        if (channel > 0) {
                            otherChar = ChannelServer.getInstance(channel).getPlayerStorage().getCharacterByName(addName);
                            if (otherChar == null) {
                                charWithId = getCharacterIdAndNameFromDatabase(addName, groupName);
                            } else if (!otherChar.isIntern() || c.getPlayer().isIntern()) {
                                charWithId = new CharacterIdNameBuddyCapacity(otherChar.getId(), otherChar.getName(), groupName, otherChar.getBuddylist().getCapacity());
                            }
                        } else {
                            charWithId = getCharacterIdAndNameFromDatabase(addName, groupName);
                        }

                        if (charWithId != null) {
                            BuddyAddResult buddyAddResult = null;
                            if (channel > 0) {
                                buddyAddResult = World.Buddy.requestBuddyAdd(addName, c.getChannel(), c.getPlayer().getId(), c.getPlayer().getName(), c.getPlayer().getLevel(), c.getPlayer().getJob());
                            } else {
                                Connection con = DatabaseConnection.getConnection();
                                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) as buddyCount FROM buddies WHERE characterid = ? AND pending = 0");
                                ps.setInt(1, charWithId.getId());
                                ResultSet rs = ps.executeQuery();

                                if (!rs.next()) {
                                    ps.close();
                                    rs.close();
                                    throw new RuntimeException("Result set expected");
                                } else {
                                    int count = rs.getInt("buddyCount");
                                    if (count >= charWithId.getBuddyCapacity()) {
                                        buddyAddResult = BuddyAddResult.BUDDYLIST_FULL;
                                    }
                                }
                                rs.close();
                                ps.close();

                                ps = con.prepareStatement("SELECT pending FROM buddies WHERE characterid = ? AND buddyid = ?");
                                ps.setInt(1, charWithId.getId());
                                ps.setInt(2, c.getPlayer().getId());
                                rs = ps.executeQuery();
                                if (rs.next()) {
                                    buddyAddResult = BuddyAddResult.ALREADY_ON_LIST;
                                }
                                rs.close();
                                ps.close();
                            }
                            if (buddyAddResult == BuddyAddResult.BUDDYLIST_FULL) {
                                c.sendPacket(BuddylistPacket.buddylistMessage((byte) 12));
                            } else {
                                int displayChannel = -1;
                                int otherCid = charWithId.getId();
                                if (buddyAddResult == BuddyAddResult.ALREADY_ON_LIST && channel > 0) {
                                    displayChannel = channel;
                                    notifyRemoteChannel(c, channel, otherCid, groupName, ADDED);
                                } else if (buddyAddResult != BuddyAddResult.ALREADY_ON_LIST) {
                                    Connection con = DatabaseConnection.getConnection();
                                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO buddies (`characterid`, `buddyid`, `groupname`, `pending`) VALUES (?, ?, ?, 1)")) {
                                        ps.setInt(1, charWithId.getId());
                                        ps.setInt(2, c.getPlayer().getId());
                                        ps.setString(3, groupName);
                                        ps.executeUpdate();
                                    }
                                }
                                buddylist.put(new BuddylistEntry(charWithId.getName(), otherCid, groupName, displayChannel, true));
                                c.sendPacket(BuddylistPacket.updateBuddylist(buddylist.getBuddies(), 10));
                            }
                        } else {
                            c.sendPacket(BuddylistPacket.buddylistMessage((byte) 15));
                        }
                    } catch (SQLException e) {
                        System.err.println("SQL THROW" + e);
                    }
                }
                break;
            }
            case 2: {
                // accept buddy
                int otherCid = slea.readInt();
                final BuddylistEntry ble = buddylist.get(otherCid);
                if (!buddylist.isFull() && ble != null && !ble.isVisible()) {
                    final int channel = World.Find.findChannel(otherCid);
                    buddylist.put(new BuddylistEntry(ble.getName(), otherCid, "", channel, true));
                    c.sendPacket(BuddylistPacket.updateBuddylist(buddylist.getBuddies(), 10));
                    notifyRemoteChannel(c, channel, otherCid, "", ADDED);
                } else {
                    c.sendPacket(BuddylistPacket.buddylistMessage((byte) 11));
                }
                break;
            }
            case 3: {
                // delete
                final int otherCid = slea.readInt();
                final BuddylistEntry blz = buddylist.get(otherCid);
                if (blz != null && blz.isVisible()) {
                    notifyRemoteChannel(c, World.Find.findChannel(otherCid), otherCid, blz.getGroup(), DELETED);
                }
                buddylist.remove(otherCid);
                c.sendPacket(BuddylistPacket.updateBuddylist(buddylist.getBuddies(), 18));
                break;
            }
            default:
                break;
        }
    }

    private static final void notifyRemoteChannel(final MapleClient c, final int remoteChannel, final int otherCid, final String group, final BuddyOperation operation) {
        final MapleCharacter player = c.getPlayer();

        if (remoteChannel > 0) {
            World.Buddy.buddyChanged(otherCid, player.getId(), player.getName(), c.getChannel(), operation, group);
        }
    }
}
