# AI_HANDOFF — Zavro Clean, aktualizacja podpisana v1227 (10.11.2026)

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

Kanon aktywnej linii: `docs/ai/ACTIVE_CLEAN_LINE.md`. Zawsze odczytuj live HEAD i GitHub Actions; ten dokument powstaje **przed końcowym CI**, a nie jest dowodem jego zakończenia.

```yaml
task_id: ZC-MPV-PLAY-TRANSPORT-APK-1227-20261110
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: 02835a356c7d083c3881864d91a66fac412e5b07
observed_head_sha: 4ee45cb341de9bc92d23166d259db52d4d161ef7
status: CI_PENDING
acceptance_source: User requested "Kompiluj" after verified MPV/Play transport repair; explicit authorization to prepare a private signed update through approved owner PR review after green final SOURCE and PR CI.
completed: MPV/Play transport repair and review fixes SOURCE 42893075830 and PR 42893088565 completed/success on base SHA. Two supplied device diagnostic archives confirm installed v1226. Prepared controlled version increase from arm64 versionCode 1226 to 1227, versionName 0.5.22-alpha123-clean, aligned workflow expected metadata, artifact name and regression expectations; did not change package, ABI, certificate or app playback.
remaining: Push this version-only update on same branch, verify exact final SHA and all SOURCE/PR required jobs success. Only then issue one owner PR review COMMENT with ZAVRO_PRIVATE_APK_REQUEST for that same SHA and task; verify completed signed run, single arm64 artifact, package, versionCode/name and pinned certificate via remote logs and metadata. Do not download APK/ZIP. Device preview playback still needs a new on-phone test.
required_ci: SOURCE process-docs and shared-tests (JavaScript, Kotlin/Robolectric, AndroidTest compile, Android lint/regression baseline), all evidence success on exact final SHA; PR shared-tests and error-learning success on matching integration checkout.
apk: REQUESTED_NOT_BUILT; latest remotely verified signed APK v1226 from run 42864976383, artifact zavro-arm64-update-apk-v1226, source f24e3463a958c83aa2f2347000fbb7705ae8bfa4. Next proposed versionCode 1227, versionName 0.5.22-alpha123-clean, artifact zavro-arm64-update-apk-v1227; no binary download by any agent.
device: NOT_TESTED for updated diagnostic; two older user ZIPs show the installed v1226 produced no page events.
known_failures: v1226 native diagnostic captured marks but no DOM evidence; device cause undetermined. SOURCE run 42895700657 on initial v1227 SHA 4ee45cb341de9bc92d23166d259db52d4d161ef7 failed process-docs (scripts/check-ai-handoff.test.cjs) because checkpoint required_ci omitted the exact token shared-tests, an existing registered recurrent error. Repaired checkpoint to canonical label without weakening test. Other stage code SOURCE/PR on base passed; do not claim preview playback fixed without device report.
verification: Base SOURCE 42893075830 and PR 42893088565 completed/success; latest verified signed v1226 APK run 42864976383. v1227 draft SHA 4ee45cb341de9bc92d23166d259db52d4d161ef7 process-docs failed missing shared-tests token; new corrective SHA and SOURCE/PR CI pending.
verified_before_push: Read AGENTS.md, docs/ai/ACTIVE_CLEAN_LINE.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md and APK review/release/signing policies at exact live base SHA; confirmed existing draft PR #140 and last signed run; HEAD check required again immediately before update.
blockers: Physical device is unavailable for test here; native signing requires owner review and GitHub Actions Secrets, not a local or agent-downloaded APK.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/140
  - https://github.com/laszko14/nira-browser/actions/runs/42893075830
  - https://github.com/laszko14/nira-browser/actions/runs/42893088565
  - https://github.com/laszko14/nira-browser/actions/runs/42864976383
  - https://github.com/laszko14/nira-browser/actions/runs/42895700657
changed_paths:
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - docs/AI_HANDOFF.md
next_action: Check final HEAD, full SOURCE+PR CI on exact version-bump SHA; then initiate exactly one approved owner PR review for private signed v1227 and verify its run and artifact, report link only. No merge, release, force-push or binary download.
```

## Historyczny wynik diagnostyki transportu

- Codex naprawił transport ACK, bezpieczne statusy, reconnect i eksport informacji w commit `efea62184e780cc3ac50261eff3175ebd0885391`; SOURCE 42889451656 i PR 42889463425 zakończone sukcesem.
- Przegląd naprawił podwójne liczenie portu i ponawianie po nowej sesji w commit `02835a356c7d083c3881864d91a66fac412e5b07`; SOURCE 42893075830 i PR 42893088565 zakończone sukcesem.
- Wersja v1226 została rzeczywiście użyta w dwóch raportach z telefonu. Nie dowodzi to poprawności podglądów ani tego, że v1227 będzie działać na witrynie.
