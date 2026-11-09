'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '../../main/java/com/prirai/android/nira/videosearch');
const sites = fs.readFileSync(path.join(root, 'MyVideoSitesActivity.kt'), 'utf8');
const diagnostic = fs.readFileSync(path.join(root, 'VideoSearchResultsDiagnostics.kt'), 'utf8');
const privateAccess = fs.readFileSync(path.join(root, 'VideoSearchPrivateRunAccess.kt'), 'utf8');
const normal = fs.readFileSync(path.join(root, 'VideoSearchResultsActivity.kt'), 'utf8');
const controls = fs.readFileSync(path.join(root, 'VideoSearchDiagnosticControls.kt'), 'utf8');

test('private results diagnostics follows opt-in and exact private epoch', () => {
  assert.match(sites, /recordingToken\(owner\)/);
  assert.match(sites, /registry\.ownerForTab\(tab\.first, tab\.second\) != owner/);
  assert.match(sites, /registry\.withAuthorizedTab\([\s\S]*?tab\.first, tab\.second/);
  assert.match(sites, /if \(scope == owner\)/);
  assert.match(sites, /submitPrivateEvents\(\s*owner, token, events\)/);
  assert.match(diagnostic, /recorder\.sessionId != expectedSessionId/);
  assert.match(diagnostic, /PRIVATE_RESULTS_SCREEN_COUNTS/);
  assert.match(diagnostic, /PRIVATE_AGGREGATE_LIMIT_UNKNOWN/);
  assert.match(diagnostic, /adapterRowsNotPixels=true/);
  assert.match(privateAccess, /VideoSearchPrivateRunSnapshot\(redacted\)/);
});

test('start while results already exist captures current composed view without input changes', () => {
  assert.match(controls, /if \(started\) onStarted\(\)/);
  assert.match(sites, /onStarted = \{ schedulePrivateDiagnosticObservation\(\) \}/);
  assert.match(normal, /recordingSession\.collectAsState\(\)/);
  assert.match(normal, /visibleKeys, diagnosticsSession\)/);
  assert.match(normal, /LaunchedEffect\(state\.error, run\?\.id, diagnosticsSession\)/);
  assert.match(diagnostic, /sessionState\.value = recorder\.sessionId/);
  assert.match(diagnostic, /sessionState\.value = null/);
  assert.match(sites, /schedulePrivateDiagnosticObservation\(\)/);
});

test('private 20k analysis runs off the main thread and reauthorizes after it completes', () => {
  assert.match(sites, /withContext\(Dispatchers\.Default\)\s*\{\s*VideoSearchPrivateResultsDiagnosticAnalyzer\.events\(snapshot, submitted\)/);
  assert.match(sites, /privateResultsItems\.subList\(/);
  assert.match(sites, /privateDiagnosticsSnapshotJob\?\.cancel\(\)/);
  assert.match(sites, /selectedPrivateTab\(\) != tab/);
  assert.match(sites, /privateResultsItems !== snapshot\.films/);
  assert.match(sites, /registry\.withAuthorizedTab\([\s\S]*?if \(scope == owner\)/);
  assert.match(sites, /VideoSearchDiagnosticResultsHost\.submitPrivateEvents\(/);
  const window = sites.slice(sites.indexOf('private fun showPrivateResultsWindow()'),
    sites.indexOf('private fun renderPrivateRun'));
  assert.doesNotMatch(window, /VideoSearchDiagnosticResultsHost\.observePrivate\(/);
  assert.doesNotMatch(window, /distinctBy|groupingBy/);
});
