# LibreLab Dialer — Settings 功能实现 TODO

> 交付标准：所有设置项均可正常工作，对应实际系统行为。
> 基于 `~/android/crdroid/packages/apps/LibreDialer` 源码对照。

---

## 当前状态

Settings UI 已完成（10项根设置 + 多级子页面），
但所有 toggle/列表值仅写入 `SharedPreferences`，无任何实际逻辑。

---

## TODO

### 1. 声音和振动 (`SoundsAndVibrationSettings`)

| 设置项 | prefs key | crDroid 源码对应 | 需要的实现 |
|--------|-----------|-----------------|-----------|
| 铃声 | `ringtone_uri` | `DefaultRingtonePreference` | 调用 `RingtoneManager.ACTION_RINGTONE_PICKER`，用户选择后回调保存 URI |
| 响铃时振动 | `vibrate_on_ring` | `Settings.System.VIBRATE_WHEN_RINGING` | 写 `Settings.System.VIBRATE_WHEN_RINGING`，需 `Settings.System.canWrite()` 权限检查 |
| 拨号键盘音 | `dtmf_tone_enabled` | `Settings.System.DTMF_TONE_WHEN_DIALING` | 在 DialpadActivity 读取此值，按键时决定是否播放 DTMF 音 |
| 拨号键盘音时长 | `dtmf_tone_length` | `Settings.System.DTMF_TONE_TYPE_WHEN_DIALING` | 短(0)/长(1)，DialpadActivity 根据此值控制 DTMF 持续时间 |
| 通话中启用勿扰 | `incall_dnd` | `incall_enable_dnd` | 通话中监听来电，根据此值将通话加入 DND；需 `NotificationManager POLICY_ACCESS` 权限 |
| 呼出电话接通时振动 | `incall_vibrate_outgoing` | `incall_vibrate_outgoing_key` | InCallService 监听通话状态，接通时触发 Vibrator |
| 呼叫等待振动 | `incall_vibrate_call_waiting` | `incall_vibrate_call_waiting_key` | 同上，CallWaitingListener 触发 |
| 挂断时振动 | `incall_vibrate_hangup` | `incall_vibrate_hangup_key` | InCallService 监听 DISCONNECTED 状态触发 |
| 通话结束前 45 秒振动 | `incall_vibrate_45` | `incall_vibrate_45_key` | InCallService 启动 45 秒倒计时，DISCONNECTED 时取消 |
| **自动开始录音** | `recording_auto_start` | `call_recording_autostart_key` | InCallService 监听 CALL_STATE_CONNECTED，启动 CallRecorderService |
| 录音格式 | `recording_format` | `call_recording_format_key` (0=OGG/1=AAC/2=AMR) | CallRecorderService 根据此值选择 AudioEncoder |
| **智能静音** | `smart_mute` | `button_smart_mute` | 注册 ProximitySensor 监听，手机面朝下/靠近耳朵时自动静音 |

### 2. 辅助拨号 (`AssistedDialingSettings`)

| prefs key | crDroid 源码对应 | 需要的实现 |
|-----------|-----------------|-----------|
| `assisted_dialing_enabled` | `assisted_dialing_setting_toggle_key` | `AssistedDialingMediator` 在 DialpadActivity 拨号前介入 |
| `assisted_dialing_country` | `assisted_dialing_setting_cc_key` | 枚举国家代码列表 (`CN=+86` 等)，拨号前缀插入逻辑 |

**关键类**：`AssistedDialingMediator`、`CountryCodeProvider`
**触发时机**：Dialpad 按下 Call 按钮 → 号码传给 TelecomManager 之前 → 插入国家前缀

### 3. 来电归属查询 (`LookupSettings`)

| prefs key | crDroid 源码对应 | 需要的实现 |
|-----------|-----------------|-----------|
| `forward_lookup` / `forward_lookup_provider` | `enable_forward_lookup` / `forward_lookup_provider` | `LookupProvider` 在拨号盘输入时实时查询（HTTP 请求） |
| `reverse_lookup` / `reverse_lookup_provider` | `enable_reverse_lookup` / `reverse_lookup_provider` | `ReverseLookupProvider` 在来电响铃时查询来电者信息 |

**提供商**：`Google`、`Yandex`、`OpenCNAM`（通过 HTTP API）
**隐私注意**：需在隐私政策中披露，号码发送至第三方

