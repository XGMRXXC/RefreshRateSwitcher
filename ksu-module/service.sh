#!/system/bin/sh
# RefreshRate Switcher —— 安装 App、授予必要权限、看门狗守护常驻服务
MODDIR=${0%/*}
PKG=com.dsh.refreshswitch
APK="$MODDIR/RefreshSwitch.apk"
LOG=/data/adb/refresh_switch.log

log() { echo "[$(date '+%m-%d %H:%M:%S')] $*" >> $LOG; echo "[refresh_switch] $*"; }

[ -f "$APK" ] || { log "APK 不存在，跳过"; exit 0; }

# ---- 等 package manager 就绪 ----
i=0
while [ $i -lt 60 ]; do
  pm path android >/dev/null 2>&1 && break
  sleep 1
  i=$((i+1))
done

# ---- 安装 / 更新（按 APK 内容比对，版本变化时自动重装）----
NEW_MD5=$(md5sum "$APK" 2>/dev/null | awk '{print $1}')
CUR_APK=$(pm path "$PKG" 2>/dev/null | head -n1 | sed 's/^package://')
CUR_MD5=$(md5sum "$CUR_APK" 2>/dev/null | awk '{print $1}')

if [ -z "$CUR_APK" ]; then
  log "未安装，正在安装"
  pm install -r -g "$APK" 2>&1 | log
elif [ "$NEW_MD5" != "$CUR_MD5" ]; then
  log "版本有更新（$CUR_MD5 -> $NEW_MD5），重新安装"
  pm install -r -g "$APK" 2>&1 | log
  # 重装后需重新拉起，避免停留 stopped 状态
  am start -n "$PKG/.TrampolineActivity" >/dev/null 2>&1
else
  log "已安装且为最新版本（$CUR_MD5）"
fi

# ---- 权限与豁免 ----
pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
log "权限与电池白名单已配置"

# ---- 先拉起一次 ----
am start -n "$PKG/.TrampolineActivity" >/dev/null 2>&1
log "已请求启动常驻服务"

# ---- 看门狗：每 60 秒检查一次，掉线则静默拉起 ----
log "看门狗启动"
(
while true; do
  sleep 60
  if ! pidof "$PKG" >/dev/null 2>&1; then
    log "检测到服务掉线，静默拉起"
    am start -n "$PKG/.TrampolineActivity" >/dev/null 2>&1
  fi
done
) &

# ---- 看门狗：每 60 秒检查一次，掉线则静默拉起 ----
log "看门狗启动"
while true; do
  sleep 60
  if ! pidof "$PKG" >/dev/null 2>&1; then
    log "检测到服务掉线，静默拉起"
    am start -n "$PKG/.TrampolineActivity" >/dev/null 2>&1
  fi
done