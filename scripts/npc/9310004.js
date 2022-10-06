function start() {
	if (cm.getQuestStatus(8512) == 1) {
    cm.warp(701010321);
    cm.dispose();
	} else {
	    cm.sendOk("你沒有完成農民的拜託任務!");
    cm.dispose();
}
}