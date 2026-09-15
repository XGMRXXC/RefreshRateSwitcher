package com.dsh.refreshswitch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 启动器入口（无界面跳板）。
 * 桌面图标属于本组件，关闭本组件即可隐藏图标，而 MainActivity 仍可被通知/悬浮窗打开。
 * 同时根据设置决定主界面是否进入最近任务。
 */
public class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (SwitchService.isHideRecents(this)) {
            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        try { startActivity(i); } catch (Throwable ignored) {}
        finish();
        overridePendingTransition(0, 0);
    }
}
