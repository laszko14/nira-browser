'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '../../main/java/com/prirai/android/nira/videosearch');
const sites = fs.readFileSync(path.join(root, 'MyVideoSitesActivity.kt'), 'utf8');
const diagnostic = fs.readFileSync(path.join(root, 'VideoSearchResultsDiagnostics.kt'), 'utf8');
const privateAccess = fs.readFileSync(path.join(root, 'VideoSearchPrivateRunAccess.kt'), 'utf8');

test('private results diagnostics follows opt-in and exact private epoch', () => {
  assert.match(sites, /isRecording\(snapshot\.run\.scope\)/);
  assert.match(sites, /registry\.withAuthorizedTab\(tab, profileId\)/);
  assert.match(sites, /if \(scope == snapshot\.run\.scope\)/);
  assert.match(sites, /observePrivate\(\s*snapshot, privateResultsItems\.take\(shown\)\)/);
  assert.match(diagnostic, /PRIVATE_RESULTS_SCREEN_COUNTS/);
  assert.match(diagnostic, /PRIVATE_AGGREGATE_LIMIT_UNKNOWN/);
  assert.match(diagnostic, /adapterRowsNotPixels=true/);
  assert.match(privateAccess, /VideoSearchPrivateRunSnapshot\(redacted\)/);
});
