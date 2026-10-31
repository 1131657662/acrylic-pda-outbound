"""Serve only this worktree's static build over isolated loopback HTTPS."""
import http.server
import runpy
import tempfile
from pathlib import Path

preview = runpy.run_path(str(Path(__file__).resolve().parents[1] / "serve-download.py"))


class DisplayPreview(preview["StaticPreview"]):
    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path.startswith("/api/"):
            self.send_error(404)
            return
        if "." not in path.rsplit("/", 1)[-1]:
            self.path = "/index.html"
        super().do_GET()


if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="datetime-display-") as temp:
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 5197), DisplayPreview)
        server.socket = preview["tls_context"](temp).wrap_socket(server.socket, server_side=True)
        server.serve_forever()
