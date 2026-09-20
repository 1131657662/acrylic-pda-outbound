"""Isolated HTTPS static preview; synthetic TLS key exists only for this process."""
import http.server
import ipaddress
import ssl
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID


class StaticPreview(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(Path("dist").resolve()), **kwargs)

    def do_GET(self):
        if self.path.split("?", 1)[0] in ("/pda-download", "/login", "/devices"):
            self.path = "/index.html"
        super().do_GET()

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, format, *args):
        pass


def tls_context(directory):
    key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")])
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(minutes=1))
            .not_valid_after(now + timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]), False)
            .sign(key, hashes.SHA256()))
    key_path = Path(directory) / "test.key"
    cert_path = Path(directory) / "test.crt"
    key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(cert_path, key_path)
    return context


if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="pda-download-test-") as temp:
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 5189), StaticPreview)
        server.socket = tls_context(temp).wrap_socket(server.socket, server_side=True)
        server.serve_forever()
