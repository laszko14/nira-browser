import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';

const BRANCH = 'tools/zavro-development-system-stage1-20261027';
const REPO = 'laszko14/nira-browser';
const SHA = /^[0-9a-f]{40}$/;
const KEYS = ['approval','branch','kind','repo','schema_version','sha','variant','version_code','version_name'];

export class RequestError extends Error {}
function assert(ok, code) { if (!ok) throw new RequestError(code); }

export function validateRequest(request, config, filename) {
  assert(request && typeof request === 'object' && !Array.isArray(request), 'INVALID_OBJECT');
  assert(Object.keys(request).sort().join(',') === KEYS.join(','), 'UNEXPECTED_FIELDS');
  assert(request.kind === 'build' && request.approval === 'APPROVE_ONE_SIGNED_ARM64_BUILD', 'NO_APPROVAL');
  assert(request.schema_version === 1 && request.repo === REPO, 'WRONG_REPO');
  assert(request.variant === 'clean' || request.variant === 'mpv', 'INVALID_VARIANT');
  assert(config && config.repo === REPO && config.mode === 'preflight-only' &&
         config.execution_enabled === false && config.create_refs_enabled === false &&
         config.allow_releases === false, 'UNSAFE_CONTROLLER_CONFIG');
  const target = config.targets[request.variant];
  assert(target && target.branch === request.branch, 'BRANCH_MISMATCH');
  assert(typeof request.sha === 'string' && SHA.test(request.sha) && target.pinned_sha === request.sha, 'SHA_MISMATCH');
  assert(filename === 'devtools/build-requests/' + request.variant + '-' + request.sha + '.json', 'NON_IDEMPOTENT_FILENAME');
  assert(request.version_code === target.version_code && request.version_name === target.version_name, 'VERSION_MISMATCH');
  assert(target.abi === 'arm64-v8a', 'ABI_MISMATCH');
  assert(target.package === (request.variant === 'clean' ? 'com.prirai.android.nira.debug' : 'com.prirai.android.nira.mpv'), 'PACKAGE_MISMATCH');
  assert(SHA.test(config.targets.clean.pinned_sha) && SHA.test(config.targets.mpv.pinned_sha), 'INVALID_CONFIG_SHA');
  return {variant: request.variant, sha:request.sha, branch:request.branch,
          version_code:request.version_code, version_name:request.version_name,
          package:target.package, certificate_sha256:target.certificate_sha256};
}

export function validateDiff(diff, filename) {
  const rows = diff.trim().split('\n').filter(Boolean);
  assert(rows.length === 1, 'EXPECTED_SINGLE_CHANGE');
  const parts = rows[0].split('\t');
  assert(parts.length === 2 && parts[0] === 'A' && parts[1] === filename, 'UNSAFE_CHANGE');
}

export function validateEvent(env) {
  assert(env.GITHUB_REPOSITORY === REPO && env.GITHUB_REF === 'refs/heads/' + BRANCH, 'UNAUTHORIZED_REPO_REF');
  assert(env.GITHUB_ACTOR === 'laszko14', 'UNAUTHORIZED_ACTOR');
  assert(SHA.test(env.BEFORE_SHA || '') && SHA.test(env.GITHUB_SHA || ''), 'INVALID_EVENT_SHA');
}

function run() {
  const env = process.env;
  validateEvent(env);
  const changed = execFileSync('git', ['diff','--name-status','--no-renames', env.BEFORE_SHA, env.GITHUB_SHA], {encoding:'utf8'});
  const rows = changed.trim().split('\n').filter(Boolean);
  if (rows.length !== 1) throw new RequestError('EXPECTED_SINGLE_CHANGE');
  const fields = rows[0].split('\t');
  const filename = fields[1] || '';
  validateDiff(changed,filename);
  if (!/^devtools\/build-requests\/(clean|mpv)-[0-9a-f]{40}\.json$/.test(filename)) throw new RequestError('UNSAFE_PATH');
  const request = JSON.parse(fs.readFileSync(filename,'utf8'));
  const config = JSON.parse(fs.readFileSync(new URL('./build-control.json',import.meta.url),'utf8'));
  const result = validateRequest(request,config,filename);
  const output = 'status=ready\nvariant=' + result.variant + '\nsha=' + result.sha +
    '\nbranch=' + result.branch + '\npackage=' + result.package +
    '\nversion_code=' + result.version_code + '\nversion_name=' + result.version_name +
    '\ncertificate_sha256=' + result.certificate_sha256 + '\n';
  fs.appendFileSync(env.GITHUB_OUTPUT,output);
  console.log('BUILD_REQUEST_VALIDATED: ' + result.variant + ' / ' + result.sha + '; no build in preflight');
}

if(process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  try {run();} catch(e) {console.error('BUILD_REQUEST_REJECTED: ' + (e instanceof RequestError ? e.message : 'INVALID_INPUT'));process.exitCode=1;}
}
