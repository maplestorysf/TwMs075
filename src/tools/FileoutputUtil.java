package tools;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.TimeZone;

public class FileoutputUtil {

    private static final SimpleDateFormat sdfT = new SimpleDateFormat("yyyy年MM月dd日HH時mm分ss秒");
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat sdfGMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat sdf_ = new SimpleDateFormat("yyyy-MM-dd");
    private static final String FILE_PATH = "logs/" + sdf_.format(Calendar.getInstance().getTime()) + "/";
    private static final String ERROR = "錯誤/";
    // 輸出文件
    public static final String Acc_Stuck = "error/Log_卡帳.txt",
            Login_Error = "error/Log_登入_錯誤.txt",
            ScriptEx_Log = "error/Log_腳本_例外.txt",
            PacketEx_Log = "error/Log_封包_例外.txt",
            Hacker_Log = "hack/Log_外掛.txt",
            Movement_Log = "error/Log_移動.txt",
            CommandEx_Log = "error/Log_指令_例外.txt",
            ConsoleCommandProcessor = "error/Log_指令_例外.txt",
            CommandProccessor = "error/Log_指令_例外.txt",
            Packet_Log = "packet/Log_數據包收發.txt";

    public static void log(final String file, final String msg) {
        logToFile(file, "\r\n------------------------ " + CurrentReadable_Time() + " ------------------------\r\n" + msg);
    }

    public static void printError(final String file, String function, final Throwable t, String msg) {
        log("例外/" + file, "[" + function + "] " + msg + "\r\n " + getString(t));
    }

    public static void printError(final String name, final Throwable t) {
        FileOutputStream out = null;
        final String file = FILE_PATH + ERROR + name;
        try {
            File outputFile = new File(file);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            out = new FileOutputStream(file, true);
            out.write(getString(t).getBytes());
            out.write("\n---------------------------------\r\n".getBytes());
        } catch (IOException ess) {
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    public static void printError(final String name, final Throwable t, final String info) {
        FileOutputStream out = null;
        final String file = FILE_PATH + ERROR + name;
        try {
            File outputFile = new File(file);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            out = new FileOutputStream(file, true);
            out.write((info + "\r\n").getBytes());
            out.write(getString(t).getBytes());
            out.write("\n---------------------------------\r\n".getBytes());
        } catch (IOException ess) {
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    public static void outputFileError(final String file, final Throwable t) {
        log(file, getString(t));
    }

    public static void logToFile(final String file, final String msg) {
        logToFile(file, msg, false);
    }

    public static void logToFile(final String file, final String msg, boolean notExists) {
        logToFile(file, msg, notExists, true);
    }

    public static void logToFile(final String file, final String msg, boolean notExists, boolean size) {
        FileOutputStream out = null;
        try {
            File outputFile = new File("日誌/" + file);
            if (outputFile.exists() && outputFile.isFile() && outputFile.length() >= 1024000 && size) {
                String sub = file.substring(0, file.indexOf("/") + 1) + "old/" + file.substring(file.indexOf("/") + 1, file.length() - 4);
                String time = sdfT.format(Calendar.getInstance().getTime());
                String sub2 = file.substring(file.length() - 4, file.length());
                String output = "Logs/" + sub + "_" + time + sub2;
                if (new File(output).getParentFile() != null) {
                    new File(output).getParentFile().mkdirs();
                }
                outputFile.renameTo(new File(output));
                outputFile = new File("Logs/" + file);
            }
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            out = new FileOutputStream("Logs/" + file, true);
            if (!out.toString().contains(msg) || !notExists) {
                OutputStreamWriter osw = new OutputStreamWriter(out, "UTF-8");
                osw.write(msg);
                osw.flush();
            }
        } catch (IOException ess) {
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    public static String CurrentReadable_Date() {
        return sdf_.format(Calendar.getInstance().getTime());
    }

    public static String CurrentReadable_Time() {
        return sdf.format(Calendar.getInstance().getTime());
    }

    public static String CurrentReadable_TimeGMT() {
        return sdfGMT.format(new Date());
    }

    public static String getString(final Throwable e) {
        String retValue = null;
        StringWriter sw = null;
        PrintWriter pw = null;
        try {
            sw = new StringWriter();
            pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            retValue = sw.toString();
        } finally {
            try {
                if (pw != null) {
                    pw.close();
                }
                if (sw != null) {
                    sw.close();
                }
            } catch (IOException ignore) {
            }
        }
        return retValue;
    }

}
