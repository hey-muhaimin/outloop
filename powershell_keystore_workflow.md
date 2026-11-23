# Outloop — Windows PowerShell Upload Keystore & Release Signing Workflow

This guide walks through generating your permanent Google Play upload keystore on Windows, configuring `app/build.gradle.kts` signing environment variables without committing secrets, building signed release artifacts (`app-release.aab` and `app-release.apk`), and verifying signatures before uploading to Google Play Console.

> **Important Security Rules**
> - **Never commit** `*.jks`, `*.keystore`, or `.env` files containing passwords to Git (all are ignored in `.gitignore`).
> - **Store a redundant offline backup** of your upload keystore and both passwords in a secure password manager. If you lose the upload key and Play App Signing is enabled, you must request an upload key reset via Google Play Support.
> - **Confirm `applicationId` first**: `app/build.gradle.kts` is configured as `com.suprbuild.outloop` (`versionCode = 7`, `versionName = "7.0"`).

---

## 1. Prerequisites Check (Windows PowerShell)

Verify Java 21 (or JDK 17+) and `keytool` are available in PowerShell:

```powershell
cd E:\Dev\OpenSource\outloop
java -version
keytool -help | Select-Object -First 3
```

Confirm your working repository is clean and Gradle wrapper files (`gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) are present:

```powershell
git status
Test-Path .\gradlew.bat
```

---

## 2. How `app/build.gradle.kts` Reads Signing Credentials

Outloop's `release` signing configuration is already wired to environment variables with a fallback path:

```kotlin
signingConfigs {
  create("release") {
    val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    storeFile = file(keystorePath)
    storePassword = System.getenv("STORE_PASSWORD")
    keyAlias = "upload"
    keyPassword = System.getenv("KEY_PASSWORD")
  }
}
```

- **`KEYSTORE_PATH`**: Full path to your `.jks` file (recommended outside the repo or at `E:\Dev\OpenSource\outloop\my-upload-key.jks`).
- **`keyAlias`**: Must be **`upload`** (matches `app/build.gradle.kts` and `scripts/build-release.sh`).
- **`STORE_PASSWORD` / `KEY_PASSWORD`**: Provided via PowerShell environment variables at build time.

---

## 3. Generate the Permanent Upload Keystore

Recommended location outside cloud-synced folders (for example `%USERPROFILE%\.android\keystores\outloop-upload-key.jks`):

```powershell
$KeystoreDir = Join-Path $env:USERPROFILE ".android\keystores"
New-Item -ItemType Directory -Force -Path $KeystoreDir | Out-Null
$KeystorePath = Join-Path $KeystoreDir "outloop-upload-key.jks"

keytool -genkeypair -v `
  -keystore $KeystorePath `
  -alias upload `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

When prompted by `keytool`:
1. Enter a strong **keystore password** (save it in your password manager).
2. Enter your **First and Last Name / Organization** details (e.g., `CN=Outloop, O=Suprbuild, C=US` or your legal developer details).
3. Press **Enter** to use the same password for the key password, or enter a dedicated key password (and record it).

Verify the newly generated keystore entry and SHA-256 fingerprint:

```powershell
keytool -list -v -keystore $KeystorePath -alias upload
```

Record the **`SHA256`** certificate fingerprint in your release notes/password manager for future reference.

---

## 4. Load Signing Variables into the Current PowerShell Session

Avoid hardcoding passwords in terminal history or files by prompting securely in PowerShell:

```powershell
$env:KEYSTORE_PATH = Join-Path $env:USERPROFILE ".android\keystores\outloop-upload-key.jks"

$storeSec = Read-Host "Enter STORE_PASSWORD" -AsSecureString
$keySec   = Read-Host "Enter KEY_PASSWORD (press Enter if same as STORE_PASSWORD)" -AsSecureString

$env:STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
  [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storeSec)
)
$keyPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
  [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySec)
)
$env:KEY_PASSWORD = if ([string]::IsNullOrEmpty($keyPlain)) { $env:STORE_PASSWORD } else { $keyPlain }
```

Verify the keystore file exists before starting the build:

```powershell
Test-Path $env:KEYSTORE_PATH
```

---

## 5. Run Verification & Build Signed Release AAB + APK

Run clean tests, release lint, Compose mapping check, Android App Bundle (`.aab` for Google Play), and signed release APK (`.apk` for local sideload testing):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintVitalRelease :app:reportReleaseComposeMappingErrors :app:bundleRelease :app:assembleRelease
```

Expected output artifacts:
- **Play Store Bundle**: `app\build\outputs\bundle\release\app-release.aab`
- **Local Sideload APK**: `app\build\outputs\apk\release\app-release.apk`
- **R8 Mapping File** *(upload to Play Console for deobfuscation)*: `app\build\outputs\mapping\release\mapping.txt`

---

## 6. Verify Signatures & Package Metadata

### Verify `.aab` Signature (`jarsigner`)
```powershell
jarsigner -verify -verbose -certs .\app\build\outputs\bundle\release\app-release.aab | Select-String -Pattern "jar verified|CN=|SHA"
```
Confirm the output ends with **`jar verified.`** and the signer DN matches your `upload` certificate.

### Verify `.apk` v2/v3 Signature (`apksigner`)
If Android SDK build-tools is installed under `%LOCALAPPDATA%\Android\Sdk\build-tools`:

```powershell
$BuildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name -Descending | Select-Object -First 1
& (Join-Path $BuildTools.FullName "apksigner.bat") verify --verbose --print-certs .\app\build\outputs\apk\release\app-release.apk
```

Confirm **`Verifies`** is `true` and the SHA-256 digest matches your `keytool -list -v` output.

### Optional: Verify Package ID & Version Code (`aapt2`)
```powershell
& (Join-Path $BuildTools.FullName "aapt2.exe") dump badging .\app\build\outputs\apk\release\app-release.apk | Select-String -Pattern "package:|application-label:|uses-permission:|sdkVersion|targetSdkVersion"
```
Confirm:
- `package: name='com.suprbuild.outloop' versionCode='7' versionName='7.0'`
- `application-label:'Outloop'`

---

## 7. Optional: Export Play App Signing Public Certificate / Encrypted Key

When uploading `app-release.aab` to a brand-new app in Google Play Console:
1. Under **Setup → App signing**, choose **Let Google manage and protect your app signing key** (recommended default for new apps).
2. Google Play automatically generates the Play App Signing key on Google's infrastructure and trusts your `upload` key from the first uploaded `.aab`.
3. Only if you ever want to export the public certificate for API allowlists or verification:

```powershell
keytool -export -rfc `
  -keystore $env:KEYSTORE_PATH `
  -alias upload `
  -file .\upload_certificate.pem
```

*(Delete `upload_certificate.pem` or avoid committing it if not needed in the repository.)*

---

## 8. Clear Session Secrets When Done

Before closing or sharing your terminal session, clear the passwords from environment memory and verify `git status` shows no untracked keystores:

```powershell
Remove-Item Env:\STORE_PASSWORD, Env:\KEY_PASSWORD, Env:\KEYSTORE_PATH -ErrorAction SilentlyContinue
git status
```
