# Zavro Clean — Video Search Diagnostics — Stage 6/7

Task: ZC-VIDEO-DIAG-6. Scope: cross-layer synthetic regression and the known
private results RAM→adapter per-site evidence gap from Stage 4.
Branch: feat/zavro-clean-video-diagnostics-20261108.
Preflight base SHA: a348000b47e7febb6b2612f4a1adf3f7a022a48d.
Authorized by one "Dalej" after Stage 5. No APK.

## Scope and data boundaries

The normal results analyzer is unchanged. The separate private analyzer uses
the authorized RAM-only VideoSearchPrivateRunSnapshot and the exact prefix
submitted to the private results RecyclerView adapter in "Moje witryny".
Per-site observations include deduplicated RAM rows, task-declared results,
submitted adapter rows, and visited pages, without copying any page URL, title,
site name, query or DOM evidence into output fields. Aggregate OBSERVATION and
per-site INFERENCE remain distinct. A partial 150-row page is reported as a
view limit, not as lost data. A missing task, RAM mismatch or interrupted task
is marked explicitly; a completed zero is UNCERTAIN absent page evidence.
The 20,000-row aggregate cap is also UNCERTAIN, never falsely complete.
Local SEEN verdicts are not website/device/pixel proof.

Capture remains opt-in. The UI reads the exact current private tab/profile
and records only inside VideoSearchPrivateSessionRegistry.withAuthorizedTab
after matching the private run's epoch. The native recorder must be currently
attached to exactly the same private owner. Unchanged view frames dedupe;
changing the "show more" page creates a new observation. No new Room write,
clipboard, network upload, file or private preference is added. Private run
snapshot toString is redacted against accidental debugging output.

## Synthetic cross-layer tests

- Real in-memory run/task/film models, site-specific RAM vs adapter counts,
  multi-site attribution and pagination vs loss.
- Missing task, mismatch, STOPPED, RUNNING, zero without independent page
  evidence, wrong order and foreign private epoch must fail/mark uncertainty.
- Opt-in recorder -> synthetic diagnostic events -> bounded RAM JSONL ->
  export manifest; repeated frames deduplicate, private retirement erases
  RAM evidence, no titles/URLs/query/secret tokens appear.
- JavaScript structural guard covers exact tab authorization and UI hook.
- Existing full JS/WebExtension, Kotlin/Robolectric, AndroidTest Kotlin
  compilation, Android lint and frozen lint regression baseline, process-docs,
  final SOURCE summary and PR evidence gates remain mandatory.

The tests are synthetic; no real GeckoView tab, HTML site, CAPTCHA, Android
content chooser, FPS, memory or physical Nothing Phone (1) was exercised.
A successfully closed report is not independently verified site completion.
No APK, merge, public release, force-push, signing/Secrets, ABI or profile
migration. Stage 7 only after a separate "Dalej".
