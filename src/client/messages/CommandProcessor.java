/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package client.messages;

import java.util.ArrayList;
import client.MapleCharacter;
import client.MapleClient;
import client.messages.commands.*;
import constants.ServerConstants;
import constants.ServerConstants.CommandType;
import constants.ServerConstants.PlayerGMRank;
import database.DatabaseConnection;
import handling.world.World;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import tools.FileoutputUtil;
import tools.packet.CWvsContext;

public class CommandProcessor {

    private final static HashMap<String, CommandObject> commands = new HashMap<>();
    private final static HashMap<Integer, ArrayList<String>> NormalCommandList = new HashMap<>();

    static {
        DoNormalCommand();

    }

    /**
     *
     * @param c MapleClient
     * @param type 0 = NormalCommand 1 = VipCommand
     */
    public static void dropHelp(MapleClient c, int type) {
        final StringBuilder sb = new StringBuilder("指令列表:\r\n ");
        HashMap<Integer, ArrayList<String>> commandList = new HashMap<>();
        int check = 0;
        if (type == 0) {
            commandList = NormalCommandList;
            check = c.getPlayer().getGMLevel();
        }
        for (int i = 0; i <= check; i++) {
            if (commandList.containsKey(i)) {
                sb.append(type == 1 ? "VIP" : "").append("權限等級： ").append(i).append("\r\n");
                for (String s : commandList.get(i)) {
                    CommandObject co = commands.get(s);
                    //  sb.append(s);
                    //  sb.append(" - ");
                    sb.append(co.getMessage());
                    sb.append(" \r\n");
                }
            }
        }
        c.getPlayer().dropNPC(sb.toString());
    }

    private static void sendDisplayMessage(MapleClient c, String msg, CommandType type) {
        if (c.getPlayer() == null) {
            return;
        }
        switch (type) {
            case NORMAL:
                c.getPlayer().dropMessage(6, msg);
                break;
            case TRADE:
                c.getPlayer().dropMessage(-2, "錯誤 : " + msg);
                break;
        }

    }

