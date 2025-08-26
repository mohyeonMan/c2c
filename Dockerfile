FROM eclipse-temurin:21-jdk-jammy

# psql 클라이언트 설치
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
