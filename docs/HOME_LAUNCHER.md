# Buddy Home (launcher build)

A `launcher` build type makes Buddy a standalone home screen: an app grid with search and a Buddy button, plus all of Buddy.

- Build: `./gradlew assembleFdroidLauncher` -> `app/build/outputs/apk/fdroid/launcher/`
- It keeps Buddy's package name (`app.buddy.assistant`), so it is a full replacement: **do not install the original Buddy alongside it** (Android would treat them as the same app and refuse to update across different signing keys; uninstall the original first).
- Use the `fdroid` flavor: it has no self-updater, which would otherwise offer the maintainer's release (a different signature) over this one.
- Without `BUDDY_KEYSTORE` it is signed with the debug key, so it installs directly. Keep signing with the same key every time, or updates will need an uninstall.
- Pick it under Settings > Apps > Default apps > Home app.

## Native libraries (proot)

Buddy's built-in Linux runs through proot, which is compiled from source into `app/src/main/jniLibs/` (gitignored).
Without it, "Install Claude Code" fails with "Cannot run program". Build it before the APK, and again after a sync
that changes `native/` or `third_party/`:

    ./scripts/build-native.sh

It runs upstream's own `native/build.sh` with the NDK upstream's CI uses (27.2.12479018, in the Android SDK's
`ndk/` folder; install it with Android Studio's SDK Manager on a new machine). On Windows it works around Git Bash and
the NDK's Windows `make`, so no Docker or WSL is needed.

## Building on Windows

Upstream's `app/src/main/assets/linux` and `third_party` are git symlinks, and the scripts must keep LF line endings.
- Without Developer Mode, git checks the symlinks out as small text files. The block at the end of
  `app/build.gradle.kts` detects that and builds from a copy with the links followed.
- Git for Windows converts to CRLF by default, which breaks the bash scripts inside Buddy's Linux. In every clone run:

      git config core.autocrlf false
      git config core.eol lf

## The home screen and Buddy's safety checks

Buddy treats everything in its own package as "Buddy's own screens": agents can't read or tap them, they don't fold
the bubble's panel, and touching them doesn't pause an agent. The home screen is in that package too, so
`app/src/main/java/app/buddy/assistant/Home.java` tells it apart (its root view has the id `buddy_home`) and these
checks treat it as an ordinary app, like any other launcher. Buddy's real screens stay off-limits as before.
Agents still need your approval to act on the home screen, and it can't go on a chat's "Allowed without asking" list.

## Staying in sync with the maintainer

The maintainer's repository is `ghorbelhamdi/buddy-android` (`origin`, `rozeappletree/buddy-android-use`, is a fork).

    ./scripts/sync-upstream.sh     # adds/points the `upstream` remote there, fetches, merges upstream/main
    ./scripts/build-native.sh      # only when native/ or third_party/ changed
    ./gradlew assembleFdroidLauncher

What's local, so you know where a merge conflict can come from:

| Where | What |
|---|---|
| `app/src/launcher/`, `app/src/main/java/app/buddy/assistant/Home.java`, `scripts/`, this file | new files: never conflict |
| `app/build.gradle.kts` | the `launcher` build type, and the symlink fix at the end |
| `Guard.java` (`isBuddy`), `BuddyService.java` (2 lines), `Tools.java` (3 lines) | one-line calls into `Home`, each marked `// local` |

If a merge conflicts in one of those lines, keep upstream's new version of the line and put the `Home` call back in
the same spot (`Home.ownUi(...)` where upstream compares a window's package with Buddy's, `Home.contains(...)` in
`Guard.isBuddy`, `Home.inFront(...)` in `onScreenTouched`). Keep new customizations in new files where possible.
