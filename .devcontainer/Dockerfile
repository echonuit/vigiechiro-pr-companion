# Base image for devcontainer with Java/JavaFX support
FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Install dependencies for JavaFX GUI applications.
# xvfb fournit un display virtuel pour ./mvnw test si l'utilisateur
# désactive le feature desktop-lite (qui démarre déjà son propre Xvfb).
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
        libgtk-3-0 \
        libglu1-mesa \
        libxrender1 \
        libxtst6 \
        libxi6 \
        xvfb \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
