# Arrow Rush: Pulse Escape (Android game prototype)

A lightweight native Android puzzle game, drawn entirely with a hardware-accelerated `Canvas` (no large 3D engine or bitmap assets). Features:

- **Verified puzzle generator**: deterministic reverse-construction + DFS solver; every generated board has a proven solution.
- **Four difficulty tiers**: Easy, Medium, Hard, Expert; levels repeat this sequence in a 12-level progression cycle.
- **Unique Pulse Chain mechanic**: gold-marked arrows automatically launch up to two legal matching-colour escapes. Every tested level has at least one reachable chain in a valid playthrough.
- **Daily challenge** seeded by UTC date.
- **Lightweight modern vector art**: low-poly facets, premium dark UI, touch animation particles, haptics, no external texture assets.
- **Opt-in rewarded AdMob hints**: first 2 free, then a test rewarded ad; reward granted only after Google's successful reward callback. Ad unavailable does not make any puzzle unsolvable.
- **Progress, stars, coins, skins** stored locally; no account or network required for base game.
- **No forced interstitial ads**.
- **Shop** uses earned in-game coins; **real-money IAP are not yet implemented** in this prototype.

## Important: Prototype vs Play Store ready

This is a **playable development prototype**, not a reviewed or production-signed Play Store release. The Android build has not been run in this execution environment (Android SDK and Gradle downloads are unavailable here). GitHub Actions will run the automated puzzle checks, build the debug APK and publish the APK as an artifact; build and device QA still need to succeed before release.

Before publishing:
1. Replace the **test AdMob application ID** in `app/src/main/AndroidManifest.xml` and **test rewarded ad unit ID** in `RewardedHints.java` with your verified AdMob IDs. Only serve production units in approved production builds.
2. Implement Play billing if monetizing paid skins/booster packs; no purchase buttons falsely charge users in this version.
3. Build a signed `release` AAB, configure Google Play Console, produce store art/screenshots/privacy policy and accurate Data safety disclosures.
4. Review ad consent requirements, age targeting, regional privacy rules and Google Play policies.
5. Physically test low-end Android phones, visual scaling, AdMob hint availability, and purchase restore if added later.
6. Avoid claiming the app guarantees organic installs. Early retention and screenshot/icon experiments are essential.

## Build without Android Studio (via GitHub Actions)

1. Create a **new, empty GitHub repository** named e.g. `arrow-rush` (don't put it inside your HeyAssist app repository).
2. Upload the **contents** of this project folder including hidden `.github/workflows/android.yml` to the repository root. On Windows, enable showing hidden files so `.github` isn't omitted.
3. Open GitHub **Actions** > **Build Arrow Rush Android APK** > **Run workflow**. Pushes to `main` also trigger builds.
4. The workflow validates **3,000 deterministic puzzle levels**, 73,000 legal moves, randomized playthroughs, and at least one achievable Pulse Chain on each level before building the APK.
5. Download the `ArrowRush-debug-APK` workflow artifact, unzip it, and install `app-debug.apk` on your Android device (enable installation from your file manager if prompted).

Optional local build if Android SDK, JDK17 and Gradle8.13 are available:

```bash
gradle :app:assembleDebug
```

## Solver invariant

The generator picks occupied cells then visits them in an order whose projected position increases from a chosen corner. Each inserted arrow must have an unobstructed ray through **all already inserted pieces**. The reverse of this insertion order is therefore always a legal complete solution. A separate memoized DFS solver validates the finished level, so corrupted/generated deadlocked layouts are rejected. Every legal move on these acyclic boards preserves solvability.

To test the pure engine without the Android SDK:

```bash
mkdir -p build/engine
javac -d build/engine app/src/main/java/com/revolution/arrowrush/PuzzleEngine.java tests/EngineCheck.java
java -cp build/engine EngineCheck
```

## Current controls

- Tap a non-blocked arrow to move it off the board.
- Gold point on arrow indicates a **Pulse** piece, potentially triggering two more valid moves of the same colour.
- The hint button highlights a guaranteed valid next arrow. After two free hints, it presents an optional rewarded test ad if inventory is loaded.
- Restart at any time; levels always regenerate identically from the same seed.
- Level map unlocks following completion, with star and coin rewards.
- Daily puzzle is consistent across UTC calendar dates.

## Product scope still worth improving

- More polished time-based animation of arrows flying beyond the board and dynamic rescue mini-city visuals.
- Play Billing + production-safe server-side purchase verification.
- User privacy/consent flow, robust ad retry, accessibility labels and localized copy.
- Retention telemetry, crash reporting, adjustable sound, difficulty playtesting, full on-device QA.
- Press kit and Play Store marketing assets.