### 4. 反垃圾短信 (`AntiSpamSettings`)

| prefs key | crDroid 源码对应 | 需要的实现 |
|-----------|-----------------|-----------|
| `anti_spam_enabled` | `antispam_enabled` | `CallScreeningService` 全局开关 |
| `anti_spam_block_high_risk` | `antispam_block_1` | 号码风险评分 API（如有） |
| `anti_spam_block_real_estate` | `antispam_block_2` | 来电号码正则匹配 |
| `anti_spam_block_ads` | `antispam_block_3` | 同上 |
| `anti_spam_block_delivery` | `antispam_block_5` | 同上 |
| `anti_spam_block_loan` | `antispam_block_6` | 同上 |
| `anti_spam_block_education` | `antispam_block_13` | 同上 |
| `anti_spam_block_repair` | `antispam_block_14` | 同上 |
| `anti_spam_block_insurance` | `antispam_block_21` | 同上 |

**实现方式**：`CallScreeningService` 收到来电 → 读取 prefs → 决定 `reject()` 或 `allow()`
**权限**：`android.permission.READ_PHONE_STATE`、`android.permission.ANSWER_PHONE_CALLS`

### 5. 其他设置 (`OtherSettings`)

| prefs key | crDroid 源码对应 | 需要的实现 |
|-----------|-----------------|-----------|
| `post_call_enabled` | `enable_post_call` | 通话结束 (`CALL_STATE_DISCONNECTED`) → 显示通话记录浮层 |
| `proximity_sensor_disabled` | `disable_proximity_sensor` | InCallActivity 通话中禁用距离传感器（防止贴耳黑屏失效）|

### 6. 语音信箱 (`VoicemailSettings`)

| prefs key | crDroid 源码对应 | 需要的实现 |
|-----------|-----------------|-----------|
| `visual_voicemail` | `voicemail_visual_voicemail_key` | `VoicemailClient` API 注册，VVM 通知 |
| `vvm_auto_archive` | `voicemail_visual_voicemail_archive_key` | 已读 VVM 自动归档 |

**系统 Intent**：`ACTION_SHOW_VOICEMAIL` / `ACTION_CHANGE_VOICEMAIL_SETTINGS`

### 7. 显示选项 (`DisplayOptionsSettings`)

| prefs key | crDroid 源码对应 | 需要的实现 |
|-----------|-----------------|-----------|
| `sort_order` | `display_options_sort_list_by_key` | `ContactsContract.Contacts.SORT_KEY_ALTERNATIVE` vs 默认排序 |
| `name_format` | `display_options_view_names_as_key` | `ContactsContract.DisplayName` GIVEN_NAME / FAMILY_NAME 优先 |

**应用位置**：ContactsScreen 和 CallLogScreen 的排序/显示逻辑

### 8. 短信回复 (RespondViaSms)

系统 Intent `ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS` → 无需自实现，但需确认 intent 可正常跳转。

### 9. 通话设置 / 通话账户

系统 Intent `ACTION_SHOW_CALL_SETTINGS` / `ACTION_CHANGE_PHONE_ACCOUNTS` → 无需自实现。

### 10. 黑名单号码

`TelecomManager.createManageBlockedNumbersIntent()` → 系统 Intent，无自实现。

### 11. 无障碍 (TTY/HAC)

系统 Intent `ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS` → 无需自实现。

---

## 实现优先级建议

1. **P0 — 核心通话体验**：拨号键盘音 (DTMF)、响铃振动、距离传感器
2. **P1 — 通话辅助**：通话录音、辅助拨号、反垃圾短信
3. **P2 — 增强功能**：来电归属查询、智能静音、可视语音信箱
4. **P3 — 细节打磨**：通话后记录、显示排序、铃声选择

---

## 已知依赖

- `CallScreeningService` — 来电过滤（需系统签名或特殊权限）
- `InCallService` — 通话中控制（需 BIND_TELECOM_CONNECTION_SERVICE）
- `NotificationManager` DND — 通话中勿扰（需 POLICY_ACCESS）
- `Settings.System` 写权限 — 铃声/振动/DTMF（需 `Settings.System.canWrite()`）
- `SharedPreferences` — 所有设置持久化（已实现）
