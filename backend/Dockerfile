# syntax=docker/dockerfile:1
FROM python:3.12-slim-bookworm@sha256:e697c81897302a4c4333bade000d98038fbb21eb064f8ee1fd42d02ebcd9d337
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 RUNMONI_DB_PATH=/var/lib/runmoni/runmoni.sqlite3
WORKDIR /app
COPY backend/requirements.lock.txt /app/backend/requirements.lock.txt
RUN --mount=type=secret,id=proxy_ca,required=false \
    if [ -f /run/secrets/proxy_ca ]; then export PIP_CERT=/run/secrets/proxy_ca; fi; \
    pip install --no-cache-dir --disable-pip-version-check -r backend/requirements.lock.txt
COPY --chown=10001:10001 --chmod=0644 backend/__init__.py backend/main.py backend/models.py backend/schema.sql /app/backend/
RUN groupadd --gid 10001 runmoni && useradd --uid 10001 --gid 10001 --no-create-home runmoni \
    && chmod 0755 /app /app/backend \
    && mkdir -p /var/lib/runmoni && chown 10001:10001 /var/lib/runmoni
USER 10001:10001
EXPOSE 8080
CMD ["python", "-m", "uvicorn", "backend.main:app", "--host", "0.0.0.0", "--port", "8080", "--workers", "1", "--no-access-log"]
