FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir cryptography==46.0.5
COPY server.py .
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8096/health',timeout=2)"
CMD ["python","server.py"]
