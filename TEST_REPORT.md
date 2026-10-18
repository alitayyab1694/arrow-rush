# Arrow Rush automated verification

Run on 18 October 2026 in the project development container.

```text
PASS: 3000 deterministic levels; 73000 verified legal moves;
randomized win paths + invalid-cycle rejection + daily puzzle.
```

Further checks added in the suite:
- Every generated board has a solver-verifiable full legal removal sequence.
- Each of 3,000 levels can be beaten by selecting different randomly legal moves, including Pulse Chain auto-clears.
- At least one Pulse Chain can be triggered along the constructed legal solution for all 3,000 levels.
- A deliberately cyclic four-arrow puzzle is correctly rejected.
- Puzzle IDs have unique cells; daily challenge is solver-verified.
- Java sources for Android are syntax/type checked against lightweight Java method stubs, not the actual Android SDK.

Difficulty observed in 3,000 samples (approximate means):

| Tier | Arrows | Initially legal moves | % immediately legal |
| --- | ---: | ---: | ---: |
| Easy | 12 | 6.9 | 58% |
| Medium | 22 | 9.1 | 42% |
| Hard | 32 | 10.8 | 34% |
| Expert | 40 | 10.9 | 28% |

**Limitations:** The entire Android APK was **not compiled or installed on a physical device here**, because the Android SDK and Gradle artifacts are unavailable in the container. GitHub Actions runs the real Android Gradle compilation and uploads an APK. Build status/device compatibility must be checked from that workflow and on a phone.
