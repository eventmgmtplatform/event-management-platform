FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py views.py ess_proxy.py .
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
EXPOSE 8094
HEALTHCHECK --interval=10s --timeout=4s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8094/health',timeout=3)"
CMD ["python", "server.py"]
