## Rasa Healthbot (Local)

This folder provides a local Rasa Open Source chatbot for the JavaFX app.

Manual start (if needed):

```bash
docker compose -f ml-services/rasa-healthbot/docker-compose.yml up -d
```

Health check:

```bash
curl -s http://localhost:5005/status
```

Stop:

```bash
docker compose -f ml-services/rasa-healthbot/docker-compose.yml down
```

