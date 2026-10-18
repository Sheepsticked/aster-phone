# Aster Phone

A small Android SIP softphone for one self-hosted Asterisk server, such as an Asterisk-based GSM gateway,
reached over a LAN or a VPN.

- Several SIP accounts at once, each with its own registration status. The dialer shows the account calls go
  through at the top; tapping it opens a scrollable list of all accounts, and the choice is remembered
- Receives calls in the background without push notifications or Google services
- Incoming call screen over the lock screen, mute, speaker, hold, DTMF keypad
- Calls are registered with Android's Telecom service: answering a mobile call puts the SIP call on hold,
  and Bluetooth headsets, watches and cars can answer or end it
- Call history with call back through the same account
- A setup checklist for the Android and Samsung battery settings that would otherwise stop background calls

Calls use G.711 (PCMA/PCMU) over UDP or TCP. TLS, video and messaging are out of scope.

## How it stays reachable

The app keeps a foreground service running (shown as a quiet "Ready for calls" notification) so Android
doesn't stop the process that holds the SIP registrations. For this to survive on a phone, the app must be
allowed to run in the background. The **Setup** tab lists each setting and opens the right screen for it.

On Samsung phones, also add the app to **Settings → Battery → Background usage limits → Never sleeping apps**.

## Building

Requirements: JDK 17 or newer and the Android SDK (platform 37).

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug testDebugUnitTest
```

The APK is written to `app/build/outputs/apk/debug/`. The unit tests include Compose UI tests that run on the
JVM through Robolectric and save a PNG of every screen to `app/build/screenshots/`.

Release builds are signed when a properties file exists at `~/.android-keys/asterphone-signing.properties`
(or the path given with `-Pasterphone.signing=...`) containing `storeFile`, `storePassword`, `keyAlias` and
`keyPassword`. Keep that file and the keystore outside the repository.

## Server tips

- The app registers again as soon as the phone's network changes, so the server learns the new address at
  once. With `max_contacts = 1` on the AOR, also set `remove_existing = yes`; otherwise Asterisk refuses the
  registration from the new address (403 Forbidden) until the old one expires, and calls go nowhere meanwhile:

  ```ini
  [phone-aor](!)
  type = aor
  max_contacts = 1
  remove_existing = yes
  qualify_frequency = 300
  ```

- Each phone ping from Asterisk (`qualify_frequency` on the AOR) and each TCP keepalive
  (`keep_alive_interval` in `[global]`) wakes the phone. Intervals of a few minutes are kinder to the battery
  than the 30–60 s often used on desk phones.
- The account's "Re-register every" setting defaults to 600 seconds. Longer values save battery; network
  changes don't wait for it.
- To use TCP, the server needs a TCP transport, for example:

  ```ini
  [transport-tcp]
  type = transport
  protocol = tcp
  bind = 0.0.0.0:5060
  ```

## License

GPL-3.0-or-later, see [LICENSE](LICENSE). The app uses the [Linphone SDK](https://linphone.org), which is
licensed under GPL-3.0. The icons are from Google's [Material Icons](https://github.com/google/material-design-icons)
(Apache-2.0).
