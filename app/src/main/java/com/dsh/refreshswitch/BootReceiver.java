package com.dsh.refreshswitch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 开机 / 应用更新后自动拉起常驻服务与守护任务。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(ModeUtil.TAG, "boot receiver: " + (intent == null ? "?" : intent.getAction()));
        if (!SwitchService.isEnabled(context)) return;
        SwitchService.start(context);
        RestartJobService.schedule(context);
    }
}
