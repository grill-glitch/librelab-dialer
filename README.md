# LibreDialer

Standalone phone / dialer app for Android — extracted from the crDroid
(AOSP/LineageOS) Dialer with an independent package identity.

[中文说明 (README.zh-CN.md)](README.zh-CN.md)

- **Package name:** `org.librelab.dialer` (was `com.android.dialer`)
- **In-call UI (InCallUI):** `org.librelab.incallui` (was `com.android.incallui`)
- **Voicemail:** `org.librelab.voicemail` (was `com.android.voicemail`)
- **Contacts common resources:** `org.librelab.contacts.common` (was `com.android.contacts.common`)
- **Signing:** self-contained `libredialer-release.keystore` (alias `libredialer`),
  exported to Soong as `certs/libredialer.{pk8,x509.pem}` via the
  `android_app_certificate` module `libredialer_cert`.
- **Priv-app whitelist:** shipped with the repo as
  `privapp_whitelist_org.librelab.dialer{,-ext}.xml`, so no framework change
  is needed for the privileged permissions.

## Build (inside an Android ROM tree)

The app is a traditional View-based Android app built by Soong (Dagger 2 /
Glide / AutoValue / protobuf-lite — no Gradle support, hence it stays a
source module rather than a presigned prebuilt like LibreMessage).

```bash
# 1. Add to .repo/local_manifests/libredialer.xml:
#    <remove-project name="crdroidandroid/android_packages_apps_Dialer" />
#    <project name="grill-glitch/librelab-dialer"
#             path="packages/apps/LibreDialer"
#             remote="github" revision="main" />
#    or symlink this checkout into packages/apps/LibreDialer
#    (the module name stays "Dialer", which telephony_product.mk already picks up)

# 2. Build the module (target = your device lunch, e.g. lineage_earth-bp4a-userdebug)
m Dialer
# APK lands at out/target/product/<device>/product/priv-app/Dialer/Dialer.apk
```

The Soong module name is intentionally kept as `Dialer` so
`build/target/product/telephony_product.mk` (and anything else that
references the module by name) keeps working with zero changes.

## System integration points (already handled by this fork)

These framework/Telecomm/Telephony references must point at the new package
for the dialer to act as the default dialer / in-call UI:

| File | Value |
|------|-------|
| `frameworks/base/core/res/res/values/config.xml` → `config_defaultDialer` | `org.librelab.dialer` (via device RRO overlay) |
| `config_priorityOnlyDndExemptPackages` | `org.librelab.dialer` (via overlay) |
| `required_apps_managed_device/user` | `org.librelab.dialer` (via overlay) |
| `packages/services/Telecomm/res/values/config.xml` → `incall_default_class` | `org.librelab.incallui.InCallServiceImpl` |
| `packages/services/Telecomm/res/values/config.xml` → `dialer_default_class` | `org.librelab.dialer.main.impl.MainActivity` |
| `packages/services/Telephony/res/values/config.xml` → `ui_default_package` / `dialer_default_class` | `org.librelab.dialer` / `org.librelab.dialer.main.impl.MainActivity` |
| `packages/apps/Contacts/AndroidManifest.xml` → `<package android:name>` | `org.librelab.dialer` |
| `build/make/target/product/sysconfig/preinstalled-packages-platform-telephony-product.xml` | `org.librelab.dialer` |
| `vendor/lineage/overlay/common/.../vendor_required_apps_managed_{device,user}.xml` | `org.librelab.dialer` |
| ThemeStore icon matcher / Launcher3 grayscale icon map | `org.librelab.dialer` |

The old `com.android.dialer` privapp whitelist in `frameworks/base/data/etc/`
is harmless leftovers (no app carries that package anymore).

## Upstream

Sourced from `crdroidandroid/android_packages_apps_Dialer` (branch `16.0`),
which tracks LineageOS's Dialer fork. License: Apache-2.0 (see LICENSE).
