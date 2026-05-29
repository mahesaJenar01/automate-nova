# Automate Nova

An Android Accessibility Service application designed to automate repetitive UI interactions within the Nova app. It drastically streamlines data entry workflows by navigating through historical visit screens, filtering for specific brands, searching for products, automatically inputting item quantities using simulated human typing, and submitting daily sellout reports—all without manual intervention.

## Features

* **Automated Navigation**: Automatically detects target app screens and navigates to the necessary update forms.
* **Smart UI Interaction**: Scrolls dynamically to locate targets and relies on accessible text, content descriptions, and bounds.
* **Human-like Typing Simulation**: Inputs text digit-by-digit while mimicking clipboard pastes to reliably trigger Android `TextWatcher` events.
* **Flexible Workflows**: Employs a state machine (`UpdateDataWorkflow`) to sequentially process tasks.

## Quick Start

1. **Build the Application**
   You can easily build the app directly using the provided batch script:
   ```cmd
   run.bat
   ```
   This script will:
   - Build the APK via Gradle.
   - Uninstall any previous versions of the app (if any).
   - Install the new APK to an attached ADB device.
   - Restart the accessibility service and stream logs.

2. **Enable Accessibility**
   To let this app interact with your device, you must enable its Accessibility Service in your Android settings (or let `run.bat` auto-enable it if your device supports secure setting modifications via ADB).

3. **Check Logs**
   The application uses the `NovaAutomation` log tag. You can monitor the automation workflow state with:
   ```bash
   adb logcat -s NovaAutomation
   ```

## Requirements
* Android device with Developer Options / ADB enabled.
* Android Accessibility privileges granted to the application.
