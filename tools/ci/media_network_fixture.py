#!/usr/bin/env python3
"""Local-only, deterministic CI fixtures for GeckoView network detector.

HTTP requests and signed URLs are never logged: summary exposes fixed category
counts only. Fixtures are intentionally tiny dummy responses, not playable media.
"""
import json
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

COUNTS = Counter()
HTML = b"""<!doctype html><html><head><meta name="viewport" content="width=device-width"></head>
<body><h1>Nira 3.2 network detector CI fixtures</h1><p id="status">loading</p>
<script>
(async () => {
  const files = [
    '/asset/clip.mp4', '/asset/clip.mp4', '/asset/clip.webm',
    '/asset/master.m3u8', '/asset/manifest.mpd',
    '/asset/segment-001.m4s', '/asset/error.mp4'
  ];
  const outcomes = await Promise.allSettled(files.map(async url => {
    const response = await fetch(url, { cache: 'no-store' });
    await response.arrayBuffer();
    return response.status;
  }));
  document.getElementById('status').textContent =
    'completed: ' + outcomes.filter(x => x.status === 'fulfilled').length;
})();
</script></body></html>"""
FIXTURES = {
    "/asset/clip.mp4": ("MP4", "video/mp4", b"ci dummy mp4 response"),
    "/asset/clip.webm": ("WEBM", "video/webm", b"ci dummy webm response"),
    "/asset/master.m3u8": ("HLS", "application/vnd.apple.mpegurl",
                            b"#EXTM3U\n#EXT-X-VERSION:3\n"),
    "/asset/manifest.mpd": ("DASH", "application/dash+xml",
                             b'<?xml version="1.0"?><MPD type="static"></MPD>'),
    "/asset/segment-001.m4s": ("SEGMENT", "video/mp4", b"ci dummy segment"),
    "/asset/error.mp4": ("HTML_MASQUERADE", "text/html", b"<h1>Not a video</h1>"),
}

class FixtureHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/_summary":
            raw = json.dumps({key: COUNTS[key] for key in
                ("PAGE", "MP4", "WEBM", "HLS", "DASH", "SEGMENT", "HTML_MASQUERADE")},
                sort_keys=True).encode()
            mime = "application/json"
        elif path == "/test.html":
            COUNTS["PAGE"] += 1
            raw, mime = HTML, "text/html; charset=utf-8"
        elif path in FIXTURES:
            key, mime, raw = FIXTURES[path]
            COUNTS[key] += 1
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, *_):
        pass  # No request URL, query string, sensitive headers or token output.

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8765), FixtureHandler).serve_forever()
