FROM python:3.12-slim
WORKDIR /app
COPY client.py server.py ./
USER 10001
ENV PYTHONDONTWRITEBYTECODE=1
EXPOSE 8095
HEALTHCHECK --interval=15s --timeout=5s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8095/health',timeout=3)"
CMD ["python", "server.py"]
