'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('media sheet Window registration is independent of FAB_OPEN measurement', () => {
  const source = fs.readFileSync(path.join(__dirname, '../../main/java/com/prirai/android/nira/media/MediaFloatingUi.kt'), 'utf8');
  const show = source.slice(source.indexOf('private fun showSheet()'), source.indexOf('private fun requestSheetRefreshRate('));
  const attach = show.indexOf('dialog.window?.let(TestLab::attachWindow)');
  const guard = show.indexOf('if (measurement != null)');
  assert.ok(attach > show.indexOf('dialog.show()') && attach < guard, 'register Window before optional latency token guard');
  assert.match(show, /TestLab\.detachWindow\(it\)/);
  assert.match(show, /capture\?\.end\(measurement, OperationOutcome\.INTERRUPTED\)/);
});

test('device scenario targets current FULL_AUDIT and embedded panels, never old 60-second selectors', () => {
  const source = fs.readFileSync(path.join(__dirname, '../../androidTest/java/com/prirai/android/nira/diagnostics/testlab/TestLabDeviceTest.kt'), 'utf8');
  assert.match(source, /URUCHOM JEDEN TEST • 120 s/);
  assert.match(source, /120_000\.0/);
  assert.match(source, /NiraEmbeddedSideTabs/);
  assert.match(source, /NiraEmbeddedSideBookmarks/);
  assert.doesNotMatch(source, /runSevenModes|NiraSideTabs"|— 60 s|0\.5\.22-alpha75-clean/);
});
