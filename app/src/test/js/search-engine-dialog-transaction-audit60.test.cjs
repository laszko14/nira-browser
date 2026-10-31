'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const read = p => fs.readFileSync(path.resolve(__dirname, '../../main/java/com/prirai/android/nira/', p), 'utf8');
const general = read('settings/fragment/GeneralSettingsFragment.kt');
const draft = read('settings/fragment/SearchEngineSelectionDraft.kt');

test('ZC-AUD-021 radio tap and Cancel do not write normal/private preferences', () => {
  const begin = general.indexOf('private fun pickSearchEngine(private: Boolean)');
  const end = general.indexOf('fun customSearchEngineDialog()', begin);
  assert.ok(begin >= 0 && end > begin);
  const picker = general.slice(begin, end);
  const radio = picker.slice(picker.indexOf('.setSingleChoiceItems('), picker.indexOf('.show()'));
  assert.match(picker, /val draft = SearchEngineSelectionDraft\(/);
  assert.match(picker, /\.setNeutralButton\(resources\.getString\(R\.string\.cancel\), null\)/);
  assert.match(radio, /draft\.select\(which, engines\.size\)/);
  assert.doesNotMatch(radio, /prefs\.(searchEngineChoice|privateSearchEngineChoice|customSearchEngine)\s*=/);
  assert.match(picker, /\.setPositiveButton\([\s\S]*?prefs\.privateSearchEngineChoice = draft\.privateChoice[\s\S]*?prefs\.searchEngineChoice = draft\.regularChoice[\s\S]*?prefs\.customSearchEngine = draft\.useCustom[\s\S]*?SearchEnginePreferences\.apply\(/);
});

test('Custom Cancel never disables previously saved Custom, invalid URL never commits', () => {
  const custom = general.slice(general.indexOf('fun customSearchEngineDialog()'));
  assert.match(custom, /if \(SearchEngineList\.isValidCustomSearchUrl\(entered\)\)/);
  assert.match(custom, /UserPreferences\(requireContext\(\)\)\.customSearchEngine = true/);
  assert.match(custom, /builder\.setNegativeButton\("Cancel", null\)/);
  assert.doesNotMatch(custom, /customSearchEngine = false/);
  assert.match(general, /dialog\.cancel\(\)\s*customSearchEngineDialog\(\)/);
  assert.match(draft, /if \(index == engineCount\) return true/);
});

test('private choice -1 still inherits the regular search engine', () => {
  assert.match(draft, /privateChoice = index - 1/);
  assert.match(draft, /forPrivateSearch -> if \(privateChoice < 0\) 0 else privateChoice \+ 1/);
  assert.match(general, /if \(!private && prefs\.privateSearchEngineChoice < 0\)/);
});
