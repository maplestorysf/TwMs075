package server;

import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import database.DatabaseConnection;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import tools.StringUtil;

/**
 *
 * @author Emilyx3
 */
public class ServerProperties {

    private static final Properties props = new Properties();

    public static String getPath() {
        return System.getProperty("path", "") + "settings.ini";
    }

    public static void loadProperties() {
        try {
            InputStream in = new FileInputStream(getPath());
            try (BufferedReader bf = new BufferedReader(new InputStreamReader(in, StringUtil.codeString(getPath())))) {
                props.load(bf);
            }
        } catch (IOException ex) {
            System.err.println("讀取\"" + getPath() + "\"檔案失敗 " + ex);
        }
    }

    static {
        loadProperties();
        try {
            try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("SELECT * FROM auth_server_channel_ip"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    props.put(rs.getString("name") + rs.getInt("channelid"), rs.getString("value"));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            System.exit(0); //Big ass error.
        }
    }

    public static void loadProperties(String s) {
        FileReader fr;
        try {
            fr = new FileReader(s);
            props.load(fr);
            fr.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void setProperty(String prop, String newInf) {
        props.setProperty(prop, newInf);
    }

    public static void setProperty(String prop, boolean newInf) {
        props.setProperty(prop, String.valueOf(newInf));
    }

    public static void setProperty(String prop, byte newInf) {
        props.setProperty(prop, String.valueOf(newInf));
    }

    public static void setProperty(String prop, short newInf) {
        props.setProperty(prop, String.valueOf(newInf));
    }

    public static void setProperty(String prop, int newInf) {
        props.setProperty(prop, String.valueOf(newInf));
    }

    public static void setProperty(String prop, long newInf) {
        props.setProperty(prop, String.valueOf(newInf));
    }

    public static void removeProperty(String prop) {
        props.remove(prop);
    }

    public static String getProperty(String s) {
        return props.getProperty(s);
    }

    public static String getProperty(String s, String def) {
        return props.getProperty(s, def);
    }

    public static boolean getProperty(String s, boolean def) {
        return getProperty(s, def ? "true" : "false").equalsIgnoreCase("true");
    }

    public static byte getProperty(String s, byte def) {
        String property = props.getProperty(s);
        if (property != null) {
            return Byte.parseByte(property);
        }
        return def;
    }

    public static short getProperty(String s, short def) {
        String property = props.getProperty(s);
        if (property != null) {
            return Short.parseShort(property);
        }
        return def;
    }

    public static int getProperty(String s, int def) {
        String property = props.getProperty(s);
        if (property != null) {
            return Integer.parseInt(property);
        }
        return def;
    }

    public static long getProperty(String s, long def) {
        String property = props.getProperty(s);
        if (property != null) {
            return Long.parseLong(property);
        }
        return def;
    }
}
