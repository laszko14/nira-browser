"""Pure stdlib tests: no GitHub calls, no network and no Android compilation."""
import copy
import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("zavro_policy", ROOT / "check_policy.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PolicyTests(unittest.TestCase):
    def setUp(self):
        self.policy = json.loads((ROOT / "policy.json").read_text(encoding="utf-8"))

    def test_valid_policy(self):
        self.assertTrue(module.validate(self.policy))

    def test_forbid_dispatch(self):
        p = copy.deepcopy(self.policy)
        p["controls"]["allow_workflow_dispatch"] = True
        with self.assertRaises(module.PolicyError):
            module.validate(p)

    def test_forbid_main_mutation(self):
        p = copy.deepcopy(self.policy)
        p["devtools_branch"] = "main"
        with self.assertRaises(module.PolicyError):
            module.validate(p)

    def test_reject_unpinned_target(self):
        p = copy.deepcopy(self.policy)
        p["targets"]["clean"]["reference_sha"] = "HEAD"
        with self.assertRaises(module.PolicyError):
            module.validate(p)

    def test_reject_wrong_variant_package(self):
        p = copy.deepcopy(self.policy)
        p["targets"]["mpv"]["application_id_debug"] = "com.prirai.android.nira.debug"
        with self.assertRaises(module.PolicyError):
            module.validate(p)

    def test_reject_duplicate_json_keys(self):
        with self.assertRaises(module.PolicyError):
            json.loads('{"x":1,"x":2}', object_pairs_hook=module._unique_pairs)

    def test_secret_guard(self):
        p = copy.deepcopy(self.policy)
        p["signing"]["secret_values_stored"] = True
        with self.assertRaises(module.PolicyError):
            module.validate(p)


if __name__ == "__main__":
    unittest.main()
