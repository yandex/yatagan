# Handoff: Yatagan KCP backend — browser-ui migration & performance work

Date: 2026-07-16. Previous session: KCP backend hardening, browser-ui PoC migration,
SPI/IC support, KSP-vs-KCP benchmarking, performance tracing.

## Repos and branches

| Repo | Path | VCS | Branch | State |
|---|---|---|---|---|
| yatagan | `/Users/bacecek/work/yatagan/yatagan` | git | `users/bacecek/kcp` | pushed to fork `github.com/bacecek/yatagan` (remote `fork`, `remote.pushDefault=fork`; `origin`=yandex/yatagan — do NOT push there). Commits: `be680b72` PoC, `d4501073` hardening. **Uncommitted on top**: annotation caching (lang/kcp: KcpMembers, KcpTypeDeclarationImpl, KcpAnnotationImpl — `by lazy` wrapper lists) + per-stage timing instrumentation with wall+CPU (processor/kcp: KcpGraphValidation, YataganCompilerPlugin). |
| browser-ui | `/Users/bacecek/work/browser/browser/client/src/yandex/android/ui` | **arc** (not git) | `wp/ABRO-90026/kcp` | See "browser state" below — user reverted/updated parts after the session. |
| kotlin, metro | siblings under `/Users/bacecek/work/yatagan/` | — | — | reference clones (metro's `compiler/.../ir/ic.kt` = LookupTracker pattern source). |

## Read these first (do not re-derive)

- `/Users/bacecek/work/yatagan/yatagan/KCP_SUPPORT.md` — full status doc: what works, verification (corpus 86/0/25, DivKit, browser-ui), remaining gaps vs KAPT/KSP, consumer setup.
- Memory files (auto-loaded): `kcp-browser-ui-migration.md`, `kcp-divkit-integration.md`, `kcp-backend-research.md` — pitfalls, fix history, publish loop.
- gradle-profiler config: `/Users/bacecek/work/junk/junk/bacecek/gradle-profiler-configs/ksp_vs_kcp.conf`; user's results in `~/Downloads/ksp_vs_kcp/` (benchmark.csv).

## Browser state at handoff — VERIFY WITH `arc status` FIRST

The user appears to have reverted/synced buildSrc to upstream after benchmarking:
- `YataganComponentsPlugin.kt` — back to the original KSP/KAPT `AnnotationProcessor` path; the
  `use_kcp` branch and KCP subplugin application are GONE (also gained a new upstream arg
  `yatagan.experimental.reportDuplicateAliasesAsErrors`).
- `BuildParams.kt` — the `use_kcp` param is gone.
- `base-project.gradle.kts` — my unconditional `api-compiled → api-public` substitution replaced
  by an upstream RT-gated variant with `TODO(jeffset)`.
- `AliceDialogDependencies.kt` / `AliceFragmentComponent.kt` — `internal` visibility restored
  (these were made public because a now-Kotlin public component exposed them; if KCP wiring
  returns, this resurfaces as a compile error).

Unknown at handoff: whether the 14 Java→Kotlin root-component conversions, `val` entry-point
conversions, and `lib-bro` sourceset hardcode are still in the working copy. Check `arc status`
/ `arc diff` before assuming anything. Do not "fix back" reverted files without asking — the
revert was deliberate (likely trunk sync or PR prep).

## Performance findings (searchapp module, canaryDebug)

- KSP mode: ksp task ~17s + kotlin ~1s. KCP mode: kotlin ~11s total.
- Yatagan share of the 11s (post annotation caching): extension total ≈ 4.9s =
  graph 3.8s wall / **2.8s CPU** + validate 0.46s + spi 0.17s + icRecord 0.26s + emit 0.16s.
  Rest ≈ 6s is kotlinc itself (frontend, lowering/writing generated classes).
- Annotation caching gained ~1s graph + halved validate/spi. Corpus stayed green.
- JFR profiling: no single hotspot left — cost is spread over ~10.4k class resolutions
  (HashMap/FqName/Name churn). Notable shared-core frames: `validateNoLoops`,
  `GraphBindingsManager.getBindingFor` (optimizing those helps KSP/KAPT too).
- gradle-profiler end-to-end: incremental scenarios KCP ~8-9% faster, clean builds a wash.
- Timing lines: `./gradlew :searchapp:compileCanaryDebugKotlin --debug 2>&1 | grep "yatagan.*timing"`.
  Lines only appear on non-incremental module recompiles (daemon filters verbose messages in IC
  runs); force with a plugin-jar republish or `-Pkotlin.incremental=false` (the latter rebuilds
  the whole tree, ~8m). A no-op probe edit (add+remove same text) compiles nothing.

## Gotchas that cost time before (see memory for full list)

- Republish loop: `GRADLE_USER_HOME=$HOME/.gradle ./gradlew :processor:kcp:publishToMavenLocal`
  (from the yatagan repo). Republishing dirties every yatagan module in browser (~2-8m build).
- Never run `b build` and another Gradle invocation concurrently on the browser checkout —
  corrupts intermediates (ASM transform NoSuchFileException).
- `b build -n` runs compile tasks only — packaging-level checks (duplicate classes etc.) only
  surface under `assembleCanaryDebug`.
- Corpus regression check: `GRADLE_USER_HOME=$HOME/.gradle ./gradlew :testing:tests:test
  --tests "...CoreBindingsTest" --tests "...ConditionsTest"` (+AssistedInjectTest/MultiBindingsTest
  when touching those areas).

## Open threads / candidate next steps

1. Decide fate of uncommitted yatagan changes (annotation caching + instrumentation): commit &
   push to fork; possibly make timing lines removable/flag-gated before any upstream PR.
2. Delete scratch `lang/kcp/src/test/.../KcpJavaSourceDebugTest.kt` before upstream PR (already
   committed in d4501073).
3. Further perf (design-level, if user wants): cache interned types/graph fragments across root
   components per compilation; look at shared-core `validateNoLoops`.
4. Remaining KCP gaps (KCP_SUPPORT.md §Known gaps): Gradle options DSL, source locations on
   messages, dagger-compat (won't fix), Java root components (won't fix).
5. Browser: if KCP wiring is to return, re-apply YataganComponentsPlugin KCP branch + use_kcp
   param + validators on `kotlinCompilerPluginClasspath` (isTransitive=false) — exact shapes in
   memory file `kcp-browser-ui-migration.md`.

## Suggested skills

- `loop` — the user drives long fix-verify cycles with it ("/loop until X works"); use when they
  ask for iterate-until-green work.
- Ponytail mode (lazy-senior-dev output style) was active all session: shortest working diff,
  no speculative abstractions, minimal prose. Keep following it unless told otherwise.

No secrets/credentials in this doc; auth for GitHub is via `gh` (account: bacecek).
