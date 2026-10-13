"""Web-session/CSRF transport; no PDA token, redirect or persisted credential."""
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request
from uuid import UUID

from .integrity import parse


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Unexpected redirect; original request remains unresolved")


class RelayHttpError(RuntimeError):
    def __init__(self, status, code):
        super().__init__(f"HTTP {status}: {code}; original files and request keys retained")
        self.status, self.code = status, code


def base_url(value):
    url = urllib.parse.urlsplit(value)
    if url.username or url.password or url.query or url.fragment or url.path not in ("", "/"):
        raise ValueError("Use a server origin without credentials or a path")
    if not url.hostname or url.scheme not in ("http", "https"):
        raise ValueError("Use HTTPS or an isolated loopback HTTP origin")
    if url.scheme == "http" and url.hostname not in ("127.0.0.1", "localhost", "::1"):
        raise ValueError("HTTP is limited to loopback; TLS verification cannot be disabled")
    return urllib.parse.urlunsplit((url.scheme, url.netloc, "", "", ""))


class RelayWeb:
    def __init__(self, server, warehouse):
        self.server = base_url(server)
        self.warehouse = str(UUID(warehouse))
        self.opener = urllib.request.build_opener(NoRedirect(),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = {}
        self.epoch = None

    def request(self, path, body=None, content_type="application/json", key=None):
        headers = {"Accept": "application/json"}
        if body is not None:
            headers.update(self.csrf)
            headers["Content-Type"] = content_type
            if self.epoch:
                headers["X-Server-Recovery-Epoch"] = self.epoch
            if key:
                headers["Idempotency-Key"] = str(UUID(key))
        request = urllib.request.Request(self.server + "/api/v1" + path, body, headers)
        try:
            with self.opener.open(request, timeout=45) as response:
                result = response.read(2 * 1024 * 1024 + 1)
        except urllib.error.HTTPError as error:
            raw = error.read(65537)
            try:
                code = parse(raw).get("code", "REQUEST_REJECTED")
            except Exception:
                code = "REQUEST_REJECTED"
            raise RelayHttpError(error.code, code) from None
        if len(result) > 2 * 1024 * 1024:
            raise ValueError("Oversized response; do not infer success")
        return result

    def refresh(self):
        result = parse(self.request("/auth/csrf"))
        data = result["data"]
        if data["headerName"] not in ("X-CSRF-TOKEN", "X-XSRF-TOKEN"):
            raise ValueError("Unexpected CSRF header")
        self.csrf = {data["headerName"]: data["token"]}
        self.epoch = result["serverRecoveryEpoch"]

    def login(self, username, password):
        self.refresh()
        token = next(iter(self.csrf.values()))
        body = urllib.parse.urlencode({
            "username": username,
            "password": password,
            "_csrf": token,
        }).encode("utf-8")
        self.request("/auth/web/login", body, "application/x-www-form-urlencoded")
        self.refresh()

    def relay(self, path, body=None, key=None):
        route = "/original-relays" + path + "?warehouseId=" + self.warehouse
        return self.request(route, body, "application/zip" if path == "" else "application/json", key)

    def data(self, path, body=None, key=None):
        return parse(self.relay(path, body, key))["data"]
