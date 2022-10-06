package handling.login.handler;

import client.LoginCrypto;
import database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import tools.FileoutputUtil;

public class AutoRegister {

    private static final int ACCOUNTS_PER_IP = 2, ACCOUNTS_PER_MAC = ACCOUNTS_PER_IP;
    public static boolean iplimit = false, maclimit = false;

    public static boolean isExistAndLimitMac(String mac) {
        int alreadyTimes = 0;

        try {
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps = con.prepareStatement("SELECT regmac FROM accounts WHERE regmac like ?")) {
                ps.setString(1, "%" + mac + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        alreadyTimes++;
                    }
                }
            }
        } catch (SQLException ex) {
            FileoutputUtil.printError("AutoRegister.txt", "isExistAndLimitMac", ex, "MAC: " + mac);
            System.out.println("檢查MAC" + mac + "發生錯誤:" + ex);
        }
        if (alreadyTimes >= ACCOUNTS_PER_MAC) { //限制每組MAC辦帳號數量
            return true;
        }
        return false;
    }

    public static boolean isExistAndLimitIP(String eip) {
        String sockAddr = "/" + eip.substring(1, eip.lastIndexOf(':'));
        int alreadyTimes = 0;
        try {
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps = con.prepareStatement("SELECT id, name FROM accounts WHERE  regIP = ?")) {// SessionIP = ? or
                ps.setString(1, sockAddr);
                //   ps.setString(2, sockAddr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        alreadyTimes++;
                    }
                }
            }

        } catch (SQLException ex) {
            FileoutputUtil.printError("AutoRegister.txt", "isExistAndLimitIP", ex, "IP: " + eip);
            System.out.println("檢查IP" + sockAddr + "發生錯誤:" + ex);
        }
        if (alreadyTimes >= ACCOUNTS_PER_IP) { //限制每組IP辦帳號數量
            return true;
        }
        return false;
    }

    public static boolean getAccountExists(String login) {
        boolean accountExists = false;
        try {
            Connection con = DatabaseConnection.getConnection();

            try (PreparedStatement ps = con.prepareStatement("SELECT name FROM accounts WHERE name = ?")) {
                ps.setString(1, login);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.first()) {
                        accountExists = true;
                    }
                }
            }
        } catch (SQLException ex) {
            System.out.println(ex);
            FileoutputUtil.printError("AutoRegister.txt", "getAccountExists", ex, "帳號: " + login);
        }
        return accountExists;
    }

    public static boolean createAccount(String login, String pwd, String ipdata, String mac) {
        String sockAddr = ipdata;

        try {
            Connection con = DatabaseConnection.getConnection();

            iplimit = !isExistAndLimitIP(ipdata);
            maclimit = !isExistAndLimitMac(mac);

            if (iplimit && maclimit) {
                Calendar c = Calendar.getInstance();
                int year = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH) + 1;
                int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
                PreparedStatement ps = con.prepareStatement("INSERT INTO accounts (name, password, email, birthday, macs, lastmac, regmac, SessionIP, regIP) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

                ps.setString(1, login);
                ps.setString(2, LoginCrypto.hexSha1(pwd));
                ps.setString(3, "autoregister@mail.com");
                ps.setString(4, year + "-" + month + "-" + dayOfMonth);
                ps.setString(5, mac);
                ps.setString(6, mac);
                ps.setString(7, mac);
                ps.setString(8, "/" + sockAddr.substring(1, sockAddr.lastIndexOf(':')));
                ps.setString(9, "/" + sockAddr.substring(1, sockAddr.lastIndexOf(':')));
                ps.executeUpdate();
                return true;
            }
        } catch (SQLException ex) {
            System.out.println(ex);
            FileoutputUtil.printError("AutoRegister.txt", "createAccount", ex, "帳號: " + login + " 密碼: " + pwd + " IP: " + ipdata + " Mac: " + mac);
        }
        return false;
    }
    /*
    private static final int ACCOUNTS_PER_MAC = 2;
    public static boolean success = false;
    public static boolean mac = true;

    public static boolean getAccountExists(String login) {
        boolean accountExists = false;
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT name FROM accounts WHERE name = ?");
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();
            if (rs.first()) {
                accountExists = true;
            }
        } catch (SQLException ex) {
            System.err.println("[getAccountExists]" + ex);
        }
        return accountExists;
    }

    public static boolean createAccount(String login, String pwd, String eip, String macData) {
        String sockAddr = eip;
        Connection con;

        try {
            con = DatabaseConnection.getConnection();
        } catch (Exception ex) {
            System.err.println("[createAccount]" + ex);
            return false;
        }

        try {
            ResultSet rs;
            try (PreparedStatement ipc = con.prepareStatement("SELECT Macs FROM accounts WHERE macs = ?")) {
                ipc.setString(1, macData);
                rs = ipc.executeQuery();
                if (rs.first() == false || rs.last() == true && rs.getRow() < ACCOUNTS_PER_MAC) {
                    try {
                        try (PreparedStatement ps = con.prepareStatement("INSERT INTO accounts (name, password, email, birthday, macs, SessionIP) VALUES (?, ?, ?, ?, ?, ?)")) {
                            Calendar c = Calendar.getInstance();
                            int year = c.get(Calendar.YEAR);
                            int month = c.get(Calendar.MONTH) + 1;
                            int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
                            ps.setString(1, login);
                            ps.setString(2, LoginCrypto.hexSha1(pwd));
                            ps.setString(3, "autoregister@mail.com");
                            ps.setString(4, year + "-" + month + "-" + dayOfMonth);//Created day
                            ps.setString(5, macData);
                            ps.setString(6, "/" + sockAddr.substring(1, sockAddr.lastIndexOf(':')));
                            ps.executeUpdate();
                        }
                        success = true;
                        return true;
                    } catch (SQLException ex) {
                        System.err.println("createAccount" + ex);
                        return false;
                    }
                }
                if (rs.getRow() >= ACCOUNTS_PER_MAC) {
                    mac = false;
                } else {
                    mac = true;
                    FileoutputUtil.logToFile("Data/註冊帳號.txt", "\r\n 時間　[" + FileoutputUtil.CurrentReadable_TimeGMT() + "] 帳號：　" + login + " 密碼：" + pwd + " IP：/" + sockAddr.substring(1, sockAddr.lastIndexOf(':')) + " MAC： " + macData + " 註冊成功 : " + (mac ? "成功" : "失敗"), false, false);
                }
            }
            rs.close();
        } catch (SQLException ex) {
            System.err.println("[createAccount]" + ex);
            return false;
        }
        return false;
    }*/
}
