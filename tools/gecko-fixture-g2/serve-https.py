#!/usr/bin/env python3
"""G2 controlled HTTPS fixture: no external calls, no app/device changes."""
import argparse
import ipaddress
from pathlib import Path
import ssl
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

ROOT = (Path(__file__).resolve().parent / "site").resolve()
ALLOWED = {
    "/", "/index.html", "/popup.html", "/video.html", "/fixture.js",
    "/style.css", "/media/a.mp4", "/media/b.mp4"
}

class FixtureHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def _allowed(self):
        return urlsplit(self.path).path in ALLOWED

    def do_GET(self):
        if not self._allowed():
            self.send_error(404, "not in fixture allowlist")
            return
        super().do_GET()

    def do_HEAD(self):
        if not self._allowed():
            self.send_error(404, "not in fixture allowlist")
            return
        super().do_HEAD()

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, max-age=0")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'none'; script-src 'self'; style-src 'self'; "
            "media-src 'self' blob:; connect-src 'self'; img-src 'self'; "
            "object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"
        )
        super().end_headers()

    def log_message(self, format_string, *args):
        # Prevent persistence of paths, queries, markers and possible tokens.
        pass

def main():
    parser = argparse.ArgumentParser(description="G2 fixture HTTPS only; loopback default")
    parser.add_argument("--cert", required=True, type=Path)
    parser.add_argument("--key", required=True, type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9443)
    parser.add_argument("--allow-remote", action="store_true")
    args = parser.parse_args()
    for name in (args.cert, args.key):
        path = name.resolve(strict=True)
        if path == ROOT or ROOT in path.parents:
            parser.error("TLS cert/key must be outside the served site directory")
    try:
        loopback = ipaddress.ip_address(args.host).is_loopback
    except ValueError:
        loopback = args.host == "localhost"
    if not loopback and not args.allow_remote:
        parser.error("Non-loopback bind requires --allow-remote and valid trusted TLS")
    if not 1 <= args.port <= 65535:
        parser.error("Invalid port")
    if not (ROOT / "media/a.mp4").is_file() or not (ROOT / "media/b.mp4").is_file():
        parser.error("Run bash make-media.sh first")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(str(args.cert), str(args.key))
    server = ThreadingHTTPServer((args.host, args.port), FixtureHandler)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print("G2 HTTPS fixture ready; loopback=" + str(loopback) + "; port=" + str(args.port), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
