# iOS Style Launcher: Lock mode and Lost phone setup (Samsung Galaxy A16)

Both features are **optional and off by default**. Everything here can be undone.

---

## Part 1: Install the launcher

1. Copy `iOSLauncher.apk` to the phone (USB, Google Drive, email to yourself, etc.).
2. Open it from **My Files**. When asked, allow **Install unknown apps** for that app
   (Settings > Apps > (the app you opened it with, e.g. My Files or Chrome) > Install unknown apps > Allow).
3. If Play Protect warns that the app is unrecognised, tap **More details > Install anyway**. It is an unsigned debug build, so this is expected.
4. Open **iOS Style Launcher**. Then go to **Settings > Apps > Choose default apps > Home app** and pick **iOS Style Launcher**.
   (You can also long-press an empty spot on the home screen > **Settings** > **Set as Default Home App**.)

---

## Part 2: Lock mode (device owner) – optional

Lock mode uses Android's *device owner* feature. When it's on:
- the launcher **cannot be uninstalled**,
- it is **always the Home app**, even after a reboot (persistent preferred HOME activity),
- optional **Kiosk** pins the launcher (lock task) and only lets apps on your Home Screen open.

You can turn it off at any time in the launcher's **Settings** (behind your launcher PIN), including
**Remove device owner**, so you can never get stuck.

### Important limitation
Android only allows `dpm set-device-owner` when **no accounts are on the phone** (Google, Samsung, etc.).
You remove them temporarily, run the command, and then add them back. **Nothing is wiped. You don't need a factory reset.**

> Before you start, make sure you know your **Google** and **Samsung account passwords**. You'll need them to sign back in.
> Also make sure photos and contacts are synced or backed up.

### Step 1: Turn on USB debugging on the A16
1. **Settings > About phone > Software information** > tap **Build number** 7 times > enter your PIN. "Developer mode has been turned on."
2. **Settings > Developer options** > turn on **USB debugging**.

### Step 2: Install Android platform-tools (adb) on a computer
- Windows / Mac / Linux: download "SDK Platform-Tools" from
  https://developer.android.com/tools/releases/platform-tools and unzip it.
- Open a terminal or Command Prompt **inside the unzipped `platform-tools` folder**.
  (On Windows use `adb.exe`; on Mac/Linux use `./adb`.)
- Mac shortcut: `brew install android-platform-tools`.

### Step 3: Connect the phone
1. Plug the phone in with a USB cable. On the phone, accept **Allow USB debugging?** (tick "Always allow").
2. Run:
   ```
   adb devices
   ```
   You should see one device listed as `device` (not `unauthorized`).

### Step 4: Install the APK (if it isn't installed already)
```
adb install -r iOSLauncher.apk
```

### Step 5: Temporarily remove accounts
On the phone: **Settings > Accounts and backup > Manage accounts**. Remove **every** account:
Google, Samsung account, and any others (WhatsApp, Microsoft, email, etc.).
- Samsung may ask for your Samsung account password to remove it.
- Removing an account from the phone **does not delete** the account or its cloud data.

Check that none are left (shows what Android still sees):
```
adb shell dumpsys account | grep -i "Account {"
```
If nothing prints, you're ready. (On Windows, run `adb shell dumpsys account` and look for `Account {` lines.)

### Step 6: Make the launcher device owner
```
adb shell dpm set-device-owner com.matiassoto.ioslauncher/.admin.AdminReceiver
```
Expected output: `Success: Device owner set to package ComponentInfo{com.matiassoto.ioslauncher/com.matiassoto.ioslauncher.admin.AdminReceiver}`

Common errors:
- `Not allowed to set the device owner because there are already some accounts on the device`
  means an account is still there. Go back to Step 5. On some Samsung builds a hidden account remains. Check
  `adb shell dumpsys account` and remove what it lists, or sign out of Samsung Pass / Samsung Cloud.
- `already several users on the device` means you need to remove Secure Folder, Dual Messenger, or the guest user first.
- `Unknown admin` means the APK isn't installed or the package name is mistyped.

### Step 7: Add your accounts back
**Settings > Accounts and backup > Manage accounts > Add account**. Add Google, Samsung, and the rest.
Device owner stays set after you add accounts.

