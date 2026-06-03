# RetroSprite 正式 APK 签名与 GitHub Release 发布指南

本文用于生成给普通用户长期下载的 Android APK。结论先说清楚：

- 普通用户长期下载应使用 **release-signed APK**，不要使用 debug-signed APK。
- 同一个 Android 应用后续升级必须继续使用同一把 release key；丢失密钥会导致已安装用户无法直接升级。
- `.jks`、`.keystore`、`keystore.properties` 和真实密码永远只保存在本地或密码管理器里，不能提交到 GitHub。
- GitHub Release 可以直接发布 APK；Google Play 才更偏向 AAB。

## 1. 生成本地 release keystore

首次正式发布前，在 Android 项目目录执行：

```bash
cd retrosprite-android
./scripts/generate_release_keystore.sh
```

脚本会调用 `keytool` 生成：

```text
release/retrosprite-release.jks
keystore.properties
```

`keytool` 会要求输入 keystore 密码。请使用强密码，并把 `.jks` 文件和密码备份到可靠位置。

生成后打开 `keystore.properties`，填写真实密码：

```properties
storeFile=release/retrosprite-release.jks
storePassword=你的本地强密码
keyAlias=retrosprite-release
keyPassword=你的本地强密码
```

也可以不使用 `keystore.properties`，改用环境变量：

```bash
export RETROSPRITE_RELEASE_STORE_FILE=/absolute/path/to/retrosprite-release.jks
export RETROSPRITE_RELEASE_STORE_PASSWORD='...'
export RETROSPRITE_RELEASE_KEY_ALIAS=retrosprite-release
export RETROSPRITE_RELEASE_KEY_PASSWORD='...'
```

## 2. 构建正式签名 APK

发布前使用 clean build，避免大 asset 或 ASR 模型切换后 APK 中留下异常旧产物空洞：

```bash
cd retrosprite-android
TAG=v0.1.0 ./scripts/build_release_apk.sh
```

脚本会执行：

```text
./gradlew :app:clean :app:testDebugUnitTest :app:assembleRelease
apksigner verify --print-certs
shasum -a 256
```

为避免本地出包时卡在 Android `lintVital*`，脚本默认跳过 release lint。需要把 lint 也纳入同一轮发布构建时执行：

```bash
RUN_LINT=1 TAG=v0.1.0 ./scripts/build_release_apk.sh
```

成功后输出：

```text
app/build/release-artifacts/RetroSprite-v0.1.0-release.apk
app/build/release-artifacts/RetroSprite-v0.1.0-release.apk.sha256
app/build/release-artifacts/RetroSprite-v0.1.0-release.apk.certs.txt
```

如果只是本地快速验证脚本，可临时跳过单元测试：

```bash
SKIP_TESTS=1 TAG=v0.1.0 ./scripts/build_release_apk.sh
```

正式发布不建议跳过测试。

## 3. 发布到 GitHub Releases

在代码已经合并到 `main`，并确认版本号、README、CHANGELOG 都已更新后发布：

```bash
gh release create v0.1.0 \
  app/build/release-artifacts/RetroSprite-v0.1.0-release.apk \
  app/build/release-artifacts/RetroSprite-v0.1.0-release.apk.sha256 \
  --repo MightyKartz/RetroSprite \
  --target main \
  --title "RetroSprite v0.1.0" \
  --notes-file app/build/release-artifacts/release-notes-v0.1.0.md
```

正式 release 不要加 `--prerelease`。如果 APK 仍是 debug-signed，只能作为 preview / prerelease。

## 4. 版本升级规则

每次正式发布前检查：

- `versionCode` 必须递增，否则 Android 不会把它当作升级包。
- `versionName` 应与 GitHub tag 对齐，例如 `0.1.0` 对应 `v0.1.0`。
- 继续使用同一把 release keystore。
- 运行 clean build，尤其是改过 ASR 模型、大型 assets、native libs 或 GKP bundled assets 后。
- 上传 APK 和 `.sha256`；保留本地 certs 输出用于发布审计。

## 5. 普通用户安装说明

用户从 GitHub Release 下载 `RetroSprite-*-release.apk` 后，可以：

- 在 Android 设备上直接打开 APK，并允许浏览器或文件管理器“安装未知来源应用”。
- 或通过 adb 安装：

```bash
adb install -r RetroSprite-v0.1.0-release.apk
```

同一签名的后续正式版本可以覆盖安装升级。debug-signed preview 包和 release-signed 正式包之间通常不能直接覆盖安装；需要卸载旧包或使用相同签名路线。

## 6. 密钥安全红线

- 不要把 release keystore 发到聊天、issue、PR、release asset 或网盘公开链接。
- 不要在命令历史里直接暴露密码；优先使用本地 `keystore.properties` 或安全的 CI secret。
- 不要频繁更换 release key。
- 更换签名会影响普通用户升级路径；只有在明确接受卸载重装成本时才这么做。
