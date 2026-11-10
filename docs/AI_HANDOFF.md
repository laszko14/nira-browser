# AI_HANDOFF — Zavro Clean MPV/Play, prywatny APK v1228 (10.11.2026)

Kanon linii: `docs/ai/ACTIVE_CLEAN_LINE.md`; archiwum: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. To checkpoint przed CI, nie dowód końcowego sukcesu.

```yaml
task_id: ZC-MPV-PLAY-APK-V1228-20261110
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: f9b84ae636a3ac37fd479ba4c7ae65bb548f2b28
observed_head_sha: 10b1f0056cd7d4caa7703fc09f031aa2242bba57
status: CI_PENDING
acceptance_source: Użytkownik jawnie polecił przygotować podpisany APK do testów po zielonej naprawie diagnostyki MPV/Play, bez merge ani publikacji.
completed: Poprzednia naprawa READY-START-ACK/BASELINE i rejestr błędów zostały zweryfikowane SOURCE 42910257738 i PR 42910266815 na f9b84ae636a3ac37fd479ba4c7ae65bb548f2b28. Poprzedni podpisany v1227 (run 42898317447) potwierdzony zdalnie. Do następnej aktualizacji przygotowano Gradle versionCode base=228, arm64 APK=1228, versionName=0.5.22-alpha124-clean oraz zgodne oczekiwania workflow, nazwę artefaktu i testy kontraktu.
remaining: Zweryfikować commit/push z tego checkpointu, live final HEAD i SOURCE/PR CI na końcowym źródłowym SHA; dopiero po sukcesie wysłać jedną autoryzowaną recenzję PR COMMENT dla dokładnego HEAD i sprawdzić build, walidator oraz artefakt v1228. Zawsze bez pobierania APK przez agenta.
required_ci: SOURCE process-docs + shared-tests (JS + Kotlin/Robolectric + AndroidTest compile + lint/regression) + summary; PR shared-tests (JS + Kotlin/Robolectric + AndroidTest compile + lint/regression) + error-learning evidence; oba na końcowym source SHA zgodnie z odrębnymi checkoutami.
device: NOT_TESTED; nowy protokół READY-START-ACK, BASELINE i ruchome miniatury wymagają rzeczywistego testu na telefonie Nothing Phone (1), Android 15.
apk: PLANNED_PRIVATE_SIGNED_v1228; ostatni zdalnie zweryfikowany v1227, run 42898317447, artifact zavro-arm64-update-apk-v1227, pakiet com.prirai.android.nira.debug, arm64-v8a, certyfikat niezmieniony. Wersja zainstalowana na telefonie nie jest niezależnie zweryfikowana co do versionCode, ale raport wskazuje versionName alpha123.
known_failures: Poprzedni raport v1227 pokazał NO_ACK i zero page events, przyczyna na urządzeniu niepotwierdzona. Nowe SOURCE 42913748802 process-docs FAIL: brak dosłownego shared-tests w polu checkpointu; SOURCE 42913748802 i PR 42913762085 JS FAIL: stary alpha123 w teście video-search-neon-redesign. Obie przyczyny zaklasyfikowane jako fixture/dokumentacja i poprawione w tym zadaniu; wymagają nowego CI.
verification: Stary code SHA f9b84ae636a3ac37fd479ba4c7ae65bb548f2b28 CODE_VERIFIED. Pierwsze v1228 SOURCE 42913748802 i PR 42913762085 FAIL z powodu brakującego shared-tests w checkpointcie i wersji alpha123 w teście; poprawka 10b1f0056cd7d4caa7703fc09f031aa2242bba57 przeszła SOURCE process-docs/JS 42914176395 i PR JS 42914184984; po zapisie błędów konieczne nowe całe SOURCE/PR CI. APK dopiero potem.
verified_before_push: Live branch HEAD, AGENTS.md, ACTIVE_CLEAN_LINE.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md, AI_HANDOFF.md, APK_CHAT_GITHUB_REVIEW_TRIGGER.md, APK_RELEASE.md, ANDROID_SIGNING_SETUP.md, app/build.gradle, workflow, wersjonujące testy i zdalna kontrola podpisanego v1227.
blockers: Brak autoryzowanego APK przed zielonym końcowym CI; nie budować przez niezatwierdzony trigger. Test fizycznego urządzenia pozostaje odrębny.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/140
  - https://github.com/laszko14/nira-browser/actions/runs/42910257738
  - https://github.com/laszko14/nira-browser/actions/runs/42910266815
  - https://github.com/laszko14/nira-browser/actions/runs/42898317447
  - https://github.com/laszko14/nira-browser/actions/runs/42913748802
  - https://github.com/laszko14/nira-browser/actions/runs/42913762085
  - https://github.com/laszko14/nira-browser/actions/runs/42914176395
  - https://github.com/laszko14/nira-browser/actions/runs/42914184984
changed_paths:
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
next_action: Po zielonym CI i niezmienionym HEAD wyślij jedną recenzję COMMENT zgodnie z APK_CHAT_GITHUB_REVIEW_TRIGGER.md: ZAVRO_PRIVATE_APK_REQUEST, sha=HEAD, task=ZC_MPV_PLAY_APK_V1228; po runie sprawdź verifier i artefakt. Bez merge/release.
```

## Zakres tej aktualizacji

Nowy prywatny APK zawiera naprawioną diagnostykę transportu MPV/Play (READY, START, ACK, BASELINE, liczniki diagnostyczne i ZIP transport-v2). Nie dokonano szerokiej zmiany odtwarzania, profili, historii ani podpisu. Dokument `ACTIVE_CLEAN_LINE.md` zachowuje historyczny punkt v1226; ostatni v1227 sprawdzono na żywo.

Nie uznawaj ruchomych miniaturek ani komunikacji GeckoView za DEVICE_VERIFIED bez kolejnego raportu z telefonu.