### Step 8: Turn Lock mode on
1. On the home screen, long-press an empty spot > **Settings** (top-left).
2. **Security > Set Launcher PIN** (4–8 digits). **Don't forget this PIN.**
3. **Lock mode** section: "Device owner" should say **Yes**. Toggle **Lock mode** on and enter your PIN.
4. Optional: turn on **Kiosk (pin launcher)**. While it's on, only apps on your Home Screen can be opened.

### How to undo
- **Turn Lock mode off:** launcher Settings > Lock mode toggle off (PIN).
- **Remove device owner completely:** launcher Settings > **Remove device owner** (PIN) > Remove.
  This clears the uninstall block and the forced Home app, then calls `clearDeviceOwnerApp`.
  After that you can uninstall the launcher normally.
- If you've forgotten the PIN, **you can't remove device owner from the launcher**. Android doesn't allow
  `adb shell dpm remove-active-admin` for a device owner that isn't a test-only app, so the remaining fallback is a factory reset.
  Write the PIN down somewhere safe.
- Turn off **USB debugging** again when you're done if you like (Developer options).

---

## Part 3: Lost phone mode – optional

When it's on, the phone answers text messages from **any** number that contain your secret code word:

| Text this to the phone | What happens |
|---|---|
| `<codeword> LOCATE` | Replies by SMS with a Google Maps link to its current location (GPS, falls back to network/last known), accuracy, and battery % |
| `<codeword> RING` | Plays a loud alarm at **max volume on the alarm stream** (plays even in silent/vibrate mode) for 60 s, with a full-screen **Stop alarm** button |

Example: code word `Tango42` means you text `Tango42 LOCATE`. Upper or lower case doesn't matter.

A **"Lost phone mode is on"** notification stays visible the whole time the feature is enabled.

### Turn it on
1. Launcher **Settings > Lost phone > Lost phone mode** toggle on. Enter your PIN, then set your **code word** (4–32 letters or digits, no spaces).
2. Grant the permissions it asks for: **SMS** (receive and send), **Location**, **Notifications**, then **Location > Allow all the time**
   (needed so it can answer while the screen is off).
3. Tap the **Permissions** row to check that everything shows ✓.
4. Use **Test alarm** to hear what RING does.
5. From another phone, text `<codeword> LOCATE` to make sure it replies.

### "Restricted setting" on Android 13+ (sideloaded apps)
Android blocks SMS permissions for apps installed from an APK until you allow them:
1. Try to grant SMS once. You'll see **"Restricted setting"**.
2. Go to **Settings > Apps > iOS Style Launcher** and tap the **⋮** menu (top-right) > **Allow restricted settings**. Confirm with your PIN or fingerprint.
   (The launcher's Settings has an **Open App info** shortcut.)
3. Go back and toggle **Lost phone mode** again or tap **Permissions**. SMS can now be granted.
   If the **⋮** menu doesn't appear, open the permission screen once first, then check again.

### Samsung battery settings (recommended)
Settings > Apps > iOS Style Launcher > **Battery > Unrestricted**, so Android doesn't put it to sleep.

### Primary backup: turn these on too
Lost phone mode is an extra. These are the main tools:
- **Google Find My Device:** Settings > Google > All services > **Find My Device** > On (and "Find your offline devices").
  Use https://www.google.com/android/find or the Find My Device app from another phone.
- **Samsung Find (SmartThings Find / Find My Mobile):** Settings > Security and privacy > **Lost device protection > Find My Mobile** > On,
  and turn on **Remote unlock**, **Send last location**, and **Offline finding**. Use https://smartthingsfind.samsung.com.
- Keep **Location** on (quick settings) and keep a SIM or data connection for SMS.

### Limits
- SMS LOCATE needs a cellular signal. If location is turned off on the phone, it replies with only the battery level.
- Do Not Disturb set to block alarms can mute RING. Alarms are allowed by default on Samsung.
- If someone switches the phone off or removes the SIM, nothing can reply. Use Find My Device / Samsung Find for the last known location.
- Every text containing the code word is answered, so keep the code word secret. You can change it any time (PIN-protected).

### Turn it off
Launcher Settings > Lost phone mode toggle off (PIN). The service and its notification stop immediately.
