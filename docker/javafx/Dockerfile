FROM maven:3.9.9-eclipse-temurin-21

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libgtk-3-0 \
        libx11-6 \
        libxext6 \
        libxrender1 \
        libxtst6 \
        libxi6 \
        libgl1 \
        libasound2t64 \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/javafx-app

CMD ["bash", "-lc", "tail -f /dev/null"]
