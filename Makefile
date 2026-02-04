.PHONY: help start stop restart install-symfony clean logs

help: ## Affiche cette aide
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

start: ## Démarre les conteneurs Docker
	docker-compose up -d

stop: ## Arrête les conteneurs Docker
	docker-compose down

restart: ## Redémarre les conteneurs Docker
	docker-compose restart

install-symfony: ## Installe Symfony 6.4 dans le conteneur
	docker-compose exec web composer create-project symfony/skeleton:"6.4.*" temp
	docker-compose exec web sh -c "mv temp/* temp/.* . 2>/dev/null || true && rm -rf temp"
	docker-compose exec web composer require webapp
	docker-compose exec web composer require --dev symfony/maker-bundle
	docker-compose exec web composer require doctrine

clean: ## Supprime tous les conteneurs et volumes
	docker-compose down -v

logs: ## Affiche les logs en temps réel
	docker-compose logs -f

shell-web: ## Ouvre un shell dans le conteneur web
	docker-compose exec web bash

shell-javafx: ## Ouvre un shell dans le conteneur javafx
	docker-compose exec javafx bash

db-migrate: ## Exécute les migrations
	docker-compose exec web php bin/console doctrine:migrations:migrate --no-interaction

db-update: ## Met à jour le schéma de base de données
	docker-compose exec web php bin/console doctrine:schema:update --force
