package com.dsh.refreshswitch;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * root 守护进程：关闭「常驻通知」后不再使用前台服务，
 * 由一段 root shell 循环负责持续锁定刷新率 —— 不占用通知栏，也不依赖应用进程存活。
 *
 * 相关文件（/data/local/tmp）：
 *   rs_daemon.sh   守护脚本（由 App 生成并复制）
 *   rs_state       目标与挡位映射（App 每次变更后同步）
 *   rs_daemon.run  运行标志，删除即退出
 *   rs_daemon.pid  守护进程号
 *   rs_daemon.log  运行日志（自动限长）
 */
public final class Daemon {

    private static final String DIR = "/data/local/tmp";
    private static final String SH = DIR + "/rs_daemon.sh";
    private static final String STATE = DIR + "/rs_state";
    private static final String RUN = DIR + "/rs_daemon.run";
    private static final String PIDF = DIR + "/rs_daemon.pid";
    private static final String LOG = DIR + "/rs_daemon.log";

    private static final String SYS_FPS =
            "/sys/devices/virtual/mi_display/disp_feature/disp-DSI-0/dynamic_fps";
    private static final int SF_CODE = 1035;

    /** 按进程名精确匹配并结束所有守护实例（避免残留旧实例重复下发）。 */
    private static final String KILL_ALL =
            "for p in $(ps -A -o PID,ARGS 2>/dev/null "
            + "| awk '/sh \\/data\\/local\\/tmp\\/rs_daemon\\.sh$/ {print $1}'); "
            + "do kill $p 2>/dev/null; done; ";

    private static volatile int cachedPid = -1;
    private static volatile long cachedAt = 0L;
    private static volatile boolean checking = false;

    private Daemon() {}

    // ---------------- 状态查询 ----------------

    /** 守护进程 pid；异步刷新缓存，调用不会阻塞主线程。 */
    public static int pid() {
        long now = System.currentTimeMillis();
        if (!checking && now - cachedAt > 3000) {
            checking = true;
            new Thread(() -> {
                int p = readPid();
                cachedPid = p;
                cachedAt = System.currentTimeMillis();
                checking = false;
            }, "daemon-pid").start();
        }
        return cachedPid;
    }

    public static boolean isRunning() { return pid() > 0; }

