const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const base = fs.readFileSync('app/src/main/java/com/prirai/android/nira/BaseBrowserFragment.kt','utf8');
const browser = fs.readFileSync('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt','utf8');
const manager = fs.readFileSync('app/src/main/java/com/prirai/android/nira/browser/WebContentPositionManager.kt','utf8');
const policy = fs.readFileSync('app/src/main/java/com/prirai/android/nira/browser/WebViewportReservation.kt','utf8');

test('only viewport manager writes Chrome/IME margins', () => {
  assert.match(base,/protected fun publishChromeReservation\(top: Int, bottom: Int\)/);
  assert.match(base,/webContentPositionManager\?\.setChromeReservation\(top, bottom\)/);
  assert.match(base,/webContentPositionManager\?\.setFullscreen\(inFullScreen\)/);
  assert.match(browser,/publishChromeReservation\(topChrome, bottomChrome\)/);
  assert.match(browser,/publishChromeReservation\(0, 0\)/);
  assert.match(manager,/private fun applyMargins\(\)/);
  assert.doesNotMatch(browser,/lp\.(topMargin|bottomMargin)\s*=/);
  assert.doesNotMatch(base,/swipeRefreshParams\.(topMargin|bottomMargin)\s*=/);
});

test('Insets and chrome use one max reservation and do not stack navigation bars', () => {
  assert.match(manager,/insets\.getInsets\(WindowInsetsCompat\.Type\.ime\(\)\)/);
  assert.match(manager,/insets\.getInsets\(WindowInsetsCompat\.Type\.systemBars\(\)\)/);
  assert.match(manager,/reservation\.setIme\(\(imeBottom - navBottom\)\.coerceAtLeast\(0\)\)/);
  assert.match(policy,/maxOf\(bottomChrome, ime\)/);
  assert.match(policy,/if \(fullscreen\) Margins\(0, 0\)/);
  assert.match(base,/publishChromeReservation\(actualToolbarHeight, bottomStrip\)/);
  assert.match(browser,/unifiedToolbar\?\.isScrollAnimationRunning\(\) == true/);
});
