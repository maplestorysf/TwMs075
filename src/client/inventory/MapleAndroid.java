package client.inventory;

import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.Serializable;

import java.util.List;
import database.DatabaseConnection;
import server.MapleItemInformationProvider;
import server.Randomizer;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovement;
import server.movement.LifeMovementFragment;
import tools.Pair;

public class MapleAndroid implements Serializable {

    private static final long serialVersionUID = 9179541993413738569L;
    private int stance = 0, uniqueid, itemid, hair, face;
    private String name;
    private Point pos = new Point(0, 0);
    private boolean changed = false;

    private MapleAndroid(final int itemid, final int uniqueid) {
        this.itemid = itemid;
        this.uniqueid = uniqueid;
    }

    public static final MapleAndroid loadFromDb(final int itemid, final int uid) {
        try {
            final MapleAndroid ret = new MapleAndroid(itemid, uid);

            Connection con = DatabaseConnection.getConnection(); // Get a connection to the database
            PreparedStatement ps = con.prepareStatement("SELECT * FROM androids WHERE uniqueid = ?"); // Get pet details..
            ps.setInt(1, uid);

            final ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                rs.close();
                ps.close();
                return null;
            }

            ret.setHair(rs.getInt("hair"));
            ret.setFace(rs.getInt("face"));
            ret.setName(rs.getString("name"));
            ret.changed = false;

            rs.close();
            ps.close();

            return ret;
        } catch (SQLException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public final void saveToDb() {
        if (!changed) {
            return;
        }
        try {
            final PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("UPDATE androids SET hair = ?, face = ?, name = ? WHERE uniqueid = ?");
            ps.setInt(1, hair);
            ps.setInt(2, face);
            ps.setString(3, name);
            ps.setInt(4, uniqueid); // Set ID
            ps.executeUpdate(); // Execute statement
            ps.close();
            changed = false;
        } catch (final SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static final MapleAndroid create(final int itemid, final int uniqueid) {
        Pair<List<Integer>, List<Integer>> aInfo = MapleItemInformationProvider.getInstance().getAndroidInfo(itemid == 1662006 ? 5 : (itemid - 1661999));
        if (aInfo == null) {
            return null;
        }
        return create(itemid, uniqueid, aInfo.left.get(Randomizer.nextInt(aInfo.left.size())), aInfo.right.get(Randomizer.nextInt(aInfo.right.size())));
    }

    public static final MapleAndroid create(int itemid, int uniqueid, int hair, int face) {
        if (uniqueid <= -1) { //wah
            uniqueid = MapleInventoryIdentifier.getInstance();
        }
        try { // Commit to db first
            PreparedStatement pse = DatabaseConnection.getConnection().prepareStatement("INSERT INTO androids (uniqueid, hair, face, name) VALUES (?, ?, ?, ?)");
            pse.setInt(1, uniqueid);
            pse.setInt(2, hair);
            pse.setInt(3, face);
            pse.setString(4, "Android");
            pse.executeUpdate();
            pse.close();
        } catch (final SQLException ex) {
            ex.printStackTrace();
            return null;
        }
        final MapleAndroid pet = new MapleAndroid(itemid, uniqueid);
        pet.setHair(hair);
        pet.setFace(face);
        pet.setName("Android");

        return pet;
    }

    public int getUniqueId() {
        return uniqueid;
    }

    public final void setHair(final int closeness) {
        this.hair = closeness;
        this.changed = true;
    }

    public final int getHair() {
        return hair;
    }

    public final void setFace(final int closeness) {
        this.face = closeness;
        this.changed = true;
    }

    public final int getFace() {
        return face;
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        this.name = n;
        this.changed = true;
    }

    public final Point getPos() {
        return pos;
    }

    public final void setPos(final Point pos) {
        this.pos = pos;
    }

    public final int getStance() {
        return stance;
    }

    public final void setStance(final int stance) {
        this.stance = stance;
    }

    public final int getItemId() {
        return itemid;
    }

    public final void updatePosition(final List<LifeMovementFragment> movement) {
        for (final LifeMovementFragment move : movement) {
            if (move instanceof LifeMovement) {
                if (move instanceof AbsoluteLifeMovement) {
                    setPos(((LifeMovement) move).getPosition());
                }
                setStance(((LifeMovement) move).getNewstate());
            }
        }
    }
}
