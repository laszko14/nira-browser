'use strict';
/** One-shot, read-only receipt for a completed process CI run. Never polls or starts workflows. */
const REPO = 'laszko14/nira-browser';
const BRANCH = 'chore/zavro-clean-codex-v3-20261101';
const SAFE_BRANCH = /^(?:feat|fix|chore)\/zavro-clean-[a-z0-9-]+$/;
const WORKFLOW = '.github/workflows/zavro-ai-process-docs.yml';
const REQUIRED = [
  "process-docs",
  "Shared regressions (JS, JVM, AndroidTest compile, lint) / JavaScript WebExtension regressions",
  "Shared regressions (JS, JVM, AndroidTest compile, lint) / Kotlin and Robolectric JVM tests",
  "Shared regressions (JS, JVM, AndroidTest compile, lint) / Android instrumentation test compilation (no device, no APK packaging)",
  "Shared regressions (JS, JVM, AndroidTest compile, lint) / Android lint with regression baseline",
  "Verify required jobs and summarize evidence (NO APK)"
];
const API = 'https://api.github.com/repos/' + REPO;

function evaluate(run, jobs, branch, expectedSha, expectedBranch = BRANCH) {
  if (!SAFE_BRANCH.test(expectedBranch)) throw Error('Unsupported Zavro Clean branch');
  if (!/^[0-9a-f]{40}$/.test(expectedSha)) throw Error('Expected exact 40-character commit SHA');
  if (!run || !Array.isArray(jobs)) throw Error('Missing run/jobs');
  const jobResults = REQUIRED.map(name => {
    const found = jobs.filter(job => job.name === name);
    const job = found.length === 1 ? found[0] : null;
    return {name, count: found.length, status: job?.status || 'missing',
      conclusion: job?.conclusion || null, attempt: job?.run_attempt || null,
      url: job?.html_url || null};
  });
  let status = 'CODE_VERIFIED', reason = 'All six jobs completed/success on the expected current SHA';
  if (run.repository?.full_name !== REPO || run.path !== WORKFLOW ||
      run.head_branch !== expectedBranch || !['push','workflow_dispatch'].includes(run.event)) {
    status = 'BLOCKED'; reason = 'Unexpected repository, workflow, branch or event';
  } else if (run.head_sha !== expectedSha || branch?.commit?.sha !== expectedSha) {
    status = 'STALE_HEAD'; reason = 'Run SHA, expected SHA and live branch HEAD do not match';
  } else if (!Number.isSafeInteger(run.run_attempt) || run.run_attempt < 1) {
    status = 'BLOCKED'; reason = 'Invalid run attempt';
  } else if (run.status !== 'completed') {
    status = 'CI_PENDING'; reason = 'GitHub workflow is not completed';
  } else if (run.conclusion !== 'success') {
    status = 'BLOCKED'; reason = 'Workflow conclusion is not success';
  } else if (jobResults.some(j => j.count !== 1 || j.status !== 'completed' ||
      j.conclusion !== 'success' || j.attempt !== run.run_attempt)) {
    status = 'BLOCKED'; reason = 'Missing, duplicated, skipped, failed or wrong-attempt required job';
  }
  const firstProblem = jobResults.find(j => j.count !== 1 || j.status !== 'completed' ||
    j.conclusion !== 'success' || j.attempt !== run.run_attempt) || null;
  return {
    status, reason, run_id: run.id, attempt: run.run_attempt,
    workflow: WORKFLOW, event: run.event, expected_sha: expectedSha,
    run_sha: run.head_sha, current_branch_sha: branch?.commit?.sha ?? null,
    run_url: 'https://github.com/' + REPO + '/actions/runs/' + run.id,
    first_problem: firstProblem, jobs: jobResults,
    device: 'NOT_TESTED', apk: 'NOT_BUILT',
    auto_resumes_chat: false
  };
}
async function getJson(fetcher, url, token) {
  const headers = {'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'};
  if (token) headers.Authorization = 'Bearer ' + token;
  const response = await fetcher(url, {headers});
  if (!response?.ok) throw Error('GitHub API read failed with HTTP ' + (response?.status || 'unknown'));
  return response.json();
}
async function readOnce({runId, expectedSha, branchName = BRANCH, fetcher = globalThis.fetch, token = ''}) {
  if (!SAFE_BRANCH.test(branchName)) throw Error('Unsupported Zavro Clean branch');
  if (!Number.isSafeInteger(runId) || runId < 1) throw Error('Invalid workflow run ID');
  if (!/^[0-9a-f]{40}$/.test(expectedSha)) throw Error('Invalid expected SHA');
  const run = await getJson(fetcher, API+'/actions/runs/'+runId, token);
  if (run.repository?.full_name !== REPO || run.path !== WORKFLOW || run.head_branch !== branchName) {
    return evaluate(run, [], null, expectedSha, branchName); // fail closed before reading another branch
  }
  const jobs = [];
  let total = null;
  for (let page = 1; page <= 20; page++) {
    const data = await getJson(fetcher, API+'/actions/runs/'+runId+
      '/jobs?filter=latest&per_page=100&page='+page, token);
    if (!Number.isSafeInteger(data.total_count) || data.total_count < 0 ||
      data.total_count > 2000 || !Array.isArray(data.jobs))
      throw Error('Invalid or oversized job pagination');
    if (total === null) total = data.total_count;
    if (total !== data.total_count) throw Error('Job count changed during one-shot read');
    jobs.push(...data.jobs);
    if (jobs.length >= total) break;
    if (data.jobs.length !== 100) throw Error('Incomplete GitHub job page');
  }
  if (jobs.length !== total) throw Error('Incomplete job listing; refuse to report PASS');
  const liveBranch = await getJson(fetcher, API+'/branches/'+encodeURIComponent(branchName), token);
  return evaluate(run, jobs, liveBranch, expectedSha, branchName);
}
async function main(argv = process.argv.slice(2), fetcher = globalThis.fetch) {
  const [runId, sha, branchName = BRANCH] = argv;
  if ((argv.length !== 2 && argv.length !== 3) || !/^[1-9][0-9]*$/.test(runId || '')) {
    process.stderr.write('Usage: node scripts/ci-run-receipt.cjs RUN_ID EXPECTED_40_CHAR_SHA [BRANCH]\n');
    process.exitCode = 2;
    return;
  }
  try {
    const receipt = await readOnce({runId:Number(runId), expectedSha:sha,
      branchName, fetcher, token:process.env.GH_TOKEN || ''});
    process.stdout.write(JSON.stringify(receipt,null,2)+'\n');
    if (receipt.status !== 'CODE_VERIFIED') process.exitCode = 2;
  } catch (error) {
    process.stderr.write('Read-only CI receipt blocked: '+error.message+'\n');
    process.exitCode = 2;
  }
}
if (require.main === module) main();
module.exports = {REPO, BRANCH, WORKFLOW, REQUIRED, evaluate, readOnce, main};
