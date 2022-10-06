/*
Red Sign - 101st Floor Eos Tower (221024500)
 */

var status = -1;
var minLevel = 35; // 35
var maxLevel = 50; // 65

var minPartySize = 6;
var maxPartySize = 6;

function action(mode, type, selection) {
	if (mode == 1) {
		status++;
	} else {
		if (status == 0) {
			cm.dispose();
			return;
		}
		status--;
	}

	if (status == 0) {
		cm.removeAll(4001022);
		cm.removeAll(4001023);
		if (cm.getParty() == null) { // No Party
			cm.sendSimple("你貌似沒有達到要求...:\r\n\r\n#r要求: " + minPartySize + " 玩家成員, 每個人的等級必須在 " + minLevel + " 到 等級 " + maxLevel);
		} else if (!cm.isLeader()) { // Not Party Leader
			cm.sendSimple("如果你想做任務，請 #b隊長#k 跟我談.");
		} else {
			// Check if all party members are within PQ levels
			var party = cm.getParty().getMembers();
			var mapId = cm.getMapId();
			var next = true;
			var levelValid = 0;
			var inMap = 0;
			var it = party.iterator();
			while (it.hasNext()) {
				var cPlayer = it.next();
				if ((cPlayer.getLevel() >= minLevel) && (cPlayer.getLevel() <= maxLevel)) {
					levelValid += 1;
				} else {
					next = false;
				}
				if (cPlayer.getMapid() == mapId) {
					inMap += (cPlayer.getJobId() == 900 ? 6 : 1);
				}
			}
			if (party.size() > maxPartySize || inMap < minPartySize) {
				next = false;
			}
			if (next) {
				var em = cm.getEventManager("LudiPQ");
				if (em == null) {
					cm.sendSimple("找不到腳本請聯絡GM");
				} else {
					var prop = em.getProperty("state");
					if (prop.equals("0") || prop == null) {
						em.startInstance(cm.getParty(), cm.getMap());
						cm.removeAll(4001022);
						cm.removeAll(4001023);
						cm.dispose();
						return;
					} else {
						cm.sendSimple("其他隊伍已經在裡面做 #r組隊任務了#k 請嘗試換頻道或者等其他隊伍完成。");
					}
				}
			} else {
				cm.sendSimple("你的隊伍貌似沒有達到要求...:\r\n\r\n#r要求: " + minPartySize + " 玩家成員, 每個人的等級必須在 " + minLevel + " 到 等級 " + maxLevel);
			}
		}
	}
}
