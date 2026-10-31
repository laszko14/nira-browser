'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const read = p => fs.readFileSync(path.resolve(__dirname, '../../main/java/com/prirai/android/nira/', p), 'utf8');
const fragment = read('browser/bookmark/ui/BookmarksBottomSheetFragment.kt');
const adapter = read('browser/bookmark/ui/BookmarkAdapter.kt');
const sorter = read('browser/bookmark/ui/BookmarkUrlSort.kt');
const item = read('browser/bookmark/items/BookmarkItem.kt');

const picker = fragment.slice(fragment.indexOf('private fun showSortDialog()'), fragment.indexOf('private fun showEmptyState()'));

test('ZC-AUD-012 name and URL are distinct real menu choices; date is not offered', () => {
  assert.match(picker, /getString\(R\.string\.sort_by_name\)/);
  assert.match(picker, /getString\(R\.string\.sort_by_url\)/);
  assert.doesNotMatch(picker, /getString\(R\.string\.sort_by_date_added\)/);
  assert.match(picker, /0 -> bookmarkAdapter\.sortBookmarks\(BookmarkSortType\.A_Z\)/);
  assert.match(picker, /1 -> bookmarkAdapter\.sortBookmarksByUrl\(\)/);
  assert.match(picker, /dialogScope !== scope \|\| !scope\.isActive/);
});
test('Sort persists atomically and rolls back on save failure', () => {
  assert.match(picker, /val originalOrder = currentFolder\.itemList\.toList\(\)/);
  assert.match(picker, /if \(!manager\.save\(\)\) \{[\s\S]*?currentFolder\.itemList\.clear\(\)[\s\S]*?currentFolder\.itemList\.addAll\(originalOrder\)/);
  assert.match(adapter, /fun sortBookmarksByUrl\(\) \{\s*BookmarkUrlSort\.sortInPlace\(items\)\s*notifyDataSetChanged\(\)/);
});
test('URL comparator uses stored URL and folder title, not synthetic dates', () => {
  assert.match(sorter, /if \(it is BookmarkFolderItem\) 0 else 1/);
  assert.match(sorter, /is BookmarkFolderItem -> item\.title\.orEmpty\(\)/);
  assert.match(sorter, /is BookmarkSiteItem -> runCatching \{ item\.url \}/);
  assert.doesNotMatch(item, /dateAdded|createdAt|timestamp/);
  assert.doesNotMatch(sorter, /System\.currentTimeMillis/);
});
