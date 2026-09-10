FROM python:3.12-slim
WORKDIR /app
RUN useradd --uid 10002 mock && mkdir /data && chown mock:mock /data
COPY server.py seed.json ./
USER 10002
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=2)"
CMD ["python", "server.py"]
