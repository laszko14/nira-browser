'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/java/com/prirai/android/nira/browser/tabs/TabsBottomSheetFragment.kt'),
  'utf8'
);

test('side panel observes layout changes without new pointer movement', () => {
  assert.match(source, /snapshotFlow\s*\{\s*currentSideDrop\(\)\?\.key\s*\}/);
  assert.match(source, /snapshotFlow[\s\S]*?\.collect\s*\{\s*latestKey\s*->[\s\S]*?hoveredKey\s*=\s*latestKey/);
});

test('side panel hover, autoscroll and final drop share live hit testing', () => {
  const hits = source.match(/currentSideDrop\(\)\?\.key/g) || [];
  assert.ok(hits.length >= 4, 'pointer movement, snapshotFlow, autoscroll and delayed expansion use current geometry');
  assert.match(source, /if \(listState\.scrollBy\(velocity\) == 0f\) break[\s\S]*?hoveredKey = currentSideDrop\(\)\?\.key/);
  assert.match(source, /fun finishDrag\(\)[\s\S]*?val resolved = currentSideDrop\(\)/);
});

test('delayed group expansion revalidates current hover before changing groups', () => {
  assert.match(source, /kotlinx\.coroutines\.delay\(450\)[\s\S]*?dragProfileId == activeProfileId/);
  assert.match(source, /currentSideDrop\(\)\?\.key == "group_" \+ targetId/);
  assert.match(source, /targetId !in expanded\s*\)\s*\{\s*viewModel\.toggleGroupExpanded\(targetId\)/);
});
