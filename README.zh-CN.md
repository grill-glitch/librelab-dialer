# LibreDialer

独立的 Android 电话/拨号应用——从 crDroid(AOSP/LineageOS)Dialer 提取,
使用独立的包名身份。

[English (README.md)](README.md)

- **包名:** `org.librelab.dialer`(原 `com.android.dialer`)
- **通话界面 (InCallUI):** `org.librelab.incallui`(原 `com.android.incallui`)
- **语音信箱:** `org.librelab.voicemail`(原 `com.android.voicemail`)
- **联系人公共资源:** `org.librelab.contacts.common`(原 `com.android.contacts.common`)
- **签名:** 仓库自带 `libredialer-release.keystore`(别名 `libredialer`),
  通过 `android_app_certificate` 模块 `libredialer_cert` 导出为
  `certs/libredialer.{pk8,x509.pem}` 供 Soong 使用。
- **Priv-app 白名单:** 随仓库携带
  `privapp_whitelist_org.librelab.dialer{,-ext}.xml`,特权权限无需改 framework。

## 构建(在 Android ROM 树内)

本应用是传统 View 架构,由 Soong 构建(Dagger 2 / Glide / AutoValue /
protobuf-lite——不支持 Gradle,因此保持源码模块而非像 LibreMessage 那样
的 presigned 预编译)。

```bash
# 1. 在 .repo/local_manifests/libredialer.xml 中添加:
#    <remove-project name="crdroidandroid/android_packages_apps_Dialer" />
#    <project name="grill-glitch/librelab-dialer"
#             path="packages/apps/LibreDialer"
#             remote="github" revision="main" />
#    或将本目录软链接到 packages/apps/LibreDialer
#    (Soong 模块名保持 "Dialer",telephony_product.mk 已自动引用)

# 2. 构建模块(目标 = 你的设备 lunch,如 lineage_earth-bp4a-userdebug)
m Dialer
# APK 输出到 out/target/product/<device>/product/priv-app/Dialer/Dialer.apk
```

Soong 模块名刻意保持为 `Dialer`,这样
`build/target/product/telephony_product.mk`(以及任何按模块名引用它的地方)
零改动即可工作。

## 系统集成点(本 fork 已处理)

以下 framework/Telecomm/Telephony 引用必须指向新包名,拨号器才能成为
默认拨号器/通话界面:

| 文件 | 值 |
|------|-----|
| `frameworks/base/core/res/res/values/config.xml` → `config_defaultDialer` | `org.librelab.dialer`(通过设备 RRO overlay) |
| `config_priorityOnlyDndExemptPackages` | `org.librelab.dialer`(通过 overlay) |
| `required_apps_managed_device/user` | `org.librelab.dialer`(通过 overlay) |
| `packages/services/Telecomm/res/values/config.xml` → `incall_default_class` | `org.librelab.incallui.InCallServiceImpl` |
| `packages/services/Telecomm/res/values/config.xml` → `dialer_default_class` | `org.librelab.dialer.main.impl.MainActivity` |
| `packages/services/Telephony/res/values/config.xml` → `ui_default_package` / `dialer_default_class` | `org.librelab.dialer` / `org.librelab.dialer.main.impl.MainActivity` |
| `packages/apps/Contacts/AndroidManifest.xml` → `<package android:name>` | `org.librelab.dialer` |
| `build/make/target/product/sysconfig/preinstalled-packages-platform-telephony-product.xml` | `org.librelab.dialer` |
| `vendor/lineage/overlay/common/.../vendor_required_apps_managed_{device,user}.xml` | `org.librelab.dialer` |
| ThemeStore 图标匹配 / Launcher3 灰度图标映射 | `org.librelab.dialer` |

`frameworks/base/data/etc/` 中旧的 `com.android.dialer` privapp 白名单是
无害残留(已无任何应用使用该包名)。

## 上游

源自 `crdroidandroid/android_packages_apps_Dialer`(分支 `16.0`),
其跟踪 LineageOS 的 Dialer fork。许可证:Apache-2.0(见 LICENSE)。
