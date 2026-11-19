# Xiaomi Buds Island

一个 LSPosed 模块，修复**柔光玻璃（透明材质）超级岛**下，小米蓝牙耳机状态通知出现**异常黑色方块**的问题。

> 适用：HyperOS / 澎湃 OS，SystemUI 的超级岛（灵动岛）耳机连接通知。

---

## 问题是什么

超级岛的耳机电量图标并不是 PNG 或 WebP，而是**两个 H.264 视频**：

```
/content://com.xiaomi.bluetooth.fileprovider/internal_files/target/<型号ID>/<型号ID>/ldi_<型号ID>_tws_earphone_left_inear.mp4
```

- 分辨率 `110×110`（部分机型 `132×132`）
- 编码 `H.264 / yuv420p` —— **H.264 不支持 alpha 通道**
- 画面本身是「白色耳机 + 纯黑背景」，四角像素实测为 `(0,0,0)`

也就是说，素材**从一开始就没有透明通道**，黑色背景是它的一部分。原设计依赖「黑底 + 纯黑岛」融为一体，所以看不到黑块。

SystemUI 用 `miui.systemui.widget.TextureVideoView`（继承 `TextureView`）播放它，而整个 SystemUI 中**没有任何 `setOpaque(false)` 调用**，Surface 按不透明合成。于是当你用模块（例如柔光玻璃 / 液态玻璃）把超级岛换成半透明材质后 —— **黑底就露出来了**。

---

## 修复思路

不去改素材文件，而是在运行时把视频「翻译」成带透明通道的动画：

1. Hook `android.view.View#onVisibilityAggregated`（framework 层，必能命中）
2. 通过类名匹配 `miui.systemui.widget.TextureVideoView`
3. 用 `MediaMetadataRetriever` 逐帧取出 `mUri` 指向的视频
4. 用 `ColorMatrixColorFilter` 做**亮度转 alpha**：

   ```
   alpha = 0.299R + 0.587G + 0.114B
   ```

   纯黑背景自然变成全透明，白色耳机保持不透明
5. 生成 `AnimationDrawable`，塞进一个 `ImageView` 覆盖在原视频上方
6. 原 `TextureVideoView` 设 `alpha = 0` 并 `pause()`，停止它继续向 BufferQueue 提交帧

### 为什么改素材文件行不通

| 障碍 | 说明 |
|---|---|
| 编码限制 | H.264 装不下 alpha；换 VP9/HEVC alpha 后 MediaPlayer 通常仍丢弃 alpha 层，且 `TextureView` 是不透明的 |
| 云端覆盖 | 素材在 `files/my_internal_files/target/<型号>/`，由云端下发，重新同步会被还原 |
| 型号差异 | 目录名是设备上报的型号 ID，不同耳机不一样，硬编码必然翻车 |
| 路径隔离 | 该目录在 mount namespace 内被屏蔽，`ls` 会报不存在，需走 `/proc/<pid>/root/` |

所以选择运行时处理，与机型、型号、素材版本全部解耦。

---

## 关键实现细节（踩过的坑）

1. **hook 点选 `View.onVisibilityAggregated`**
   `miui.systemui.widget.TextureVideoView` 在 SystemUI 的**插件 ClassLoader** 里，`findClass` 会 `ClassNotFoundException`。改用 framework 的 `View`，再用 `getClass().getName()` 过滤。

2. **不要用 `setVisibility(INVISIBLE)` 压住原 View**
   这会触发它自己的「不可见」回调，把我们的覆盖层也一起藏掉（现象：黑框没了，图标也没了）。
   → 只用 `setAlpha(0f)`。视觉上完全透明，但不改变可见性，不触发回调。

3. **覆盖层要绑定到各自的 View**
   早期用「清空父容器里所有覆盖层」的写法，左右耳会互相删除重建，导致**多个动画重叠**。
   → `WeakHashMap<View, ImageView>`，一对一绑定，插入位置用 `indexOfChild(v) + 1`。

4. **必须暂停原播放器**
   原视频即使 `alpha=0`，MediaPlayer 仍在逐帧提交。叠加我们的动画等于 **双倍帧流量**，在 Vulkan + 3 buffer 的配置下会明显压缩可用 buffer 空间。
   → 反射调用 `pause()`。

