"""Disposable PostgreSQL only. Does not touch platform containers or volumes."""
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid

name = "oem-dashboards-test-" + uuid.uuid4().hex[:10]
def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()

try:
    docker("run", "--rm", "-d", "--name", name, "-p", "127.0.0.1::5432", "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:17")
    port = docker("port", name, "5432/tcp").split(":")[-1]
    for attempt in range(40):
        result = subprocess.run(["docker", "exec", name, "pg_isready", "-U", "postgres"], capture_output=True)
        if result.returncode == 0: break
        time.sleep(.5)
    else: raise RuntimeError("Isolated PostgreSQL did not become ready")
    env = {**os.environ, "OEM_TEST_DSN": f"host=127.0.0.1 port={port} dbname=postgres user=postgres"}
    result = subprocess.run([sys.executable, str(Path(__file__).with_name("test_dashboard.py")), "-v"], env=env)
    raise SystemExit(result.returncode)
finally:
    subprocess.run(["docker", "rm", "-f", "-v", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
