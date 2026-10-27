"use strict";
const test=require("node:test"),assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const repo=path.resolve(__dirname,"../../../..");
const read=p=>fs.readFileSync(path.join(repo,p),"utf8");
const root="app/src/main/java/com/prirai/android/nira/diagnostics/testlab/";
test("v1168 clean has exactly seven explicit 60s TestLab modes",()=>{
 const contract=read(root+"TestLabContract.kt");
 const modes=contract.match(/COMPLEX, PANELS_GESTURES, TABS_GROUPS, GECKO_PAGES, VIDEO_DISCOVERY, RESOURCES, STABILITY/);
 assert.ok(modes);
 assert.match(read(root+"TestLabEngine.kt"),/DURATION_NS/);
 assert.match(read(root+"TestLabActivity.kt"),/testlab-start-/);
 assert.match(read(root+"TestLabViewModel.kt"),/Wideo i wykrywanie/);
});
test("v1168 TestLab does not restore internal MPV, DSP or Lua",()=>{
 const dir=path.join(repo,root);
 for(const name of fs.readdirSync(dir).filter(f=>f.endsWith(".kt"))){
  const source=fs.readFileSync(path.join(dir,name),"utf8");
  assert.doesNotMatch(source,/ZavroMpvActivity|libmpv|MPV_|MpvDebugTrace|MediaElementPlaybackBridge|LUA_|DSP_|TestType.VIDEO_MPV/,name);
 }
 const manifest=read("app/src/main/AndroidManifest.xml");
 assert.match(manifest,/diagnostics\.testlab\.TestLabActivity/);
 assert.doesNotMatch(manifest,/ZavroMpvActivity/);
});
test("v1168 retains source-safe screen rate and external video request semantics",()=>{
 const runtime=read(root+"TestLabRuntime.kt"),report=read(root+"TestLabReportGenerator.kt");
 assert.match(runtime,/supportedModes/);
 assert.match(report,/currentRefreshHz/);
 assert.match(report,/requestedRefreshHz/);
 assert.match(report,/nie dowodzi rozpoczęcia odtwarzania/);
 assert.match(read("app/src/main/java/com/prirai/android/nira/media/MediaFloatingUi.kt"),/VIDEO_EXTERNAL_REQUEST/);
});
test("v1168 signed update metadata preserves clean package and version track",()=>{
 const gradle=read("app/build.gradle"),workflow=read(".github/workflows/apk-release.yml");
 assert.match(gradle,/versionCode = 182/);
 assert.match(gradle,/versionName = "0\.5\.22-alpha87-clean"/);
 assert.match(gradle,/applicationIdSuffix = '\.debug'/);
 assert.match(workflow,/EXPECTED_VERSION_CODE: "1182"/);
 assert.match(workflow,/EXPECTED_VERSION_NAME: 0\.5\.22-alpha87-clean/);
});
