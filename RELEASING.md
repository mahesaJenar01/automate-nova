# Releasing Automate Nova (GitHub Releases + Obtainium)

Installing and updating the app on the phone over the air — no USB cable, no
developer mode, no `adb`. The APK is published as a GitHub Release asset and
Obtainium watches the repo for new ones.

There are two ways to produce that APK. They are interchangeable: both use the
same signing key, so an install from one can be updated by the other.

- **By hand** — `run.bat release X.Y.Z`, then drag the file onto the release
  form. Needs a working Gradle on this machine.
- **In CI** — push a `vX.Y.Z` tag and GitHub Actions builds and publishes it.

## Why a dedicated signing key

Android refuses to update an installed app if the new APK is signed with a
different key. The key in `keystore/release.jks` is therefore permanent:

- **Back it up.** Copy `keystore/release.jks`, `keystore.properties` and the
  password into a password manager or offline storage.
- **Never commit it.** All of it is git-ignored (see the bottom of `.gitignore`).
- If it is lost, every future release must be installed by uninstalling the app
  first, which wipes its data.

## One-time setup

### 1. Install Obtainium on the phone

Get it from <https://github.com/ImranR98/Obtainium/releases> (the
`app-release.apk` asset). Android will ask to allow installing unknown apps
from the browser — that permission is per-app and is not developer mode.

### 2. Add the app in Obtainium

**Add App** → URL `https://github.com/mahesaJenar01/automate-nova` → **Add**.

The repo is public, so no token is needed. Obtainium picks up the latest
release and installs the APK; every later release shows up as an update in the
app list.

### 3. Only if you want CI to build releases too

Add four repository secrets at **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the entire contents of `keystore.base64.txt` (one long line) |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `automate-nova` |
| `KEY_PASSWORD` | same as `KEYSTORE_PASSWORD` |

Releasing by hand does not need these.

## Version numbers

Versions are `MAJOR.MINOR.PATCH`. `versionCode` is derived as
`MAJOR * 10000 + MINOR * 100 + PATCH` (so `1.2.3` → `10203`), which keeps it
monotonic as long as MINOR and PATCH each stay at or below 99. The numbers in
`app/build.gradle.kts` are only fallbacks — both release paths inject the real
ones via `-PversionName` / `-PversionCode`.

Always tag the release `v<version>` so the tag Obtainium reads matches the
`versionName` inside the APK.

## Releasing by hand

Commit everything you want in the build first — then:

```cmd
run.bat release 1.0.0
```

This builds a signed release APK, checks it is *not* debug-signed, and writes
it to `dist\automate-nova-1.0.0.apk`. It never touches your device, so no cable
and no ADB.

Then:

1. Open <https://github.com/mahesaJenar01/automate-nova/releases/new>
2. **Choose a tag** → type `v1.0.0` → **Create new tag: v1.0.0 on publish**
3. Title `v1.0.0`, and click **Generate release notes** if you want a changelog
4. Drag `dist\automate-nova-1.0.0.apk` onto *"Attach binaries by dropping them
   here or selecting them"* and wait for the upload to finish
5. **Publish release**

Then pull to refresh in Obtainium.

Publishing that way also creates the tag, which starts the **Release APK**
workflow. It will notice the release already carries
`automate-nova-1.0.0.apk` and leave your upload alone, so the two paths do not
fight. (If you have not added the signing secrets, that run simply fails and
can be ignored — your release is already published.)

## Releasing from CI instead

Commit and push, then:

```bash
git tag v1.0.1
git push origin main --tags
```

The **Release APK** workflow builds, signs, verifies the signature, and
publishes the release with `automate-nova-1.0.1.apk` attached. You can also
trigger it from the **Actions** tab: **Release APK → Run workflow →** type
`1.0.1`, which creates the tag for you.

## Checking a build without releasing

The **Build check** workflow compiles a debug APK on every push to `main` and
on pull requests, and uploads it as a run artifact.

## Note on local release builds

`keystore.properties` points `run.bat release` at the local key. If that file
is missing the release build falls back to the debug key — `run.bat release`
refuses to run in that case, because a debug-signed APK can never update a
properly signed install.
