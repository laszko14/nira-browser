'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = path.resolve(__dirname, '../../main/java/com/prirai/android/nira/videosearch');
const sites = fs.readFileSync(path.join(base, 'MyVideoSitesActivity.kt'), 'utf8');
const results = fs.readFileSync(path.join(base, 'VideoSearchResultsActivity.kt'), 'utf8');
const tab = fs.readFileSync(path.join(base, 'VideoSearchResultsTabContent.kt'), 'utf8');

test('neon site controls keep all existing search and configuration handlers', () => {
  for (const handler of [
    'addSite()', 'bulkSelection(selectAll = true)',
    'bulkSelection(selectAll = false)', 'chooseParallelism()',
    'chooseDefaultVideoGroup()', 'previewQueue()',
    'showMultiStatus()', 'stopMultiSearch()', 'showMultiHistory()',
    'showSavedFilmFolders()', 'onInspect = ::inspectSite', 'onCheck = ::checkConfiguration',
    'configureManually', 'confirmRemoval',
  ]) assert.ok(sites.includes(handler), handler);
  assert.match(sites, /styleNeonButton\(this, accent = true, height = 52\)/);
  assert.match(sites, /val expanded = LinearLayout\(ctx\)\.apply \{/);
  assert.match(sites, /visibility = View.GONE/);
  assert.match(sites, /android\.graphics\.Color\.WHITE/);
});

test('results keep compact always-available filters and legible film cards', () => {
  for (const label of ['⌕ Filtry', '↕', '⇄', '▱', 'Scalaj po nazwie',
    'Scalaj po długości', 'Według witryn', 'Wszystkie razem']) {
    assert.ok(results.includes(label), label);
  }
  assert.match(results, /controlsCollapsed = true/);
  assert.match(results, /contentColor = Color.White/g);
  assert.match(results, /NeonResultAction\("⌕ Filtry"/);
  assert.match(results, /onViewChange\(target\)/);
  assert.match(results, /onOpenSavedFolders/);
  assert.match(results, /VideoSearchGroupExpansion\.toggle/);
  assert.match(results, /VideoSearchBulkSelection\.toggle/);
  assert.match(results, /film\.originalTitle/);
  assert.match(results, /VideoSearchThumbnail/);
});

test('both result hosts keep high-contrast dark theme', () => {
  for (const source of [results, tab]) {
    assert.match(source, /onSurfaceVariant = Color\(0xffeee7f9\)/);
    assert.match(source, /onBackground = Color.White/);
    assert.match(source, /onSecondaryContainer = Color.White/);
  }
});

test('update remains on same debug package and strictly greater arm64 version', () => {
  const gradle = fs.readFileSync(path.resolve(__dirname, '../../../build.gradle'), 'utf8');
  assert.match(gradle, /versionCode = 208/);
  assert.match(gradle, /versionName = "0\.5\.22-alpha113-clean"/);
  assert.match(gradle, /applicationIdSuffix = '\.debug'/);
  assert.match(gradle, /include\(testAbi \?: 'arm64-v8a'\)/);
});
