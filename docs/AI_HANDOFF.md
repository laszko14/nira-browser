# AI_HANDOFF — Zavro Clean MPV/Play V2, aktualizacja APK v1229 (11.11.2026)

Kanon: `docs/ai/ACTIVE_CLEAN_LINE.md`. Archiwum: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`. To checkpoint PRZED końcowym CI; `CI_PENDING` nie zastępuje weryfikacji na żywo.

```yaml
task_id: ZC-MPV-PLAY-V2-APK-V1229-20261111
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: cc8a72feb13b9eb45c1f0a9e1992579a45f52ee4
observed_head_sha: cc8a72feb13b9eb45c1f0a9e1992579a45f52ee4
status: CI_PENDING
acceptance_source: Użytkownik 11.11.2026 polecił sprawdzić ukończoną naprawę MPV Play i, przy pomyślnym wyniku, skompilować podpisane APK aktualizacyjne.
completed: Przed buildem potwierdzono etapy 1-6 V2, SOURCE run 43017903847 i PR run 43017913061 completed SUCCESS na cc8a72feb13b9eb45c1f0a9e1992579a45f52ee4. Ostatni zweryfikowany APK v1228 z run 42916351673; podniesiono wyłącznie wersję do 1229 (Gradle base 229, 0.5.22-alpha125-clean), workflow i nazwę artefaktu. Nie zmieniano logiki MPV/Play, GeckoView, podpisu, profili, historii ani danych.
remaining: Zweryfikować końcowy SOURCE i PR CI dla nowego SHA; po PASS wysłać jedną owner COMMENT review PR 140 z dokładnym SHA, zgodnie z APK_CHAT_GITHUB_REVIEW_TRIGGER.md; sprawdzić build, log verify-update-apk.sh i pojedynczy artefakt, bez pobierania APK ani ZIP.
required_ci: SOURCE process-docs + shared-tests JavaScript, JVM/Robolectric, AndroidTest compile, lint i lint-regression + summary; PR shared-tests + error-learning-evidence; wszystkie SUCCESS dla finalnego source SHA.
device: NOT_TESTED — Nothing Phone (1), Android 15; D01-D12 z docs/ai/MPV_PLAY_V2_STAGE6_DEVICE_TEST.md wymagają rzeczywistego sprawdzenia.
apk: PENDING — v1229, versionCode 1229, versionName 0.5.22-alpha125-clean, com.prirai.android.nira.debug, arm64-v8a, dotychczasowy certyfikat. Nie deklaruj APK_VERIFIED bez runu i logu podpisanego weryfikatora.
known_failures: Historyczne NO_ACK oraz problem Play i ruchomych miniaturek nie zostały sprawdzone na urządzeniu. Synthetic tests/CI nie potwierdzają ich rozwiązania. Closed Shadow DOM, nieobsługiwane ramki i DRM pozostają granicami obserwacji.
verification: SOURCE https://github.com/laszko14/nira-browser/actions/runs/43017903847 i PR https://github.com/laszko14/nira-browser/actions/runs/43017913061 potwierdzone dla cc8a72feb13b9eb45c1f0a9e1992579a45f52ee4; ostatni zweryfikowany build v1228 https://github.com/laszko14/nira-browser/actions/runs/42916351673.
verified_before_push: Bezpośrednio sprawdzono live HEAD cc8a72feb13b9eb45c1f0a9e1992579a45f52ee4, PR 140, AGENTS.md, ACTIVE_CLEAN_LINE.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md, APK_CHAT_GITHUB_REVIEW_TRIGGER.md, APK_RELEASE.md, Gradle i testy kontraktu.
blockers: Nie ma dowodu testu telefonu ani nowszej zainstalowanej wersji. v1229 jest wyższa niż najnowszy zweryfikowany v1228. Ostateczna akceptacja wymaga finalnego CI i bezpiecznego podpisu w Actions.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/43017903847
  - https://github.com/laszko14/nira-browser/actions/runs/43017913061
  - https://github.com/laszko14/nira-browser/actions/runs/42916351673
  - https://github.com/laszko14/nira-browser/pull/140
changed_paths:
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - docs/AI_HANDOFF.md
next_action: Po zielonym końcowym SOURCE i PR CI autoryzować owner COMMENT review dla v1229, sprawdzić run, podpis, ABI i artefakt. Nie deklarować DEVICE_VERIFIED bez telefonu.
```

## Granice interpretacji

`START_ACK` potwierdza transport, `top_frame_baseline_confirmed` potwierdza migawkę TOP, a `page_events_observed` zauważone interakcje. Żaden z tych stanów samodzielnie nie dowodzi naprawy odtwarzania Play ani miniaturek.
