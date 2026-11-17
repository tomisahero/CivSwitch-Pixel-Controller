# CivSwitch Pixel HID Controller (experimental)

Android root app for a Pixel with `CONFIG_USB_CONFIGFS_F_HID=y`, using the 87-line `payload.txt` from the user-supplied RecurCIV v1.1.0 source archive. **Not tested on physical hardware.**

## Build an APK without a local toolchain

1. Create a GitHub repository and upload the **contents** of this folder (including `.github/workflows/build-apk.yml`). GitHub's web upload may hide dot-directories; verify `.github/workflows/build-apk.yml` appears in the repository.
2. Open **Actions → Build Android APK → Run workflow** (or push to `main`).
3. After the workflow succeeds, download artifact `CivSwitch-Pixel-Controller-debug-apk`, extract its ZIP, and install `app-debug.apk` on the rooted Pixel.
4. Grant root when prompted. Initialize HID with the Pixel connected to the Switch 2. Set the speed and checkpoint size, start the run, and manually confirm at each checkpoint.
5. Tap **Restore original Android USB** after stopping/finishing. Keep an alternate recovery path available in case the USB gadget becomes inaccessible.

## Behavior and limitations

- Uses the original 87 text lines without altering payload contents; USB keyboard uses **US layout**.
- Sends key-down and key-up reports; sends Enter after each line. Progress counts reports submitted to `/dev/hidg0`, **not acceptance by Civilization VII**.
- Adjustable key duration, message pause, manual checkpoints, pause/resume, stop and best-effort USB restore.
- Setup temporarily unbinds and rebinds Android's active `g1` gadget and adds `hid.civkbd` to `configs/b.1`. Some Android USB stacks dynamically rebuild gadgets or use a different active config; setup can fail and must be debugged on-device.
- No persistence or recovery after app crash/reboot; root USB restore is best-effort and may require disconnect/reconnect or reboot.
- Do **not** use while transferring files over USB. USB setup can interrupt ADB, MTP, charging negotiation, or other USB functions.
- This is a **debug APK** (not a Play Store release) built and signed by Android build tools.
- Checkpoints require a human to inspect the game's chat; no feedback channel confirms the game processed a message.

## Safety

Root USB gadget operations can break USB connectivity until reboot. Do not use the app while the phone is attached to a computer doing file transfers. The application does not modify the Switch Lite's SD card.

## Source and licensing

Payload copied verbatim from user-provided `RecurCIV-v1.1.0-source.zip` (`payload.txt`). Original source archive contains license and attribution files; see `THIRD_PARTY.md`. This project is not affiliated with the original authors.
