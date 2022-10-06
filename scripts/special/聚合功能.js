/* global cm */

var status = -1;
var select = -1;

function start() {
    cm.sendSimple(cm.getChannelServer().getServerName() + "管理員為您服務，請問你想做什麼呢？\r\n"+
	"#L1#進入拍賣行#l\r\n" +
	"#L2#查看線上人數#l\r\n" +
	"#L3#領取線上點數#l\r\n" +
	"#L4#傳送訊息給GM#l\r\n" +
	//"#L5#清除卡精靈商人#l\r\n" +
	//"#L6#卡圖修復#l\r\n" +
	"#L7#存檔#l\r\n" +
	//"#L8#BOSSPQ兌換#l\r\n" +
	"#L9#萬能工具箱#l\r\n" +
	"#L10#參加活動#l\r\n" +
	//"#L11#領取勳章#l\r\n" +
	//"#L12#夏日Fun暑假#l\r\n" +
	//"#L13#我是抓羊專家#l\r\n" +
	"#L14#開/關閉廣播顯示#l\r\n" 
    );
}

function action(mode, type, selection) {
    if (select === -1) {
        select = selection;
    }

    switch (select) {
        case 1: {
            cm.dispose();
            //cm.enterMTS();
			cm.playerMessage("拍賣功能尚未開放。");
            break;
        }
        case 2: {
            cm.sendOk("當前" + cm.getChannelNumber() + "頻道: " + cm.getChannelOnline() + "人   當前伺服器總計線上人數: " + cm.getTotalOnline() + "個");
            cm.dispose();
            break;
        }
        case 3: {
            select3(mode, type, selection);
            break;
        }
        case 4: {
            CGM(mode, type, selection);
            break;
        }
        case 5: {
            cm.dispose();
            cm.processCommand("@jk_hm");
            break;
        }
        /*case 6: {
            cm.dispose();
            cm.processCommand("@卡圖");
            break;
        }*/
        case 7: {
            cm.dispose();
            cm.processCommand("@存檔");
            break;
        }
        case 8: {
            openNpc(9330082);
            break;
        }
        case 9: {
            openNpc(9000058);
            break;
        }
        case 10: {
            openNpc(9000001);
            break;
        }
        case 11: {
            openNpc(9010000, "Medals");
            break;
        }
		case 12: {
			openNpc(9010000, "sumnmer");
			break;
		}
		case 13: {
			openNpc(9010000, "抓羊專家");
			break;
		}
		case 14: {
			cm.dispose();
			cm.processCommand("@Tsmega");
			break;
		}
        default : {
            cm.sendOk("此功能未完成");
            cm.dispose();
        }
    }
}

function select3(mode, type, selection) {
    if (mode === 1) {
        status++;
    } else if (mode === 0) {
        status--;
    }

    var i = -1;
    if (status <= i++) {
        cm.dispose();
    } else if (status === i++) {
        var gain = cm.getMP();
        if (gain <= 0) {
            cm.sendOk("目前沒有任何在線點數唷。");
            cm.dispose();
            return;
        } else {
            cm.sendYesNo("目前楓葉點數: " + cm.getMaplePoint() + "\r\n" + "目前在線點數已經累積: " + gain + " 點，是否領取?");
        }
    } else if (status === i++) {
        var gain = cm.getMP();
        cm.setMP(0);
        cm.gainMaplePoint(gain);
        cm.save();
        cm.sendOk("領取了 " + gain + " 點在線點數, 目前楓葉點數: " + cm.getMaplePoint());
        cm.dispose();
    } else {
        cm.dispose();
    }
}

function CGM(mode, type, selection) {
    if (mode === 1) {
        status++;
    } else if (mode === 0) {
        status--;
    }

    var i = -1;
    if (status <= i++) {
        cm.dispose();
    } else if (status === i++) {
        cm.sendGetText("請輸入你要對GM傳送的訊息");
    } else if (status === i++) {
        var text = cm.getText();
        if (text === null || text === "") {
            cm.sendOk("並未輸入任何內容.");
            cm.dispose();
            return;
        }
        cm.dispose();
        cm.processCommand("@CGM " + text);
    } else {
        cm.dispose();
    }
}

function openNpc(npcid) {
    openNpc(npcid, null);
}

function openNpc(npcid, script) {
    var mapid = cm.getMapId();
    cm.dispose();
    if (cm.getPlayerStat("LVL") < 10) {
        cm.sendOk("你的等級不能小於10等.");
    } else if (
            cm.hasSquadByMap() ||
            cm.hasEventInstance() ||
            cm.hasEMByMap() ||
            mapid >= 990000000 ||
            (mapid >= 680000210 && mapid <= 680000502) ||
            (mapid / 1000 === 980000 && mapid !== 980000000) ||
            mapid / 100 === 1030008 ||
            mapid / 100 === 922010 ||
            mapid / 10 === 13003000
    ) {
        cm.sendOk("你不能在這裡使用這個功能.");
    } else {
        if (script == null) {
            cm.openNpc(npcid);
        } else {
            cm.openNpc(npcid, script);
        }
    }
}