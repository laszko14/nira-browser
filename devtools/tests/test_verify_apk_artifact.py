"""Small synthetic ZIPs: no real APK, keys, or Android compilation."""
import hashlib
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path
SPEC=importlib.util.spec_from_file_location("verify_apk_artifact",Path(__file__).resolve().parents[1]/"verify-apk-artifact.py")
V=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(V)

class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.archive=self.root/"artifact.zip"
    def make(self,files):
        with zipfile.ZipFile(self.archive,"w") as zip:
            for name,body in files.items():
                zip.writestr(name,body)
        return hashlib.sha256(self.archive.read_bytes()).hexdigest()
    def bad(self,digest,why):
        with self.assertRaisesRegex(V.ArtifactError,why):
            V.verify_archive(self.archive,digest,self.root)
    def test_one_apk_and_digest(self):
        dig=self.make({"zavro-arm64-v1174.apk":b"synthetic dummy"})
        self.assertEqual(V.verify_archive(self.archive,"sha256:"+dig,self.root).read_bytes(),b"synthetic dummy")
    def test_requires_digest(self):
        self.make({"x.apk":b"fake"});self.bad("bad","EXPECTED_DIGEST_REQUIRED")
    def test_wrong_hash(self):
        self.make({"x.apk":b"fake"});self.bad("a"*64,"ZIP_DIGEST_MISMATCH")
    def test_traversal(self):
        dig=self.make({"../unsafe.apk":b"x"});self.bad(dig,"UNSAFE_APK_ZIP_PATH")
    def test_extra_file(self):
        dig=self.make({"app.apk":b"x","secret.txt":b"x"});self.bad(dig,"EXPECTED_EXACTLY_ONE_APK")
    def test_empty_apk(self):
        dig=self.make({"app.apk":b""});self.bad(dig,"UNSAFE_APK_MEMBER")
    def test_wrong_extension(self):
        dig=self.make({"app.bin":b"x"});self.bad(dig,"UNSAFE_APK_ZIP_PATH")
    def test_corrupt_zip(self):
        self.archive.write_bytes(b"not zip")
        self.bad(hashlib.sha256(self.archive.read_bytes()).hexdigest(),"CORRUPT_ZIP")
    def test_missing_trusted_apksigner_script(self):
        dig=self.make({"app.apk":b"x"})
        with self.assertRaisesRegex(V.ArtifactError,"TRUSTED_VERIFIER_NOT_FOUND"):
            V.verify_binary(self.archive,dig,self.root/"missing.sh","com.prirai.android.nira.debug",1174,"0.5.22-alpha79-clean","a"*64)

if __name__=="__main__":
    unittest.main()
