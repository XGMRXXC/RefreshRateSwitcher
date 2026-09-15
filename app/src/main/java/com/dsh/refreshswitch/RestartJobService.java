package com.dsh.refreshswitch;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * 自启动守护：周期性检查常驻服务是否存活，被杀则拉起。
 * 配合 START_STICKY / BOOT_COMPLETED / onTaskRemoved 闹钟形成多级守护。
 */
public class RestartJobService extends JobService {

    private static final int JOB_ID = 0x5253;
    /** 系统允许的最小周期为 15 分钟 */
    private static final long PERIOD_MS = 15 * 60 * 1000L;

    public static void schedule(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            JobInfo.Builder b = new JobInfo.Builder(JOB_ID,
                    new ComponentName(ctx, RestartJobService.class))
                    .setPersisted(true)                       // 重启后仍有效
                    .setPeriodic(PERIOD_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
            if (Build.VERSION.SDK_INT >= 24) {
                b.setPeriodic(PERIOD_MS, JobInfo.getMinFlexMillis());
            }
            js.schedule(b.build());
            Log.i(ModeUtil.TAG, "restart job scheduled");
        } catch (Throwable t) {
            Log.e(ModeUtil.TAG, "schedule failed", t);
        }
    }

    public static void cancel(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(JOB_ID);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(ModeUtil.TAG, "restart job fired");
        SwitchService.start(getApplicationContext());
        // 顺便重排一次，保证周期任务的持续性
        schedule(getApplicationContext());
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