    private static int readPid() {
        String out = ModeUtil.suOut("p=$(cat " + PIDF + " 2>/dev/null); "
                + "if [ -n \"$p\" ] && [ -d /proc/$p ]; then echo $p; fi; true");
        if (out == null) return -1;
        for (String tok : out.split("\\s+")) {
            try {
                int v = Integer.parseInt(tok.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    // ---------------- 控制 ----------------

    /** 启动（或重启）守护进程。 */
    public static void start(Context ctx) {
        if (!ModeUtil.hasRoot()) {
            Log.w(ModeUtil.TAG, "daemon start skipped: no root");
            return;
        }
        File script = writeFile(ctx, "daemon.sh", SCRIPT);
        File state = writeState(ctx);
        if (script == null || state == null) return;

        String cmd = KILL_ALL
                + "rm -f " + RUN + "; "
                + "cp '" + script.getAbsolutePath() + "' " + SH + "; "
                + "cp '" + state.getAbsolutePath() + "' " + STATE + "; "
                + "chmod 755 " + SH + "; "
                + "touch " + RUN + "; "
                + "setsid sh " + SH + " >/dev/null 2>&1 </dev/null &";
        Log.i(ModeUtil.TAG, "daemon start");
        ModeUtil.suOut(cmd);
        cachedAt = 0L;   // 让下次查询立刻刷新
    }

    /** 停止守护进程。 */
    public static void stop() {
        String cmd = "rm -f " + RUN + "; " + KILL_ALL + "rm -f " + PIDF + "; true";
        Log.i(ModeUtil.TAG, "daemon stop");
        ModeUtil.suOut(cmd);
        cachedPid = -1;
        cachedAt = System.currentTimeMillis();
    }

    /** 把当前锁定目标与挡位映射同步给守护进程（状态文件内容变化时调用）。 */
    public static void sync(Context ctx) {
        if (!ModeUtil.hasRoot()) return;
        File state = writeState(ctx);
        if (state == null) return;
        ModeUtil.suOut("cp '" + state.getAbsolutePath() + "' " + STATE);
    }

    /** 重新拉起（先同步状态再确保在跑）。 */
    public static void restart(Context ctx) {
        stop();
        start(ctx);
    }

    // ---------------- 文件生成 ----------------

    private static File writeState(Context ctx) {
        boolean enabled = SwitchService.isLockEnabled(ctx);
        int fps = SwitchService.lockedFps(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("enabled=").append(enabled ? 1 : 0).append('\n');
        sb.append("target=").append(fps > 0 ? fps : "").append('\n');
        List<ModeUtil.Mode> modes = ModeUtil.listModes(ctx);
        for (ModeUtil.Mode m : modes) {
            int sfIndex = m.id - 1;               // SurfaceFlinger 索引 = modeId - 1
            if (sfIndex < 0) continue;
            sb.append(m.fpsInt()).append('=').append(sfIndex).append('\n');
        }
        return writeFile(ctx, "daemon.state", sb.toString());
    }

    private static File writeFile(Context ctx, String name, String content) {
        try {
            File f = new File(ctx.getFilesDir(), name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            f.setReadable(true, false);
            return f;
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "write " + name + " failed", t);
            return null;
        }
    }

    /** 读取守护日志尾部（调试用）。 */
    public static String log() {
        String s = ModeUtil.suOut("tail -n 40 " + LOG + " 2>/dev/null; true");
        return s == null ? "" : s;
    }

    // ---------------- 守护脚本 ----------------

    private static final String SCRIPT =
            "#!/system/bin/sh\n"
            + "# RefreshRate Switcher —— root 守护：无通知栏持续锁定刷新率\n"
            + "D=" + DIR + "\n"
            + "SYS=" + SYS_FPS + "\n"
            + "PIDF=$D/rs_daemon.pid\n"
            + "STATE=$D/rs_state\n"
            + "RUN=$D/rs_daemon.run\n"
            + "LOG=$D/rs_daemon.log\n"
            + "\n"
            + "echo $$ > $PIDF\n"
            + "echo \"[$(date '+%m-%d %H:%M:%S')] daemon start pid=$$\" >> $LOG\n"
            + "\n"
            + "while [ -f $RUN ]; do\n"
            + "  if [ \"$(cat $PIDF 2>/dev/null)\" != \"$$\" ]; then exit 0; fi\n"
            + "  EN=$(sed -n 's/^enabled=//p' $STATE 2>/dev/null | head -n1)\n"
            + "  T=$(sed -n 's/^target=//p' $STATE 2>/dev/null | head -n1)\n"
            + "  if [ \"$EN\" = \"1\" ] && [ -n \"$T\" ]; then\n"
            + "    IDX=$(sed -n \"s/^$T=//p\" $STATE 2>/dev/null | head -n1)\n"
            + "    CUR=$(cat $SYS 2>/dev/null | tr -d ' \\r\\n')\n"
            + "    if [ -n \"$IDX\" ] && [ -n \"$CUR\" ] && [ \"$CUR\" != \"$T\" ]; then\n"
            + "      service call SurfaceFlinger " + SF_CODE + " i32 $IDX >/dev/null 2>&1\n"
            + "      echo \"[$(date '+%H:%M:%S')] enforce $CUR -> $T (idx $IDX)\" >> $LOG\n"
            + "    fi\n"
            + "  fi\n"
            + "  SZ=$(wc -c < $LOG 2>/dev/null | tr -d ' ')\n"
            + "  if [ -n \"$SZ\" ] && [ \"$SZ\" -gt 65536 ]; then\n"
            + "    tail -n 200 $LOG > $LOG.tmp 2>/dev/null && mv $LOG.tmp $LOG\n"
            + "  fi\n"
            + "  sleep 1\n"
            + "done\n"
            + "\n"
            + "echo \"[$(date '+%m-%d %H:%M:%S')] daemon exit pid=$$\" >> $LOG\n"
            + "if [ \"$(cat $PIDF 2>/dev/null)\" = \"$$\" ]; then rm -f $PIDF; fi\n";
}
