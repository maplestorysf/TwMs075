package handling.login.handler;

import java.util.List;
import java.util.Calendar;

import client.inventory.Item;
import client.MapleClient;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import constants.ServerConstants;
import constants.Types.MonitorType;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginInformationProvider;
import handling.login.LoginServer;
import handling.login.LoginWorker;
import handling.world.World;
import java.sql.Timestamp;
import server.MapleItemInformationProvider;
import tools.FileoutputUtil;
import tools.KoreanDateUtil;
import tools.StringUtil;
import tools.packet.CField;
import tools.packet.LoginPacket;
import tools.data.LittleEndianAccessor;
import tools.packet.CWvsContext;

public class CharLoginHandler {

    public static final byte 空白 = 1,
            帳號GASH正常狀態 = 3,
            密碼錯誤 = 4,
            無此帳號 = 5,
            帳號使用中 = 7;

    private static final boolean loginFailCount(final MapleClient c) {
        c.loginAttempt++;
        if (c.loginAttempt > 5) {
            return true;
        }
        return false;
    }

    public static final void setGenderRequest(final LittleEndianAccessor slea, final MapleClient c) {
        String username = slea.readMapleAsciiString();
        String password = slea.readMapleAsciiString();
        if (c.getAccountName().equals(username)) {
            c.setGender(slea.readByte());
            c.setSecondPassword(password);
            c.update2ndPassword();
            c.updateGender();
            c.sendPacket(LoginPacket.getGenderChanged());
            c.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, c.getSessionIPAddress());
        } else {
            c.getSession().close();
        }
    }

    public static final void handleLoginAccount(final LittleEndianAccessor slea, final MapleClient c) {
        final String acc = slea.readMapleAsciiString();
        final String pwd = slea.readMapleAsciiString();

        String mac = readMacAddress(slea, c);

        c.setMacs(mac);
        c.setClientMac(mac);
        c.setAccountName(acc);

        final boolean ipBan = c.hasBannedIP();
        final boolean macBan = c.hasBannedMac();
        final boolean ban = ipBan || macBan;
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        int loginok = c.login(acc, pwd, ban);
        String info = null;

        final Calendar tempbannedTill = c.getTempBanCalendar();

        if (loginok == 無此帳號) {
            if (ban) {
                info = "由於您使用不法程式的紀錄，無法註冊新的帳號。";
                loginok = 空白;
            } else {
                if (ServerConstants.AUTO_REGISTER) {
                    if (AutoRegister.getAccountExists(acc)) {
                        info = "無法使用將[" + acc + "]當作您的帳號.";
                    } else if (acc.length() > 11) {
                        info = "您的帳號[" + acc + "]過長，請更換一組帳號.";
                    } else if (pwd.equalsIgnoreCase(GameConstants.HackPW) || GameConstants.banpw.contains(pwd)) {
                        info = "無法使用將[" + pwd + "]當作您的密碼.";
                    } else {
                        boolean success = AutoRegister.createAccount(acc, pwd, c.getSession().getRemoteAddress().toString(), c.getClientMac());
                        if (success) {
                            info = "帳號已經註冊成功\r\n 帳號: [" + acc + "]\r\n 密碼: [" + pwd + "]";
                            // if (GameSetConstants.Log_Ac) {
                            FileoutputUtil.logToFile("紀錄/系統/註冊帳號.txt", FileoutputUtil.CurrentReadable_Time() + " IP:" + c.getSessionIPAddress() + " MAC:" + c.getClientMac() + " 帳號: <" + acc + "> 密碼: <" + pwd + ">");
                            //}
                            World.Broadcast.broadcastGMMessageS(CWvsContext.serverNotice(6, "[GM監聽] <註冊> IP:" + c.getSessionIPAddress() + " MAC:" + c.getClientMac() + " 帳號: <" + acc + "> 密碼: <" + pwd + ">"), MonitorType.帳號註冊訊息);
                        } else {
                            info = "註冊的帳號數量已達上限，無法註冊新的帳號。";
                        }
                    }
                    loginok = 空白;
                }
            }
        } else if (loginok == 0) {
            if (World.isPlayerSaving(c.getAccID())) {
                info = "系統忙碌中，請稍後再試。";
                loginok = 空白;
            }
        }

        if (loginok == 空白 && ban && !c.isGm()) {// 非GM帳號的封鎖IP/MAC登入成功
            FileoutputUtil.logToFile("封鎖/登入/" + (macBan ? "MAC" : "IP") + "封鎖_登入帳號.txt", "\r\n " + FileoutputUtil.CurrentReadable_Time() + "  目前MAC:" + mac + " 所有MAC: " + c.getMacs() + " IP地址: " + c.getSession().getRemoteAddress().toString().split(":")[0] + " 帳號: " + acc + " 密碼: " + pwd);
            loginok = 帳號GASH正常狀態;
        }

        if (loginok != 0) {
            if (!loginFailCount(c)) {
                c.sendPacket(LoginPacket.getLoginFailed(loginok));
                if (info != null) {
                    c.sendPacket(CWvsContext.serverNotice(1, info));
                }
            } else {
                c.getSession().close();
            }
        } else if (tempbannedTill.getTimeInMillis() != 0) {
            if (!loginFailCount(c)) {
                c.sendPacket(LoginPacket.getTempBan(KoreanDateUtil.getTempBanTimestamp(tempbannedTill.getTimeInMillis()), c.getBanReason()));
            } else {
                c.getSession().close();

            }
        } else {
            try {
//                c.getLoginLock().lock();// TODO: 利用LoginSever為對象來鎖
                //c.sleep(1000);
                c.updateMacs(mac);
                LoginWorker.registerClient(c);
                World.Broadcast.broadcastGMMessageS(CWvsContext.serverNotice(6, "[GM監聽] <登入> IP:" + c.getSessionIPAddress() + " MAC:" + c.getClientMac() + " 帳號: <" + acc + "> 密碼: <" + pwd + ">"), MonitorType.帳號登入訊息);
            } catch (Exception ex) {
                FileoutputUtil.outputFileError(FileoutputUtil.Login_Error, ex);
            }
//            finally {
//                c.getLoginLock().unlock();
//            }

        }
    }

    /*
    public static final void login(final LittleEndianAccessor slea, final MapleClient c) {
        String username = slea.readMapleAsciiString();
        String password = slea.readMapleAsciiString();
        int[] bytes = new int[6];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = slea.readByteAsInt();
        }
        StringBuilder sps = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sps.append(StringUtil.getLeftPaddedStr(Integer.toHexString(bytes[i]).toUpperCase(), '0', 2));
            sps.append("-");
        }
        String macData = sps.toString();
        macData = macData.substring(0, macData.length() - 1);
        c.setMacs(macData);
        c.setLoginMacs(macData);
        final boolean ipBan = c.hasBannedIP();
        final boolean macBan = c.hasBannedMac();

        if (ServerConstants.AUTO_REGISTER && !AutoRegister.getAccountExists(username) && (!c.hasBannedIP() || !c.hasBannedMac())) {
            if (username.length() > 13) {
                c.sendPacket(CWvsContext.serverNotice(1, "帳號太長，請重新輸入。"));
                c.sendPacket(LoginPacket.getLoginFailed(1));
                return;
            }
            if (password.equalsIgnoreCase("disconnect") || password.equalsIgnoreCase("fixlogged")) {
                c.sendPacket(CWvsContext.serverNotice(1, "密碼無效，請重新輸入。"));
                c.sendPacket(LoginPacket.getLoginFailed(1));
                return;
            }
            
            AutoRegister.createAccount(username, password, c.getSession().getRemoteAddress().toString(), macData);

            if (AutoRegister.success && AutoRegister.mac) {
                c.sendPacket(CWvsContext.serverNotice(1, "帳號註冊成功！\r\n請重新登入遊戲。\r\n帳號：" + username + "\r\n密碼：" + password));
                c.sendPacket(LoginPacket.getLoginFailed(1));
                return;
            } else if (!AutoRegister.mac) {
                c.sendPacket(CWvsContext.serverNotice(1, "無法註冊過多的帳號密碼。"));
                c.sendPacket(LoginPacket.getLoginFailed(1));
                AutoRegister.success = false;
                AutoRegister.mac = true;
                return;
            }
        }

        int loginok = c.login(username, password, ipBan || macBan);
        final Calendar tempbannedTill = c.getTempBanCalendar();

        if ((loginok == 0 || loginok == 3) && (ipBan || macBan) && !c.isGm()) {
            // 當被封鎖的玩家，登入帳號非GM的處理
            FileoutputUtil.logToFile("data/" + (macBan ? "MAC" : "IP") + "封鎖_登入帳號.txt", "\r\n 時間　[" + FileoutputUtil.CurrentReadable_TimeGMT() + "]  目前MAC位址: " + macData + " 所有MAC位址: " + c.getMacs() + " IP地址: " + c.getSessionIPAddress().split(":")[0] + " 帳號: " + username + " 密碼：" + password + " " + (loginok > 0 ? "(封鎖的帳號)" : ""));

            loginok = 3;
            //           if (macBan) {
//                // this is only an ipban o.O" - maybe we should refactor this a bit so it's more readable
//                MapleCharacter.ban(c.getSession().getRemoteAddress().toString().split(":")[0], "Enforcing account ban, account " + username, false, 4, false);
//            }
        }

        // ↓if (loginok == 7)：自動解卡(+踢除在線角色(含購物商城內)，防止玩家洗道具)
        if (loginok == 7) {
            for (MapleCharacter chr : c.loadCharacters(0)) {
                for (ChannelServer cs : ChannelServer.getAllInstances()) {
                    MapleCharacter victim = cs.getPlayerStorage().getCharacterById(chr.getId());
                    if (victim != null) {
                        victim.getClient().getSession().close();
                        victim.getClient().disconnect(true, false);
                    }
                }
                MapleCharacter victim = CashShopServer.getPlayerStorage().getCharacterById(chr.getId());
                if (victim != null) {
                    victim.getClient().getSession().close();
                    victim.getClient().disconnect(true, false);
                }
            }
            c.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, c.getSessionIPAddress());
            //c.sendPacket(CWvsContext.serverNotice(1, "解卡成功，請重新登入。"));
            //c.sendPacket(LoginPacket.getLoginFailed(1)); //Shows no message, used for unstuck the login button
            loginok = c.login(username, password, ipBan || macBan);
        }
        // ↑if (loginok == 7)：自動解卡(+踢除在線角色(含購物商城)，防止玩家洗道具)

        if (loginok != 0) {
            if (!loginFailCount(c)) {
                c.clearInformation();
                c.sendPacket(LoginPacket.getLoginFailed(loginok));
            } else {
                c.getSession().close();
            }
        } else if (tempbannedTill.getTimeInMillis() != 0) {
            if (!loginFailCount(c)) {
                c.clearInformation();
                c.sendPacket(LoginPacket.getTempBan(PacketHelper.getTime(tempbannedTill.getTimeInMillis()), c.getBanReason()));
            } else {
                c.getSession().close();
            }
        } else {
            c.loginAttempt = 0;
            c.updateMacs(macData);
            FileoutputUtil.logToFile("data/登入帳號.txt", "\r\n" + FileoutputUtil.CurrentReadable_TimeGMT() + " MAC 地址: " + c.getLoginMacs() + "  IP 地址 : " + c.getSessionIPAddress().split(":")[0] + " 帳號: " + username + " 密碼: " + password);// 
            LoginWorker.registerClient(c);
        }
    }
     */
    public static final void ServerListRequest(final MapleClient c) {
        LoginServer.forceRemoveClient(c, false);
        ChannelServer.forceRemovePlayerByCharNameFromDataBase(c, MapleClient.loadCharacterNamesByAccId(c.getAccID()));
        c.sendPacket(LoginPacket.getServerList(0, LoginServer.getLoad()));
        c.sendPacket(LoginPacket.getEndOfServerList());
    }

    public static final void ServerStatusRequest(final MapleClient c) {
        // 0 = Select world normally
        // 1 = "Since there are many users, you may encounter some..."
        // 2 = "The concurrent users in this world have reached the max"
        final int numPlayer = LoginServer.getUsersOn();
        final int userLimit = LoginServer.getUserLimit();
        if (numPlayer >= userLimit) {
            c.sendPacket(LoginPacket.getServerStatus(2));
        } else if (numPlayer * 2 >= userLimit) {
            c.sendPacket(LoginPacket.getServerStatus(1));
        } else {
            c.sendPacket(LoginPacket.getServerStatus(0));
        }
    }

    public static final void CharlistRequest(final LittleEndianAccessor slea, final MapleClient c) {
        if (!c.isLoggedIn()) {
            c.getSession().close();
            return;
        }
        LoginServer.forceRemoveClient(c, false);
        ChannelServer.forceRemovePlayerByCharNameFromDataBase(c, MapleClient.loadCharacterNamesByAccId(c.getAccID()));

        slea.readByte();
        final int server = slea.readByte();
        final int channel = slea.readByte() + 1;
        if (!World.isChannelAvailable(channel) || server != 0) { //TODOO: MULTI WORLDS
            c.sendPacket(LoginPacket.getLoginFailed(10)); //cannot process so many
            return;
        }

        //System.out.println("Client " + c.getSession().getRemoteAddress().toString().split(":")[0] + " is connecting to server " + server + " channel " + channel + "");
        final List<MapleCharacter> chars = c.loadCharacters(server);
        if (chars != null && ChannelServer.getInstance(channel) != null) {
            c.setWorld(server);
            c.setChannel(channel);
            c.sendPacket(LoginPacket.getCharList(c.getSecondPassword(), chars, c.getCharacterSlots()));
        } else {
            c.getSession().close();
        }
    }

    public static final void CheckCharName(final String name, final MapleClient c) {
        c.sendPacket(LoginPacket.charNameResponse(name,
                !(MapleCharacterUtil.canCreateChar(name, c.isGm()) && (!LoginInformationProvider.getInstance().isForbiddenName(name) || c.isGm()))));
    }

    public static final void CreateChar(final LittleEndianAccessor slea, final MapleClient c) {
        // 防止假客戶端
        if (!c.isLoggedIn()) {
            c.getSession().close();
            return;
        }

        final String name = slea.readMapleAsciiString();
        final int face = slea.readInt();
        final int hair = slea.readInt();
        final int top = slea.readInt();
        final int bottom = slea.readInt();
        final int shoes = slea.readInt();
        final int weapon = slea.readInt();
        final int str = slea.readByte();
        final int dex = slea.readByte();
        final int int_ = slea.readByte();
        final int luk = slea.readByte();

        StringBuilder sb = new StringBuilder();
        final byte gender = c.getGender();
        byte skinColor = 0;
        int hairColor = 0;
        int type = 0;
        int[] CreateCharItem = {face, hair, top, bottom, shoes, weapon};

        // 封包檢測區
        for (int i = 0; i < 6; i++) {
            if (!LoginInformationProvider.getInstance().isEligibleItem(gender, i, type, CreateCharItem[i])) {
                sb.append("裝備種類: ").append(CreateCharItem[i]).append(", ");
            }
        }
        if (str + dex + int_ + luk != 25) {
            sb.append("力量: ").append(str).append(" 敏捷: ").append(dex).append(" 智力: ").append(int_).append(" 幸運: ").append(luk);
        }

        if (sb.length() > 0) {
            FileoutputUtil.logToFile("外掛/封包異常.txt", "\r\n" + FileoutputUtil.CurrentReadable_TimeGMT() + " MAC: " + c.getLoginMacs() + " IP: " + c.getSessionIPAddress() + " 帳號: " + c.getAccountName() + " 角色: " + name, false, false);
            return;
        }

        MapleCharacter newchar = MapleCharacter.getDefault(c);
        newchar.setWorld((byte) c.getWorld());
        newchar.setName(name);
        newchar.setFace(face);
        newchar.setHair(hair + hairColor);
        newchar.getStat().str = (byte) str;
        newchar.getStat().dex = (byte) dex;
        newchar.getStat().int_ = (byte) int_;
        newchar.getStat().luk = (byte) luk;
        newchar.setGender(gender);
        newchar.setSkinColor(skinColor);

        final MapleItemInformationProvider li = MapleItemInformationProvider.getInstance();
        final MapleInventory equip = newchar.getInventory(MapleInventoryType.EQUIPPED);

        Item item = li.getEquipById(top);
        item.setPosition((byte) -5);
        equip.addFromDB(item);

        if (bottom > 0) { //resistance have overall
            item = li.getEquipById(bottom);
            item.setPosition((byte) -6);
            equip.addFromDB(item);
        }

        item = li.getEquipById(shoes);
        item.setPosition((byte) -7);
        equip.addFromDB(item);

        item = li.getEquipById(weapon);
        item.setPosition((byte) -11);
        equip.addFromDB(item);

        newchar.getInventory(MapleInventoryType.ETC).addItem(new Item(4161001, (byte) 0, (short) 1, (byte) 0));

        if (MapleCharacterUtil.canCreateChar(name, c.isGm()) && (!LoginInformationProvider.getInstance().isForbiddenName(name) || c.isGm()) && (c.isGm() || c.canMakeCharacter(c.getWorld()))) {
            MapleCharacter.saveNewCharToDB(newchar);
            c.sendPacket(LoginPacket.addNewCharEntry(newchar, true));
            c.createdChar(newchar.getId());
        } else {
            c.sendPacket(LoginPacket.addNewCharEntry(newchar, false));
        }
    }

    public static final void DeleteChar(final LittleEndianAccessor slea, final MapleClient c) {
        slea.readByte();
        String Secondpw_Client = slea.readMapleAsciiString();
        final int Character_ID = slea.readInt();

        if (!c.login_Auth(Character_ID) || !c.isLoggedIn() || loginFailCount(c)) {
            c.getSession().close();
            return; // Attempting to delete other character
        }
        byte state = 0;

        if (c.getSecondPassword() != null) { // On the server, there's a second password
            if (Secondpw_Client == null) { // Client's hacking
                c.getSession().close();
                return;
            } else if (!c.check2ndPassword(Secondpw_Client)) { // Wrong Password
                state = 0x10;
            }
        }

        if (state == 0) {
            state = (byte) c.deleteCharacter(Character_ID);
        }
        c.sendPacket(LoginPacket.deleteCharResponse(Character_ID, state));
    }

    public static final void Character_WithoutSecondPassword(final LittleEndianAccessor slea, final MapleClient c, final boolean haspic, final boolean view) {
        final int charId = slea.readInt();
        if (!c.isLoggedIn() || loginFailCount(c) || c.getSecondPassword() == null || !c.login_Auth(charId) || ChannelServer.getInstance(c.getChannel()) == null || c.getWorld() != 0) { // TODOO: MULTI WORLDS
            c.getSession().close();
            return;
        }
        if (c.getIdleTask() != null) {
            c.getIdleTask().cancel(true);
        }
        final String s = c.getSessionIPAddress();
        LoginServer.putLoginAuth(charId, s.substring(s.indexOf('/') + 1, s.length()), c.getTempIP());
        c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, s);
        c.sendPacket(CField.getServerIP(c, Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
    }

    public static final void Character_WithSecondPassword(final LittleEndianAccessor slea, final MapleClient c, final boolean view) {
        final String password = slea.readMapleAsciiString();
        final int charId = slea.readInt();
        if (view) {
            c.setChannel(1);
            c.setWorld(slea.readInt());
        }
        if (!c.isLoggedIn() || loginFailCount(c) || c.getSecondPassword() == null || !c.login_Auth(charId) || ChannelServer.getInstance(c.getChannel()) == null || c.getWorld() != 0) { // TODOO: MULTI WORLDS
            c.getSession().close();
            return;
        }
//        c.updateMacs(slea.readMapleAsciiString());
        if (c.CheckSecondPassword(password) && password.length() >= 6 && password.length() <= 16) {
            if (c.getIdleTask() != null) {
                c.getIdleTask().cancel(true);
            }
            final String s = c.getSessionIPAddress();
            LoginServer.putLoginAuth(charId, s.substring(s.indexOf('/') + 1, s.length()), c.getTempIP());
            c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, s);
            c.sendPacket(CField.getServerIP(c, Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
        } else {
            c.sendPacket(LoginPacket.secondPwError((byte) 0x14));
        }
    }

    private static String readMacAddress(final LittleEndianAccessor slea, final MapleClient c) {
        int[] bytes = new int[6];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = slea.readByteAsInt();
        }
        StringBuilder sps = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sps.append(StringUtil.getLeftPaddedStr(Integer.toHexString(bytes[i]).toUpperCase(), '0', 2));
            sps.append("-");
        }
        return sps.toString().substring(0, sps.toString().length() - 1);
    }

}