5. **隐藏时释放，不要只设 INVISIBLE**
   停止动画 + 移除 ImageView + 置空 drawable，释放约 1.2 MB 帧内存和绘制提交。

6. **帧缓存**
   `LinkedHashMap` LRU（上限 6 条），避免每次弹出都重新解码。典型只缓 2 条（左右耳）。

---

## 构建

需要 JDK 8+、Android build-tools 的 `d8` 和 `aapt2`。

```bash
# r8.jar 可从 dl.google.com/dl/android/maven2/com/android/tools/r8/<ver>/r8-<ver>.jar 获取
# framework-res.apk 从设备取：adb pull /system/framework/framework-res.apk
./build.sh /path/to/r8.jar /path/to/aapt2 /path/to/framework-res.apk
```

产物：`build-out/XiaomiBudsIsland.apk`

### 关于 aapt2

较新 Android 的 `resources.arsc` 结构变化较大，旧版 `aapt2`（含 Debian 仓库里的 2.19）会报：

```
RES_TABLE_TYPE_TYPE entry offsets overlap actual entry data
```

需要用与目标系统接近的版本。本项目在 arm64 设备上通过 `box64` 运行 x86_64 版 `aapt2` 完成链接。

---

## 安装

1. 安装 APK
2. 在 LSPosed 中启用，作用域勾选 **系统界面（`com.android.systemui`）**
3. 重启 SystemUI 或重启手机

### 手动写入 LSPosed 配置（无人值守场景）

LSPosed 的配置库在 `/data/adb/lspd/config/modules_config.db`（SQLite）。修改前**先停止 lspd**，否则会被内存状态覆盖，并且**改完需要重启手机**让 `system_server` 重新握手：

```bash
# 1. 停 lspd
kill $(pidof lspd)

# 2. 改库
sqlite3 modules_config.db <<'SQL'
INSERT OR REPLACE INTO modules(module_pkg_name, apk_path) VALUES('<pkg>', '<apk路径>');
INSERT OR REPLACE INTO modules_state(module_pkg_name, user_id, enabled, scope_request_blocked)
  VALUES('<pkg>', 0, 1, 0);
INSERT OR IGNORE INTO scope(module_pkg_name, app_pkg_name, user_id)
  VALUES('<pkg>', 'com.android.systemui', 0);
SQL

# 3. 修权限
chown root:root modules_config.db && chmod 600 modules_config.db
restorecon modules_config.db
```

> 只重启 SystemUI 不够，`lspd` 重启后 `system_server` 不会自动重连（`lspctl status` 会显示 `System server: not connected`），必须整机重启。

---

## 已验证

| 项目 | 情况 |
|---|---|
| 透明效果 | 黑框消失，柔光玻璃下耳机动画正常显示 |
| 左右耳独立 | 各自覆盖层，无重叠、无互相删除 |
| 重复弹出 | 帧缓存命中，不重复解码 |
| 岛隐藏 | 动画停止并释放覆盖层 |
| 资源占用 | 无常驻进程；单次解码 25 帧约 30–80 ms |

---

## 已知问题与边界

- **崩溃排查结论**：有用户反馈 SystemUI 崩溃，日志为
  `BLASTBufferQueue::acquireNextBufferLocked failed ... status=-24 (ENOSPC)`，
  发生在 `DynamicIslandWindow` 的 Vulkan 路径。经分析为**图形缓冲区耗尽**，崩溃时内存健康（可用 3.5 GB），与本模块无直接关系；真正的高风险项是同时运行的**抓屏类玻璃模块**（日志中反复出现 `capture failed, falling back to native blur`）。
  本模块已通过「暂停原播放器 + 隐藏时释放」降低帧流量，但无法消除其他模块的 buffer 压力。
- 依赖 `TextureVideoView` 的类名，若 MIUI 后续改名需同步调整。
- 亮度转 alpha 对**灰色/暗色**素材会部分透明；当前耳机素材为白色，不受影响。如需更精确的边缘，可改用色键（chroma key）方案。

---

## 许可证

MIT
