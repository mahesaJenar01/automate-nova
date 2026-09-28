# Automate Nova

An Android Accessibility Service application designed to automate repetitive UI interactions within the Nova app. It drastically streamlines data entry workflows by navigating through historical visit screens, filtering for specific brands, searching for products, automatically inputting item quantities using simulated human typing, and submitting daily sellout reports—all without manual intervention.

## Features

* **Automated Navigation**: Automatically detects target app screens and navigates to the necessary update forms.
* **Smart UI Interaction**: Scrolls dynamically to locate targets and relies on accessible text, content descriptions, and bounds.
* **Human-like Typing Simulation**: Inputs text digit-by-digit while mimicking clipboard pastes to reliably trigger Android `TextWatcher` events.
* **Flexible Workflows**: Employs a state machine (`UpdateDataWorkflow`) to sequentially process tasks.

## Install on a phone (no cable)

Releases are built and signed by GitHub Actions and published as GitHub
Releases; [Obtainium](https://github.com/ImranR98/Obtainium) installs and
updates them over the air. See [RELEASING.md](RELEASING.md) for the one-time
setup and how to cut a release. The short version:

```cmd
run.bat release
```

It numbers the build itself — the version in `version.properties` with its
patch stepped by one — and prints the file it wrote into `dist\`. Drag that
onto a new GitHub Release tagged `v<that version>`.

## Quick Start (cable / ADB)

1. **Build the Application**
   You can easily build the app directly using the provided batch script:
   ```cmd
   run.bat
   ```
   This script will:
   - Build the APK with the permanent release signing key and the version in
     `version.properties`.
   - Update the existing app without removing its data.
   - Restart the accessibility service and stream logs.

   If the phone still has an older debug-signed copy, Android requires a
   one-time migration: export from **Cadangan Data**, uninstall Automate Nova,
   run `run.bat`, and import the backup. Every later signed build can update in
   place.

2. **Enable Accessibility**
   To let this app interact with your device, you must enable its Accessibility Service in your Android settings (or let `run.bat` auto-enable it if your device supports secure setting modifications via ADB).

3. **Check Logs**
   The application uses the `NovaAutomation` log tag. You can monitor the automation workflow state with:
   ```bash
   adb logcat -s NovaAutomation
   ```

## Requirements
* Android device with Developer Options / ADB enabled (only for the `run.bat` workflow; Obtainium installs need neither).
* Android Accessibility privileges granted to the application.
