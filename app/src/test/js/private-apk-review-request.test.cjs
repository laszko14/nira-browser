"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { authorizePrivateApkReview } =
  require("../../../../scripts/authorize-private-apk-review.cjs");

const sha = "c66350841741f16d1fccbf81fd876b012f4fe335";
const repo = "laszko14/nira-browser";
function event() {
  return {
    action: "submitted",
    sender: { login: "laszko14" },
    review: {
      state: "commented", user: { login: "laszko14" }, commit_id: sha,
      body: "ZAVRO_PRIVATE_APK_REQUEST\\nsha=" + sha +
        "\\ntask=VIDEO_SEARCH_BULK_V1205",
    },
    pull_request: {
      number: 114, state: "open", merged: false,
      head: {
        sha, ref: "feat/zavro-clean-video-results-bulk-v1205-20261101",
        repo: { full_name: repo },
      },
      base: { repo: { full_name: repo } },
    },
  };
}

test("owner exact SHA review may authorize private update", () => {
  assert.deepEqual(authorizePrivateApkReview(event(), "laszko14", repo),
    { sha, task: "VIDEO_SEARCH_BULK_V1205", pr: 114 });
});

test("normal reviews, PR creation and malicious review text fail closed", () => {
  for (const invalid of [
    e => { e.action = "opened"; },
    e => { e.review.state = "approved"; },
    e => { e.review.body = "Kompiluj"; },
    e => { e.review.body += "\\nPublikuj"; },
    e => { e.review.body = e.review.body.replace("sha=", "source_sha="); },
    e => { e.review.commit_id = "0".repeat(40); },
    e => { e.pull_request.head.sha = "0".repeat(40); },
    e => { e.pull_request.state = "closed"; },
    e => { e.pull_request.head.ref = "main"; },
    e => { e.pull_request.head.repo.full_name = "attacker/fork"; },
    e => { e.review.user.login = "attacker"; },
    e => { e.sender.login = "attacker"; },
  ]) {
    const e = event();
    invalid(e);
    assert.throws(() => authorizePrivateApkReview(e, "laszko14", repo));
  }
  assert.throws(() => authorizePrivateApkReview(event(), "attacker", repo));
  assert.throws(() => authorizePrivateApkReview(event(), "laszko14", "fork/repo"));
});

test("ordinary PR and push have no signed APK path in workflow", () => {
  const root = path.resolve(__dirname, "../../../..");
  const workflow = fs.readFileSync(path.join(root,
    ".github/workflows/apk-release.yml"), "utf8");
  const build = workflow.split(/\\n  build:\\s*\\n/)[1]
    ?.split(/\\n  regression-tests:\\s*\\n/)[0];
  assert.ok(build, "private build job exists");
  assert.match(workflow, /pull_request_review:\\s*\\n\\s*types: \\[submitted\\]/);
  assert.match(build, /github\\.event_name == 'pull_request_review'/);
  assert.match(build, /github\\.event\\.review\\.user\\.login == 'laszko14'/);
  assert.match(build, /scripts\\/authorize-private-apk-review\\.cjs/);
  assert.match(build, /git rev-parse HEAD/);
  assert.match(build, /head_sha=\\$REVIEW_HEAD_SHA/);
  assert.match(build, /scripts\\/verify-update-apk\\.sh/);
  assert.doesNotMatch(build, /github\\.event_name == 'push'/);
  assert.doesNotMatch(build, /github\\.event_name == 'pull_request' &&/);
  assert.doesNotMatch(build, /github\\.event_name == 'pull_request_target'/);
  const guard = build.indexOf("node scripts/authorize-private-apk-review.cjs");
  const secrets = build.indexOf("ANDROID_KEYSTORE_BASE64: \\${{ secrets.");
  assert.ok(guard > 0 && secrets > guard, "authorization precedes keystore");
});
