'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const search = fs.readFileSync(
  path.resolve(__dirname, '../../main/java/com/prirai/android/nira/search/SearchDialogFragment.kt'),
  'utf8'
);
const methodStart = search.indexOf('private fun hideDeviceKeyboard(dialog: DialogInterface)');
const methodEnd = search.indexOf('override fun onDismiss(', methodStart);
const hideBlock = search.slice(methodStart, methodEnd);
const dismissBlock = search.slice(
  search.indexOf('override fun onDismiss('),
  search.indexOf('override fun onBackPressed(): Boolean')
);

test('ZC-AUD-016: dismiss always hides IME, never toggles an unknown state', () => {
  assert.ok(methodStart >= 0 && methodEnd > methodStart);
  assert.doesNotMatch(search, /toggleSoftInput\s*\(/);
  assert.match(hideBlock, /if \(dialogHandledAction\) return/);
  assert.match(hideBlock, /\(dialog as\? Dialog\)\?\.window/);
  assert.match(hideBlock, /WindowCompat\.getInsetsController\(dialogWindow, dialogWindow\.decorView\)[\s\S]*?\.hide\(WindowInsetsCompat\.Type\.ime\(\)\)/);
  assert.match(hideBlock, /else \{[\s\S]*?view\?\.hideKeyboard\(\)/);
});

test('ZC-AUD-016: IME is hidden before dismiss destroys the dialog window', () => {
  assert.match(dismissBlock, /hideDeviceKeyboard\(dialog\)\s*super\.onDismiss\(dialog\)/);
  assert.match(search, /override fun onPause\(\)[\s\S]*?view\?\.hideKeyboard\(\)/);
  assert.match(search, /override fun onBackPressed\(\): Boolean \{\s*view\?\.hideKeyboard\(\)\s*dismissAllowingStateLoss\(\)/);
});

test('ZC-AUD-016: accepted search ownership and search operations unchanged', () => {
  assert.match(search, /dismissDialog = \{\s*dialogHandledAction = true\s*dismissAllowingStateLoss\(\)/);
  assert.match(search, /clearToolbarFocus = \{\s*dialogHandledAction = true\s*toolbarView\.view\.hideKeyboard\(\)/);
  assert.match(search, /interactor\.onSearchShortcutsButtonClicked\(\)/);
  assert.match(search, /searchTermOrURL = url/);
});
