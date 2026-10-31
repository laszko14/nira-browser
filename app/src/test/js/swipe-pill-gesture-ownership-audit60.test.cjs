'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const src = (...files) => fs.readFileSync(
  path.resolve(__dirname, '../../main/java/com/prirai/android/nira/browser/tabs/compose', ...files),
  'utf8'
);
const pill = src('SwipeableTabPill.kt');
const bar = src('TabBarCompose.kt');
const gate = src('SwipePillGestureOwner.kt');

test('ZC-AUD-019: stable pointer identity, Main/Final passes, consumed/drag owner', () => {
  assert.match(pill, /awaitFirstDown\(requireUnconsumed = true\)/);
  assert.match(pill, /firstOrNull \{ it\.id == down\.id \}/);
  assert.match(pill, /otherFinger \|\| consumed \|\|[\s\S]*?currentDragInProgress\(\)/);
  assert.match(pill, /awaitPointerEvent\(PointerEventPass\.Main\)/);
  assert.match(pill, /awaitPointerEvent\(PointerEventPass\.Final\)/);
  assert.match(pill, /owner\.move\(/);
  assert.match(pill, /if \(claimed\) change\.consume\(\)/);
  assert.match(pill, /finally \{[\s\S]*?owner\.cancel\(\)[\s\S]*?offsetY = 0f/);
  assert.doesNotMatch(pill, /event\.changes\.firstOrNull\(\)/);
});

test('ZC-AUD-019: actual nested DnD callsite shares drag ownership', () => {
  assert.match(bar, /\.draggableItem\([\s\S]*?SwipeableTabPill\([\s\S]*?isDragInProgress = \{ coordinator\.isDragging\(tab\.id\) \}/);
  assert.match(gate, /this\.pointerId != pointerId \|\| consumed \|\| otherPointerPressed \|\| dragInProgress/);
  assert.match(gate, /if \(this\.pointerId == pointerId && claimed &&[\s\S]*?!consumed && !otherPointerPressed && !dragInProgress/);
});
