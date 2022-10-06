var status = -1;
var selectedMap = -1;
var maps = Array(104000000, 100000000, 101000000, 103000000, 120000000, 105040300, 200000000, 110000000, 230000000, 240000000, 211000000, 222000000, 221000000, 260000000, 261000000, 250000000, 251000000, 500000000, 600000000, 740000000, 742000000, 800000000, 801000000);

function start() {
	if (cm.getPlayer().getLevel() < 10 && cm.getPlayer().getJob() != 200) {
		cm.sendOk("請10等再來跟我講話.");
		cm.dispose();
		return;
	}
	action(1, 0, 0);
}

function action(mode, type, selection) {
	if (mode == 1) {
		status++;
	} else {
		if (status >= 2 || status == 0) {
			cm.dispose();
			return;
		}
		status--;
	}

	if (status == 0) {
		cm.sendSimple("歡迎來到#rOldMS[封測]#k 你可以選擇的服務:\r\n#L0#地圖傳送");
	} else if (status == 1) {
		if (selection == 0) {
			var selStr = "選擇您的目的地.#b";
			for (var i = 0; i < maps.length; i++) {
				selStr += "\r\n#L" + i + "##m" + maps[i] + "# #l";
			}
		}
		cm.sendSimple(selStr);
	} else if (status == 2) {
		cm.sendYesNo("所以您在這裡什麼都沒有留下 \r\n請問您要移動到 #r#m" + maps[selection] + "##k?");
		selectedMap = selection;
	} else if (status == 3) {
		if (selectedMap >= 0) {
			cm.warp(maps[selectedMap], 0);
		}
		cm.dispose();
	}
}
