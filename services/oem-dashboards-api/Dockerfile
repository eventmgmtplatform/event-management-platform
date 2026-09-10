FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home dashboard && mkdir /config && chown dashboard:dashboard /config
COPY dashboard.py delivery.py collection.py cli.py server.py ./
USER 10001
ENV OEM_BIND=0.0.0.0 OEM_PORT=8092 PYTHONDONTWRITEBYTECODE=1
EXPOSE 8092
HEALTHCHECK --interval=15s --timeout=5s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8092/health', timeout=3)"
CMD ["python", "server.py"]
