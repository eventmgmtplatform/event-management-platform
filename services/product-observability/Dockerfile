FROM python:3.12-slim
WORKDIR /app
COPY services/product-observability/collector.py ./
COPY infrastructure/opensearch/index-template.json ./
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
USER 65534:65534
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s CMD python -c "import time; assert time.time()-float(open('/tmp/last-success').read()) < 100"
CMD ["python", "collector.py"]
