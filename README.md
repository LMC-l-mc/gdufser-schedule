# gdufser-schedule

完全离线的 Android 课程表应用：无账号、无服务器、无广告、无网络请求，数据只保存在你的设备上。

## 功能

- **课表管理**：创建/编辑/复制/切换/删除课表，设置学期开始日期、总周数与每周第一天
- **作息时间管理**：默认七个大节（08:30 起），支持多方案、时间段排序与重叠校验
- **课程管理**：课程名称/教师/地点/备注/颜色，多条上课安排，周次规则（每周/单周/双周/自定义）
- **今日页**：正在上课、下一节、今日课程与明日预览
- **周课表**：星期 × 大节网格，跨大节跨行显示，重叠课程分栏，今日列高亮
- **课程卡片**：折叠备注（1–2 行），点击"备注"打开约 40% 高度的底部编辑框
- **外观设置**：浅色/深色/跟随系统、动态取色（Android 12+）、卡片圆角/间距/透明度、课表行高、信息显示开关
- **备份与导出**：JSON 全量备份/恢复（新建/合并/覆盖），ICS 日历导出，全程离线
- **多语言**：简体中文 / 繁体中文 / English

## 截图

（截图位置：`docs/screenshots/`。构建并安装后可在真机截屏放入该目录。）

## 构建

环境：JDK 21、Android SDK（compileSdk 35）。

```bash
# 单元测试
./gradlew :app:testDebugUnitTest

# Debug APK
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk

# Release APK(已启用 R8 混淆与资源裁剪)
./gradlew :app:assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

> Windows 本地注意：若项目位于含中文的路径，单元测试进程可能因类路径编码问题失败（`ClassNotFoundException`）。请通过 ASCII 目录联接执行构建，或把项目移至纯英文路径。仓库内已配置 `-Dfile.encoding=UTF-8` 缓解。

发布签名：当前 Release 使用调试签名（本地发布够用）。正式发布请在 `app/build.gradle.kts` 中替换为自己的 keystore。

## 数据与隐私

- 全部数据（课表/作息/课程/设置）存储在应用私有 Room 数据库 `timetable.db`，不出设备。
- 无任何网络权限与第三方 SDK：不采集、不上传、无广告、无埋点。
- 系统备份：`timetable.db` 随 Android 系统备份/迁移（方便换机）。
- 备份文件（JSON）由你主动导出到自选位置，请自行妥善保存，其中包含课程备注等个人信息。

## 架构

- Kotlin + Compose Material 3（单 Activity）、MVVM + Kotlin Coroutines + Flow
- Room（`timetable.db`）、DataStore（预留）
- Koin 依赖注入、Navigation Compose
- 纯 Kotlin 领域层（周次计算、冲突检测、备份格式、ICS 生成）为未来 Kotlin Multiplatform 迁移预留

## CI

GitHub Actions（.github/workflows/ci.yml）：单元测试 + Debug/Release APK 构建，产物上传为构建工件。