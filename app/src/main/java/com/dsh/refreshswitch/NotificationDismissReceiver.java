package com.dsh.refreshswitch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 常驻通知被用户手动关闭（划掉）时触发。
 * 若「通知被关闭后自动重发」开关打开，则重新拉起服务把通知发回来。
 * 机制：通知上挂了 setDeleteIntent，系统在通知被清除时发送这个广播（无需任何额外权限）。
 */
public class NotificationDismissReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!SwitchService.ACTION_NOTIF_DISMISSED.equals(intent.getAction())) return;
        if (!SwitchService.isResendEnabled(context)) return;
        // 重新拉起服务 → onCreate 会重新 startForeground 发出常驻通知
        SwitchService.start(context);
    }
}