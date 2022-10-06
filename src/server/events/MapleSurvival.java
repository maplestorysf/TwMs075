package server.events;

import java.util.concurrent.ScheduledFuture;
import client.MapleCharacter;
import server.Timer.EventTimer;
import tools.packet.CField;
import tools.packet.CWvsContext;

public class MapleSurvival extends MapleEvent {

    protected long time = 360000; //reduce for less time
    protected long timeStarted = 0;
    protected ScheduledFuture<?> olaSchedule;

    public MapleSurvival(final int channel, final MapleEventType type) {
        super(channel, type);
    }

    @Override
    public void finished(final MapleCharacter chr) {
        givePrize(chr);
        //chr.finishAchievement(25);
    }

    @Override
    public void onMapLoad(MapleCharacter chr) {
        super.onMapLoad(chr);
        if (isTimerStarted()) {
            chr.getClient().sendPacket(CField.getClock((int) (getTimeLeft() / 1000)));
        }
    }

    @Override
    public void startEvent() { // TODO: Messages
        unreset();
        super.reset(); //isRunning = true
        broadcast(CField.getClock((int) (time / 1000)));
        this.timeStarted = System.currentTimeMillis();

        olaSchedule = EventTimer.getInstance().schedule(new Runnable() {

            @Override
            public void run() {
                for (int i = 0; i < type.mapids.length; i++) {
                    for (MapleCharacter chr : getMap(i).getCharactersThreadsafe()) {
                        warpBack(chr);
                    }
                    unreset();
                }
            }
        }, this.time);

        broadcast(CWvsContext.serverNotice(0, "活動已經開始，請通過中間的入口開始遊戲。"));
        broadcast(CWvsContext.serverNotice(0, "只准成功，不許失敗！"));
    }

    public boolean isTimerStarted() {
        return timeStarted > 0;
    }

    public long getTime() {
        return time;
    }

    public void resetSchedule() {
        this.timeStarted = 0;
        if (olaSchedule != null) {
            olaSchedule.cancel(false);
        }
        olaSchedule = null;
    }

    @Override
    public void reset() {
        super.reset();
        resetSchedule();
        getMap(0).getPortal("join00").setPortalState(false);
    }

    @Override
    public void unreset() {
        super.unreset();
        resetSchedule();
        getMap(0).getPortal("join00").setPortalState(true);
    }

    public long getTimeLeft() {
        return time - (System.currentTimeMillis() - timeStarted);
    }
}
