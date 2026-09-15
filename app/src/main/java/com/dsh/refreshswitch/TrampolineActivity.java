package com.dsh.refreshswitch;

import android.app.Activity;
import android.os.Bundle;

/** 无界面跳板：仅供外部（如 KernelSU 模块看门狗）静默拉起常驻服务，启动后立即结束。 */
public class TrampolineActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SwitchService.start(getApplicationContext());
        RestartJobService.schedule(getApplicationContext());
        finish();
        overridePendingTransition(0, 0);
    }
}
