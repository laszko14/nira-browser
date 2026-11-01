"use strict";

const fs = require("node:fs");

/** Fail-closed validation of an explicit PRIVATE APK request from the repo owner. */
function authorizePrivateApkReview(event, actor, currentRepo) {
  const review = event && event.review;
  const pr = event && event.pull_request;
  if (!event || event.action !== "submitted" ||
      !review || review.state !== "commented" || !pr) {
    throw new Error("Not a submitted review command");
  }
  if (currentRepo !== "laszko14/nira-browser" || actor !== "laszko14" ||
      event.sender?.login !== actor || review.user?.login !== actor ||
      pr.head?.repo?.full_name !== currentRepo ||
      pr.base?.repo?.full_name !== currentRepo) {
    throw new Error("Private APK review requires the repository owner and same-repository PR");
  }
  // No loose phrase matching, whitespace aliases or variable expansion.
  const match = /^ZAVRO_PRIVATE_APK_REQUEST\nsha=([a-f0-9]{40})\ntask=([A-Z0-9][A-Z0-9_-]{5,99})$/
    .exec(review.body || "");
  if (!match) throw new Error("Private APK request syntax invalid");
  const sha = match[1];
  const task = match[2];
  if (sha !== pr.head?.sha || sha !== review.commit_id) {
    throw new Error("Review source SHA differs from PR head or review commit");
  }
  if (!/^(?:feat|fix)\/zavro-clean-[a-z0-9-]+$/.test(pr.head?.ref || "")) {
    throw new Error("Private APK request refused on unexpected source branch");
  }
  if (!Number.isSafeInteger(pr.number) || pr.number < 1 ||
      pr.state !== "open" || pr.merged === true) {
    throw new Error("Private APK request requires an open unmerged PR");
  }
  return { sha, task, pr: pr.number };
}

if (require.main === module) {
  try {
    const path = process.env.GITHUB_EVENT_PATH;
    if (!path) throw new Error("GitHub event payload missing");
    const event = JSON.parse(fs.readFileSync(path, "utf8"));
    const request = authorizePrivateApkReview(event,
      process.env.GITHUB_ACTOR, process.env.GITHUB_REPOSITORY);
    if (request.sha !== process.env.REVIEW_HEAD_SHA ||
        event.review.body !== process.env.REVIEW_BODY) {
      throw new Error("Explicit request does not match GitHub event context");
    }
    console.log("Validated private APK command for PR #" + request.pr +
      ", task=" + request.task + ", SHA=" + request.sha);
  } catch (error) {
    console.error("Private APK authorization failed: " + error.message);
    process.exitCode = 1;
  }
}
module.exports = { authorizePrivateApkReview };
