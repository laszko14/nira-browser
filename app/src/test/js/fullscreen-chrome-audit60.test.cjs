const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const browser = fs.readFileSync('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt','utf8');
const base = fs.readFileSync('app/src/main/java/com/prirai/android/nira/BaseBrowserFragment.kt','utf8');
test('fullscreen callback generations gate posted toolbar restoration', () => {
  assert.match(browser,/fullscreenChromeGate\.modeChanged\(inFullScreen\)/);
  assert.match(browser,/collapseBrowserViewFromFullscreen\(transition\)/);
  assert.match(browser,/toolbar\.post \{[\s\S]*?!fullscreenChromeGate\.canRestore\(transition\)/);
  assert.match(browser,/this@BrowserFragment\.view !== ownerView/);
  assert.match(browser,/fullscreenChromeGate\.invalidateView\(\)/);
});
test('hidden chrome never reserves margins in fullscreen', () => {
  assert.match(browser,/if \(fullscreenChromeGate\.isFullscreen\) \{[\s\S]*fullscreenLp\.topMargin = 0[\s\S]*fullscreenLp\.bottomMargin = 0/);
});
test('base deferred viewport initializer checks current fullscreen and view identity', () => {
  assert.match(base,/fullscreenChromeGeneration\+\+[\s\S]*fullscreenChromeActive = inFullScreen/);
  assert.match(base,/fullscreenChromeActive \|\| fullscreenChromeGeneration != ownerGeneration/);
  assert.match(base,/_binding !== ownerBinding/);
});
