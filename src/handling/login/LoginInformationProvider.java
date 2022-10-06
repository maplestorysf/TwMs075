package handling.login;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Map;
import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import tools.Triple;

public class LoginInformationProvider {

    public enum JobType {

        UltimateAdventurer(-1, "Ultimate", 0, 130000000),
        Resistance(0, "Resistance", 3000, 931000000),
        Adventurer(1, "", 0, 0),
        Cygnus(2, "Premium", 1000, 913040000),
        Aran(3, "Orient", 2000, 914000000),
        Evan(4, "Evan", 2001, 900090000),
        Mercedes(5, "", 2002, 910150000),
        Demon(6, "", 3001, 931050310);
        public int type, id, map;
        public String job;

        private JobType(int type, String job, int id, int map) {
            this.type = type;
            this.job = job;
            this.id = id;
            this.map = map;
        }

        public static JobType getByJob(String g) {
            for (JobType e : JobType.values()) {
                if (e.job.length() > 0 && g.startsWith(e.job)) {
                    return e;
                }
            }
            return Adventurer;
        }

        public static JobType getByType(int g) {
            for (JobType e : JobType.values()) {
                if (e.type == g) {
                    return e;
                }
            }
            return Adventurer;
        }

        public static JobType getById(int g) {
            for (JobType e : JobType.values()) {
                if (e.id == g) {
                    return e;
                }
            }
            return Adventurer;
        }
    }
    private final static LoginInformationProvider instance = new LoginInformationProvider();
    protected final List<String> ForbiddenName = new ArrayList<>();
    //gender, val, job
    protected final Map<Triple<Integer, Integer, Integer>, List<Integer>> makeCharInfo = new HashMap<>();
    //0 = 眼睛 1 = 頭髮 2 = 上衣 3 = 褲裙 4 = 鞋子 5 = 武器

    public static LoginInformationProvider getInstance() {
        return instance;
    }

    protected LoginInformationProvider() {
        final String WZpath = System.getProperty("net.sf.odinms.wzpath");
        final MapleDataProvider prov = MapleDataProviderFactory.getDataProvider(new File(WZpath + "/Etc.wz"));
        MapleData nameData = prov.getData("ForbiddenName.img");
        for (final MapleData data : nameData.getChildren()) {
            ForbiddenName.add(MapleDataTool.getString(data));
        }
        nameData = prov.getData("Curse.img");
        for (final MapleData data : nameData.getChildren()) {
            ForbiddenName.add(MapleDataTool.getString(data).split(",")[0]);
        }
        final MapleData infoData = prov.getData("MakeCharInfo.img");

        for (MapleData dat : infoData.getChildren()) {
            int gender = -1;
            if (dat.getName().endsWith("CharFemale")) { // comes first..
                gender = 1;
            } else if (dat.getName().endsWith("CharMale")) {
                gender = 0;
            }
            final int job = JobType.getByJob(dat.getName()).type;
            for (MapleData da : dat.getChildren()) {
                final Triple<Integer, Integer, Integer> key = new Triple<>(gender, Integer.parseInt(da.getName()), 0);//job
                List<Integer> our = makeCharInfo.get(key);
                if (our == null) {
                    our = new ArrayList<>();
                    makeCharInfo.put(key, our);
                }
                for (MapleData d : da) {
                    our.add(MapleDataTool.getInt(d, -1));
                }
            }
        }
//        for (MapleData dat : infoData) {
//            try {
//                final int type = JobType.getById(Integer.parseInt(dat.getName())).type;
//                for (MapleData d : dat) {
//                    int val;
//                    if (d.getName().endsWith("female")) {
//                        val = 1;
//                    } else if (d.getName().endsWith("male")) {
//                        val = 0;
//                    } else {
//                        continue;
//                    }
//                    for (MapleData da : d) {
//                        final Triple<Integer, Integer, Integer> key = new Triple<>(val, Integer.parseInt(da.getName()), type);
//                        List<Integer> our = makeCharInfo.get(key);
//                        if (our == null) {
//                            our = new ArrayList<>();
//                            makeCharInfo.put(key, our);
//                        }
//                        for (MapleData dd : da) {
//                            our.add(MapleDataTool.getInt(dd, -1));
//                        }
//                    }
//                }
//            } catch (NumberFormatException e) {
//            }
//        }
//        final MapleData uA = infoData.getChildByPath("UltimateAdventurer");
//        for (MapleData dat : uA) {
//            final Triple<Integer, Integer, Integer> key = new Triple<Integer, Integer, Integer>(-1, Integer.parseInt(dat.getName()), JobType.UltimateAdventurer.type);
//            List<Integer> our = makeCharInfo.get(key);
//            if (our == null) {
//                our = new ArrayList<Integer>();
//                makeCharInfo.put(key, our);
//            }
//            for (MapleData d : dat) {
//                our.add(MapleDataTool.getInt(d, -1));
//            }
//        }
    }

    public final boolean isForbiddenName(final String in) {
        for (final String name : ForbiddenName) {
            if (in.toLowerCase().contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public final boolean isEligibleItem(final int gender, final int val, final int job, final int item) {
        if (item < 0) {
            return false;
        }
        final Triple<Integer, Integer, Integer> key = new Triple<>(gender, val, job);
        final List<Integer> our = makeCharInfo.get(key);
        if (our == null) {
            return false;
        }
        return our.contains(item);
    }
}
