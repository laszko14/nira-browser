"""G2 fixture-only tests; NOT device, app or GeckoView tests."""
import json
from html.parser import HTMLParser
import pathlib
import subprocess
import unittest

BASE = pathlib.Path(__file__).resolve().parents[1]
SITE = BASE / "site"

class HtmlRefs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.external = []
        self.scripts = []
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        for key in ("src", "href"):
            if key in attrs and "://" in attrs[key]:
                self.external.append(attrs[key])
        if tag == "script":
            self.scripts.append(attrs.get("src"))

class TestFixture(unittest.TestCase):
    def test_pages_are_local_and_script_only_from_fixture(self):
        for name in ("index.html", "popup.html", "video.html"):
            parser = HtmlRefs()
            parser.feed((SITE / name).read_text(encoding="utf-8"))
            self.assertEqual(parser.external, [], name)
            self.assertEqual(parser.scripts, ["/fixture.js"], name)

    def test_storage_and_popup_controls(self):
        html = (SITE / "index.html").read_text(encoding="utf-8")
        for token in ('value="P"', 'value="Q"', 'id="write"', 'id="read"',
                      'id="open-window"', 'id="open-noopener"',
                      'target="_blank"', 'rel="noopener"'):
            self.assertIn(token, html)
        js = (SITE / "fixture.js").read_text(encoding="utf-8")
        for token in ("indexedDB.open", "localStorage.setItem", "SameSite=Strict",
                      "window.open", "window.opener"):
            self.assertIn(token, js)

    def test_video_controls_and_observation(self):
        html = (SITE / "video.html").read_text(encoding="utf-8")
        for id in ("load-a", "load-b", "switch-b", "invalid-b", "source-list", "blob-b"):
            self.assertIn('id="' + id + '"', html)
        js = (SITE / "fixture.js").read_text(encoding="utf-8")
        for token in ("currentSrc", "loadedmetadata", "MutationObserver",
                      "readyState", "videoWidth", "durationchange"):
            self.assertIn(token, js)

    def test_video_assets_are_decodable_and_distinct(self):
        cases = {"a.mp4": (160, 90, 2.0), "b.mp4": (320, 180, 5.0)}
        for name, (width, height, seconds) in cases.items():
            file = SITE / "media" / name
            self.assertTrue(file.is_file() and file.stat().st_size > 1000, name)
            raw = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries",
                "stream=width,height,codec_type", "-show_entries", "format=duration",
                "-of", "json", str(file)], text=True)
            data = json.loads(raw)
            streams = [x for x in data["streams"] if x["codec_type"] == "video"]
            self.assertEqual(len(streams), 1)
            self.assertEqual((streams[0]["width"], streams[0]["height"]), (width, height))
            self.assertAlmostEqual(float(data["format"]["duration"]), seconds, delta=0.2)
        self.assertFalse((SITE / "media" / "invalid-b.mp4").exists())

    def test_server_does_not_allow_private_files_or_invalid_media(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("gv_g2", BASE / "serve-https.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for path in ("/.gitignore", "/../README.md", "/media/invalid-b.mp4"):
            self.assertNotIn(path, module.ALLOWED)
        self.assertIn("/media/a.mp4", module.ALLOWED)
        self.assertIn("/media/b.mp4", module.ALLOWED)

if __name__ == "__main__":
    unittest.main()
