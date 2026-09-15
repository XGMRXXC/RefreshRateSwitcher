# MIUIX Compose / Compose 运行时在 AAR 内自带 consumer rules；
# 这里只补充本应用特有的保留项。

# 反射/序列化无关，但保留 Java 侧被 Kotlin 直接调用的公开 API 更稳妥
-keep class com.dsh.refreshswitch.ModeUtil { *; }
-keep class com.dsh.refreshswitch.ModeUtil$Mode { *; }

# 组件由 Manifest 引用，AGP 会自动保留；此处兜底
-keep class com.dsh.refreshswitch.SwitchService { *; }
-keep class com.dsh.refreshswitch.RestartJobService { *; }
-keep class com.dsh.refreshswitch.BootReceiver { *; }
-keep class com.dsh.refreshswitch.LauncherActivity { *; }
-keep class com.dsh.refreshswitch.TrampolineActivity { *; }
-keep class com.dsh.refreshswitch.MainActivity { *; }
-keep class com.dsh.refreshswitch.PanelActivity { *; }
