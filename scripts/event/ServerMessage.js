var Message = new Array(
    "如果遇到不能點技能/能力值/不能進傳點/不能點NPC,請在對話框打@ea就可以了",
    "/找人 玩家名字 可以用來找人喔",
	"禁止開外掛，遊戲愉快！！",
	"關於伺服器指令可以使用@help/@幫助查看",
    "如有bug請回報GM");

var setupTask;

function init() {
    scheduleNew();
}

function scheduleNew() {
    setupTask = em.schedule("start", 300000);
}

function cancelSchedule() {
	setupTask.cancel(false);
}

function start() {
    scheduleNew();
    em.broadcastYellowMsg("[" + em.getChannelServer().getServerName() + "幫助] " + Message[Math.floor(Math.random() * Message.length)]);
}