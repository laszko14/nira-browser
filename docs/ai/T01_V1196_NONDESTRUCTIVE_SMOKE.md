# T01/16 — niedestrukcyjny test Zavro Clean v1196 na telefonie

**Status:** FOLLOWUP_PENDING — user's concise report: all three requested basic smoke checks worked. Report logged UTC `2026-11-01T01:05:55.350Z` (receipt timestamp, not a measured start/end of physical test). Follow-up not before `2026-11-15T01:05:55.350Z`. No physical steps run by an agent.
**Pilot:** `docs/ai/PILOT_16.json`, T01 (`T01-GECKO-V1196`, GECKO/MEDIUM), started UTC `2026-11-01T00:53:46.368Z`.
**Phone:** Nothing Phone (1), Android 15, 3-button navigation, existing installed Clean v1196 **reported by user**.
**Build evidence:** branch `build/zavro-clean-post19-gecko-g4c-update-v1196-20261101` @ `717608d1af23638614499d25dae7682f16a7fd8a`; [G4C CI run 41250929267](https://github.com/laszko14/nira-browser/actions/runs/41250929267) completed/success on this SHA (push, attempt 1). That is **code/packaging evidence, not proof that this exact binary is installed**.

## Wynik T01 — zgłoszenie użytkownika (bez niezależnych logów)

Po zadaniu obejmującym **trzy** niedestrukcyjne próby z sekcji poniżej użytkownik odpowiedział: **„Wszystko działa”**. Zachowujemy to jako **USER_REPORTED_3_OF_3_OK**, nie jako niezależny pomiar w debuggerze ani test Gecko natywnie:
- system Back po zmianie testowej strony: **OK według użytkownika**;
- przełączanie dwóch zwykłych kart testowych: **OK według użytkownika**;
- powrót z ekranu głównego przez ostatnie aplikacje: **OK według użytkownika**.

**Brak osobnej obserwacji:** dokładny versionCode z ekranu urządzenia, opcjonalne gesty paneli, zbieranie logów, kompatybilność wszystkich profili, wewnętrzny restart WebExtension, izolacja P/Q, trwałe usuwanie danych i wyścig metadanych A→B. Wersja v1196 to uprzednie zgłoszenie użytkownika, a commit `717608d1af23638614499d25dae7682f16a7fd8a` odnosi się do źródła buildu G4C, nie do poświadczonego hasha zainstalowanego pakietu.

**Werdykt:** podstawowy smoke użytkownika 3/3 PASS (self-report); pełne DEVICE_VERIFIED czterech ustaleń **NIE**. T01 zapisany jako `FOLLOWUP_PENDING` w 14-dniowym oknie obserwacji. Regresje, liczba ponownych otwarć i statystyki interwencji pozostają `null` (NOT_MEASURED), nie 0. Brak nieautoryzowanych działań destrukcyjnych.

## Acceptance — first device smoke (no logs, no account/private data)

1. **Version**: On the handset, in Android Settings → Apps → Zavro Clean → App info (or the app's About screen), confirm if displayed `0.5.22-alpha101-clean` and code `1196`. If the device does not expose the code, say **NOT_VISIBLE**, do not guess.
2. **Navigation**: In the already installed Zavro Clean, use an ordinary, non-private tab to visit `https://example.com` (only the reserved test domain). Open `https://example.org` in the **same tab**, then use the system **Back** button (3-button navigation). Expected: return to `example.com` without app crash; note if site/network unavailable. Do not sign in or share URLs with personal tokens.
3. **Tabs/background**: Open a second *temporary* ordinary tab at `https://example.org`. Switch between the two test tabs, press Android Home, then return via recent apps. Expected: app remains responsive, correct selected tab and page; reloading due to memory can be reported separately. **Do not** force-stop app, change its data/settings, remove existing tabs or profiles.
4. **Panels (optional quick UI check)**: Open and close left/right panels; tap a normal tab again. Note gesture conflicts or freezes. This is a separate UI observation, **not** a Gecko native-level test.

**Short reply from user:** `wersja: 1196 / inna / niewidoczna; Back: OK / błąd; karty i powrót: OK / błąd; panele: OK / błąd / pominięte`. For errors, state the reproducible steps in a sentence, no screenshots of secrets.

## Safety gates and limitations

- No APK build/install, no reinstall, no ADB, no `pm clear`, `force-stop`, cache clearing, profile deletion, cookies removal, hidden debug setting or signing changes.
- **ZC-AUD-002** (background WebExtension restart with *same* native EngineSession), **030** (cookie/localStorage/IndexedDB P/Q isolation), **031** (destructive durable erase) and **043** (A→B media metadata ordering) are **NOT TESTED** by these steps. Their 19B statuses remain unchanged. App background ≠ WebExtension-only restart; generic page navigation ≠ metadata A→B evidence.
- If all three mandatory smoke steps are observed and captured, only the **bounded smoke scenario** may be marked device-observed, not all Gecko/device requirements. The user's pre-existing report that v1196 updated and kept settings remains a report, not a direct independent measurements of isolated storage or signing.
- If a step might affect private data or requires a destructive operation, **STOP** instead. Never run the old G4D `preflight-device.sh` requiring installed 1195 against already upgraded 1196.
- `DEVICE_VERIFIED` for the four audit IDs remains NOT_TESTED/BLOCKED until separate trusted device evidence. Any later ZC-AUD-031 deletion test needs its own explicit authorization and isolated test profile.
- Record actual observations first, check evidence and success criteria, then transition T01 to `FOLLOWUP_PENDING` as applicable. The 14-day observation field is **not** started merely by opening T01.

## Completion boundaries

This commit only registers T01 and validates the pilot record by CI. No physical execution is claimed. Do not overwrite the G4C branch `docs/AI_HANDOFF.md` (it is legacy and stale for process tracking). The process branch `docs/AI_HANDOFF.md` is current **only on the process branch**.

In case of a physical crash or any data anomaly, refrain from further trials and report the exact benign steps. Do not attempt to recover by clearing app data.
