"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../../../..");
const read = file => fs.readFileSync(path.join(root,file), "utf8");
const media="app/src/main/java/com/prirai/android/nira/media/";
test("v1170: detected videos render actual Codex-style 1-10 row horizontal tiles",()=>{
 const panel=read(media+"VideoTilesPanel.kt"),settings=read(media+"VideoTilesSettings.kt");
 assert.match(panel,/LazyHorizontalGrid\(rows = GridCells\.Fixed\(geometry\.rows\)/);
 assert.match(panel,/verticalScroll\(vertical\)/);
 assert.match(panel,/video-tiles-grid/);
 assert.match(settings,/rows\.coerceIn\(1, 10\)/);
 assert.match(settings,/widthDp\.coerceIn\(100, 280\)/);
 assert.match(settings,/gapDp\.coerceIn\(2, 16\)/);
});
test("v1170: actions and metadata are real, not inert UI decoration",()=>{
 const panel=read(media+"VideoTilesPanel.kt"),ui=read(media+"MediaFloatingUi.kt");
 assert.match(panel,/AsyncImage\(/);
 assert.match(panel,/entry\.posterUrl/);
 assert.match(panel,/onDownload\(entry\.id\)/);
 assert.match(panel,/onCopy\(entry\.id\)/);
 assert.match(ui,/queueDownload\(id\)/);
 assert.match(ui,/ClipboardManager/);
 assert.match(ui,/sameSelection\(snapshot\.tab, latest\.tab\)/);
 assert.match(ui,/MediaFormat\.HLS, MediaFormat\.DASH/);
 assert.match(ui,/requestSheetRefreshRate\(dialog\)/);
 assert.match(ui,/HighRefreshRatePolicy\.choose\(rates\)/);
});
test("v1170: separate orientation settings and six toggles persist without media URLs",()=>{
 const prefs=read(media+"VideoTilesPreferences.kt");
 for(const key of ["thumbnail","title","format","quality","size","actions"])assert.match(prefs,new RegExp('key\\(landscape, "'+key+'"\\)'));
 assert.match(prefs,/landscape.*portrait/);
 assert.doesNotMatch(prefs,/putString\(.*(?:url|cookie|tabId|profileId)/i);
});
