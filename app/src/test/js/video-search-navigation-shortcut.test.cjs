'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/java/com/prirai/android/nira');
const tabs = fs.readFileSync(path.join(root, 'browser/tabs/TabsBottomSheetFragment.kt'), 'utf8');
const sites = fs.readFileSync(path.join(root, 'videosearch/MyVideoSitesActivity.kt'), 'utf8');

function between(source, start, end) {
  const from = source.indexOf(start);
  assert.notEqual(from, -1, 'missing source anchor: ' + start);
  const until = source.indexOf(end, from + start.length);
  assert.notEqual(until, -1, 'missing end anchor: ' + end);
  return source.slice(from, until);
}

test('left drawer footer has two equal-weight accessible buttons', () => {
  const footer = between(tabs, 'val footerBrush = if (landscape)', '// Persist separate side-panel widths');
  assert.equal((footer.match(/Modifier\.weight\(1f\)/g) || []).length, 2);
  assert.match(footer, /Text\("Nowa karta"/);
  assert.match(footer, /Text\("Wyszukiwarka"/);
  assert.match(footer, /contentDescription = "Wyszukiwarka filmów"/);
  assert.match(footer, /\.clickable \{ openVideoSearchFromPanel\(\) \}/);
});

test('search shortcut opens manager and closes embedded drawer via its host', () => {
  const shortcut = between(tabs, 'private fun openVideoSearchFromPanel()', 'private fun addNewTab()');
  assert.match(shortcut, /Intent\(requireContext\(\), MyVideoSitesActivity::class\.java\)/);
  assert.match(shortcut, /isEmbeddedSidePanel\) embeddedDismissRequested\?\.invoke\(\)/);
  assert.match(shortcut, /else dismiss\(\)/);
  assert.doesNotMatch(shortcut, /tabsUseCases\.addTab/);
});

test('confirmed multi-site search opens live results tab rather than previous browser tab', () => {
  const confirmation = between(sites, '.setPositiveButton("Uruchom") { _, _ ->', 'private fun showMultiStatus()');
  assert.match(confirmation, /bridge\?\.startMultiSite\(plan, repository\)/);
  assert.match(confirmation, /showMultiHistory\(\)/);
  assert.doesNotMatch(confirmation, /startActivity\(Intent/);
  const results = between(sites, 'private fun showMultiHistory()', 'private fun showSavedFilmFolders()');
  assert.match(results, /VIDEO_SEARCH_RESULTS_TAB_URL/);
  assert.match(results, /selectTab = true/);
});

test('single-site search does not close settings on successful submission', () => {
  const single = between(sites, 'private fun searchSite(', 'private fun editSite(');
  assert.match(single, /VideoSearchBridgeHost\.instance\(\)\?\.searchSite/);
  assert.doesNotMatch(single, /finish\(\)/);
});

test('private multi-site search stays with RAM results instead of leaving settings', () => {
  const privateMulti = between(sites, 'if (VideoSearchBridgeHost.instance()?.startPrivateMultiSite(', 'header.addView(TextView(this).apply {');
  assert.match(privateMulti, /refreshPrivatePanel\(\)/);
  assert.doesNotMatch(privateMulti, /finish\(\)/);
});
