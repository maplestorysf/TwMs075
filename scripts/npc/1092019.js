function start() {
    if (cm.getJob() == 522 && cm.getPlayerStat("LVL") >= 120 && cm.getQuestStatus(6400) == 1) {
        if (!cm.hasSkill(5221003)) {
            cm.teachSkill(5221003, 0, 10);
            cm.forceCompleteQuest(6400);
        }
    }
    cm.sendOk("你是誰你在跟我說話？如果你只是無聊，去找別人！！");
}

function action(mode, type, selection) {
    cm.dispose();
}