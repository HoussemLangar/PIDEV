# Run the development stack (local)

This helper explains how to start the project locally using Docker Compose.

Quick start (from project root):

```bash
# Start containers (build if needed)
./run.sh

# Check containers
docker ps

# View logs
docker-compose logs -f pidev-nginx pidev-web
```

Important:
- This script does NOT run database migrations or modify the database schema.
- If you need to run migrations do it manually after backing up the DB.
- To run commands inside the PHP container use e.g. `docker-compose exec pidev-web php bin/console`.
