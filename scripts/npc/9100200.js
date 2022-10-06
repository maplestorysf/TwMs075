function start() {
				var marr = cm.getQuestRecord(160201);
				if (marr.getCustomData() == null) {
					marr.setCustomData("0");
				}
				var dat = parseInt(marr.getCustomData());
if (dat + 86400000 < cm.getCurrentTime()) {
                    cm.gainBeans(500);
    cm.開啟小鋼珠(0);
                } else {
    cm.開啟小鋼珠(0);
		}
            cm.dispose();
}