#!/system/bin/sh
# 模块操作按钮：重新安装/更新 App 并重启常驻服务
MODDIR=${0%/*}
APK="$MODDIR/RefreshSwitch.apk"
PKG=com.dsh.refreshswitch
[ -f "$APK" ] || { echo "APK 不存在"; exit 1; }
pm install -r -g "$APK"
pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
am start -n "$PKG/.TrampolineActivity" >/dev/null 2>&1
# 兜底入口：即使桌面图标被隐藏、通知被关闭，也能从这里打开主界面
am start -n "$PKG/.MainActivity" >/dev/null 2>&1
echo "已更新 RefreshSwitch 并拉起服务"
