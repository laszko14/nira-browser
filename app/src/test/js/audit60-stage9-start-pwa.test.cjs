'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname,'../../main/java/com/prirai/android/nira');
const read = p => fs.readFileSync(path.join(root,p),'utf8');

test('only restore owner bootstraps start and close, never pre-restore zero', () => {
  const base=read('BaseBrowserFragment.kt');
  const gate=read('TabRestoreBootstrapGate.kt');
  assert.match(base,/gate\.shouldCreate\(restored, tabIds\)/);
  assert.equal((base.match(/components\.tabsUseCases\.addTab\.invoke\(url, selectTab = true\)/g)||[]).length,1);
  assert.match(gate,/if \(!restoreComplete\) return false/);
});

test('cold PWA intent always creates fragment and validates latest generation', () => {
  const source=read('webapp/WebAppActivity.kt');
  assert.match(source,/extractUrlFromIntent\(intent\)\?\.let\(::openWebApp\)/);
  assert.match(source,/if \(!launchGate\.isCurrent\(ticket\)/);
  assert.match(source,/private fun installOrDefer\(/);
  assert.match(source,/\.replace\(R\.id\.webapp_container, WebAppFragment\.newInstance\(/);
  assert.match(source,/supportFragmentManager\.isStateSaved/);
  assert.match(source,/override fun onPostResume\(/);
});
