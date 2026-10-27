#!/usr/bin/env python3
"""Fail-closed, read-only Zavro stage-1 policy validator (Python stdlib only)."""
import argparse
import json
import re
import sys
from pathlib import Path

SHA = re.compile(r"[0-9a-f]{40}\\Z")
CERT = re.compile(r"[0-9a-f]{64}\\Z")
SYSTEM_PREFIX = "tools/zavro-development-system-"


class PolicyError(ValueError):
    pass


def _unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise PolicyError("duplicate key: " + key)
        result[key] = value
    return result


def validate(policy):
    if not isinstance(policy, dict):
        raise PolicyError("policy must be an object")
    if policy.get("schema_version") != 1 or policy.get("stage") != 1:
        raise PolicyError("unsupported stage/schema")
    if policy.get("repo") != "laszko14/nira-browser":
        raise PolicyError("unexpected repository")
    if policy.get("stage_state") != "audit_only":
        raise PolicyError("stage 1 must be audit_only")
    branch = policy.get("devtools_branch")
    if not isinstance(branch, str) or not branch.startswith(SYSTEM_PREFIX):
        raise PolicyError("unsafe devtools branch")
    if policy.get("allowed_write_prefix") != SYSTEM_PREFIX:
        raise PolicyError("unsafe write prefix")
    base = policy.get("baseline")
    if not isinstance(base, dict) or base.get("ref") != "main":
        raise PolicyError("invalid baseline")
    if not isinstance(base.get("sha"), str) or not SHA.fullmatch(base["sha"]):
        raise PolicyError("invalid baseline SHA")
    flags = policy.get("controls")
    if not isinstance(flags, dict):
        raise PolicyError("missing controls")
    for key in ("allow_mutations", "allow_workflow_dispatch", "allow_release",
                "allow_merge", "allow_force_push"):
        if flags.get(key) is not False:
            raise PolicyError("stage 1 must disable: " + key)
    for key in ("require_fresh_ref_check", "require_commit_pin", "require_human_build_intent"):
        if flags.get(key) is not True:
            raise PolicyError("missing guard: " + key)
    targets = policy.get("targets")
    if not isinstance(targets, dict) or set(targets) != {"clean", "mpv"}:
        raise PolicyError("expected exactly two isolated targets")
    ids = {"clean": "com.prirai.android.nira.debug",
           "mpv": "com.prirai.android.nira.mpv.debug"}
    for name, target in targets.items():
        if not isinstance(target, dict):
            raise PolicyError("invalid target")
        ref, sha = target.get("reference_branch"), target.get("reference_sha")
        if not isinstance(ref, str) or not ref.startswith("feat/zavro"):
            raise PolicyError("unsafe target ref: " + name)
        if not isinstance(sha, str) or not SHA.fullmatch(sha):
            raise PolicyError("target must be pinned to full SHA: " + name)
        if target.get("application_id_debug") != ids[name]:
            raise PolicyError("unexpected debug package: " + name)
        if target.get("apk_abi") != "arm64-v8a":
            raise PolicyError("unsupported ABI")
    signing = policy.get("signing")
    if not isinstance(signing, dict):
        raise PolicyError("missing signing policy")
    if not isinstance(signing.get("expected_certificate_sha256"), str) or not CERT.fullmatch(signing["expected_certificate_sha256"]):
        raise PolicyError("invalid public certificate fingerprint")
    if signing.get("secret_values_stored") is not False or signing.get("secrets_inspection") != "forbidden":
        raise PolicyError("secret-handling guard violated")
    return True


def load(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=_unique_pairs)
    except (OSError, json.JSONDecodeError) as error:
        raise PolicyError(str(error)) from error


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default=str(Path(__file__).with_name("policy.json")))
    args = parser.parse_args(argv)
    try:
        validate(load(args.path))
    except PolicyError as error:
        print("POLICY FAIL:", error, file=sys.stderr)
        return 1
    print("POLICY PASS: stage-1 read-only baseline validated; no build dispatched")
    return 0


if __name__ == "__main__":
    sys.exit(main())
