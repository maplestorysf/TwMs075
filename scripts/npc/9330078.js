var status = 0;

function start() {
	if (cm.getPlayer().getMapId() == 749020910) {
		status = 1;
		cm.sendYesNo("準備挑戰緊張又刺激的活動了嗎??");
	} else {
		cm.sendYesNo("嗨，我是#p9330078# 目前楓之谷3週年慶想要參加活動嗎???");
	}
}

function action(mode, type, selection) {
	if (mode != 1) {
		if (mode == 0)
			cm.sendOk("需要的時候，再來找我吧。");
		cm.dispose();
		return;
	}
	status++;
	if (status == 1) {
		cm.saveLocation("BIRTHDAY");
		cm.warp(749020910, 0);
		cm.dispose();
	} else if (status == 2) {
		if (cm.getPlayer().hasEquipped(1302084)) {
			var em = cm.getEventManager("Cake");
			if (em == null) {
				cm.sendOk("找不到腳本，請聯繫GM！");
				cm.dispose();
				return;
			} else {
				var prop = em.getProperty("state");
				if (prop == null || prop.equals("0")) {
					em.startInstance(cm.getPlayer(), cm.getMap());
				} else {
					cm.sendOk("已經有人在裡面挑戰了。");
					cm.dispose();
					return;
				}
			}
		} else {
			cm.sendOk("請確認是否穿戴了#t1302084#。");
			cm.dispose();
			return;
		}
		cm.dispose();
	}
}
