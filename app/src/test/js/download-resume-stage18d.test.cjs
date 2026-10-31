'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const main = path.join(__dirname, '../../main/java/com/prirai/android/nira/downloads');
const service = fs.readFileSync(path.join(main, 'ParallelDownloadService.kt'), 'utf8');
const guard = fs.readFileSync(path.join(main, 'DownloadResumeGuard.kt'), 'utf8');

test('18D binds resumed part files to an authenticated resource identity before workers', () => {
  assert.match(service, /val representation = probeRepresentation\(initial\)/);
  assert.match(service, /DownloadResumeGuard\.prepare\(/);
  assert.ok(service.indexOf('DownloadResumeGuard.prepare(') < service.indexOf('coroutineScope {'));
  assert.match(service, /strongEtag = representation\.strongEtag/);
  assert.match(service, /downloadChunk\(download, start, end, File\(dir, "part\$index"\), representation\.strongEtag\)/);
  assert.match(guard, /val strongEtag: String/);
  assert.match(guard, /writeString\(identity\.url\)/);
  assert.match(guard, /writeString\(identity\.strongEtag\)/);
  assert.match(guard, /writeString\(identity\.referrerUrl\)/);
});

test('18D HTTP requires strong ETag on probe and every range, including resume', () => {
  assert.match(service, /DownloadResumeGuard\.strongEtag\(response\.headers\["ETag"\]\) \?: return null/);
  assert.match(service, /headers\.append\("If-Range", expectedEtag\)/);
  assert.match(service, /DownloadResumeGuard\.matchesRange\(/);
  assert.match(service, /response\.headers\["ETag"\], rangeStart, end/);
  assert.match(guard, /status == 206/);
  assert.match(guard, /strongEtag\(responseEtag\) == expectedEtag/);
});

test('18D cannot reuse legacy sidecars or retain unverified parts on serial fallback', () => {
  assert.match(guard, /if \(actual != expected \|\| temp\.exists\(\)\)/);
  assert.match(guard, /directory\.deleteRecursively\(\)/);
  assert.match(service, /stale\.exists\(\) && !stale\.deleteRecursively\(\)/);
  assert.match(service, /DownloadController\.startFallback\(this, initial\.id\)/);
  assert.match(guard, /MessageDigest\.getInstance\("SHA-256"\)/);
});