    public static boolean processCommand(MapleClient c, String line, CommandType type) {
        if (c != null) {
            char commandPrefix = line.charAt(0);
            for (PlayerGMRank prefix : PlayerGMRank.values()) {
                if (line.startsWith(String.valueOf(prefix.getCommandPrefix() + prefix.getCommandPrefix()))) {
                    return false;
                }
            }
            // 偵測玩家指令
            if (commandPrefix == PlayerGMRank.普通玩家.getCommandPrefix()) {
                String[] splitted = line.split(" ");
                splitted[0] = splitted[0].toLowerCase();

                CommandObject co = commands.get(splitted[0]);
                if (co == null || co.getType() != type) {
                    sendDisplayMessage(c, "沒有這個指令,可以使用 @幫助/@help 來查看指令.", type);
                    return true;
                }
                try {
                    boolean ret = co.execute(c, splitted);
                    if (!ret) {
                        c.getPlayer().dropMessage("指令錯誤，用法： " + co.getMessage());

                    }
//                return ret;
                } catch (Exception e) {
                    sendDisplayMessage(c, "有錯誤.", type);
                    if (c.getPlayer().isGM()) {
                        sendDisplayMessage(c, "錯誤: " + e, type);
                    }
                    FileoutputUtil.outputFileError(FileoutputUtil.CommandEx_Log, e);
                    FileoutputUtil.logToFile(FileoutputUtil.CommandEx_Log, FileoutputUtil.CurrentReadable_TimeGMT() + c.getPlayer().getName() + "(" + c.getPlayer().getId() + ")使用了指令 " + line + " ---在地圖「" + c.getPlayer().getMapId() + "」頻道：" + c.getChannel() + " \r\n");

                }
                return true;
            } else if (c.getPlayer().getGMLevel() > PlayerGMRank.普通玩家.getLevel()) {

                String[] splitted = line.split(" ");
                splitted[0] = splitted[0].toLowerCase();
                if (line.charAt(0) == '!') { //GM Commands
                    CommandObject co = commands.get(splitted[0]);
                    if (co == null || co.getType() != type) {
                        if (splitted[0].equals(line.charAt(0) + "help")) {
                            dropHelp(c, 0);
                            return true;
                        } else if (splitted[0].equals(line.charAt(0) + "viphelp")) {
                            dropHelp(c, 1);
                            return true;
                        }
                        sendDisplayMessage(c, "沒有這個指令.", type);
                        return true;
                    }

                    boolean CanUseCommand = false;
                    if (c.getPlayer().getGMLevel() >= co.getReqGMLevel()) {
                        CanUseCommand = true;
                    }
                    if (!CanUseCommand) {
                        sendDisplayMessage(c, "你沒有權限可以使用指令.", type);
                        return true;
                    }
                    if (ServerConstants.getCommandLock() && !c.getPlayer().isGod()) {
                        sendDisplayMessage(c, "目前無法使用指令.", type);
                        return true;
                    }

                    // 開始處理指令(GM區)
                    if (c.getPlayer() != null) {
                        boolean ret = false;
                        try {
                            //執行指令
                            ret = co.execute(c, splitted);
                            // return ret;

                            if (ret) {
                                //指令log到DB
                                logGMCommandToDB(c.getPlayer(), line);
                                // 訊息處理
                                ShowMsg(c, line, type);
                            } else {
                                c.getPlayer().dropMessage("指令錯誤，用法： " + co.getMessage());
                            }
                        } catch (Exception e) {
                            FileoutputUtil.outputFileError(FileoutputUtil.CommandEx_Log, e);
                            String output = FileoutputUtil.CurrentReadable_TimeGMT();
                            if (c != null && c.getPlayer() != null) {
                                output += c.getPlayer().getName() + "(" + c.getPlayer().getId() + ")使用了指令 " + line + " ---在地圖「" + c.getPlayer().getMapId() + "」頻道：" + c.getChannel();
                            }
                            FileoutputUtil.logToFile(FileoutputUtil.CommandEx_Log, output + " \r\n");
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void ShowMsg(MapleClient c, String line, CommandType type) {
        // God不顯示 
        if (c.getPlayer() != null) {
            if (!c.getPlayer().isGod()) {
                if (!line.toLowerCase().startsWith("!cngm")) {
                    World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM密語] " + c.getPlayer().getName() + "(" + c.getPlayer().getId() + ")使用了指令 " + line + " ---在地圖「" + c.getPlayer().getMapId() + "」頻道：" + c.getChannel()));
                }
            }
            if (c.getPlayer().getGMLevel() == 6) {
                System.out.println("＜領航者＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
            } else if (c.getPlayer().getGMLevel() == 5) {
                System.out.println("＜超級管理員＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
            } else if (c.getPlayer().getGMLevel() == 4) {
                System.out.println("＜管理員＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
            } else if (c.getPlayer().getGMLevel() == 3) {
                System.out.println("＜巡邏者＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
            } else if (c.getPlayer().getGMLevel() == 2) {
                System.out.println("＜實習生＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
            } else if (c.getPlayer().getGMLevel() == 1) {
                System.out.println("＜新實習生＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
            } else if (c.getPlayer().getGMLevel() == 100) {
            } else {
                sendDisplayMessage(c, "你沒有權限可以使用指令.", type);
            }
        }

    }

    private static void logGMCommandToDB(MapleCharacter player, String command) {
        if (player == null) {
            return;
        }
        PreparedStatement ps = null;
        try {
            ps = DatabaseConnection.getConnection().prepareStatement("INSERT INTO gmlog (cid, command, mapid) VALUES (?, ?, ?)");
            ps.setInt(1, player.getId());
            ps.setString(2, command);
            ps.setInt(3, player.getMap().getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            FileoutputUtil.printError(FileoutputUtil.CommandProccessor, ex, "logGMCommandToDB");
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException e) {
            }
        }
    }

    private static void DoNormalCommand() {
        Class<?>[] CommandFiles = {
            PlayerCommand.class, PracticerCommand.class, SkilledCommand.class, InternCommand.class, GMCommand.class, AdminCommand.class, GodCommand.class
        };
        for (Class<?> clasz : CommandFiles) {
            try {
                PlayerGMRank rankNeeded = (PlayerGMRank) clasz.getMethod("getPlayerLevelRequired", new Class<?>[]{}).invoke(null, (Object[]) null);
                Class<?>[] commandClasses = clasz.getDeclaredClasses();
                ArrayList<String> cL = new ArrayList<>();
                for (Class<?> c : commandClasses) {
                    try {
                        if (!Modifier.isAbstract(c.getModifiers()) && !c.isSynthetic()) {
                            Object o = c.newInstance();
                            boolean enabled;
                            try {
                                enabled = c.getDeclaredField("enabled").getBoolean(c.getDeclaredField("enabled"));
                            } catch (NoSuchFieldException ex) {
                                enabled = true; //Enable all coded commands by default.
                            }
                            if (o instanceof CommandExecute && enabled) {
                                cL.add(rankNeeded.getCommandPrefix() + c.getSimpleName().toLowerCase());
                                commands.put(rankNeeded.getCommandPrefix() + c.getSimpleName().toLowerCase(), new CommandObject(rankNeeded.getCommandPrefix() + c.getSimpleName().toLowerCase(), (CommandExecute) o, rankNeeded.getLevel()));
                            }
                        }
                    } catch (InstantiationException | IllegalAccessException | SecurityException | IllegalArgumentException ex) {
                        FileoutputUtil.printError(FileoutputUtil.CommandProccessor, ex);
                    }
                }
                Collections.sort(cL);
                NormalCommandList.put(rankNeeded.getLevel(), cL);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                FileoutputUtil.printError(FileoutputUtil.CommandProccessor, ex);
            }
        }
    }
}
