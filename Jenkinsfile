pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Executer les tests Symfony et JavaFX')
        booleanParam(name: 'ENABLE_OBSERVABILITY', defaultValue: true, description: 'Demarrer Prometheus/Grafana/ELK')
        booleanParam(name: 'CLEANUP_AFTER_BUILD', defaultValue: false, description: 'Arreter les conteneurs en fin de pipeline')
    }

    environment {
        COMPOSE_DOCKER_CLI_BUILD = '1'
        DOCKER_BUILDKIT = '1'
        PROJECT_DIR = '/workspace/PIDEV'
        COMPOSE_PROJECT_ROOT = '/workspace/PIDEV'
        COMPOSE_HOST_PROJECT_ROOT = '/home/pi-dev/PIDEV'
        FORCE_DOCKER_COMPOSE_IMAGE = '1'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh '''#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$PROJECT_DIR"
tar -C "$WORKSPACE" -cf - . | tar -C "$PROJECT_DIR" -xf -
'''
            }
        }

        stage('Preflight') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

chmod +x ./scripts/ci/compose

command -v docker >/dev/null

./scripts/ci/compose version
docker version

./scripts/ci/compose config >/dev/null

# Evite le bug docker-compose v1 "ContainerConfig" sur des conteneurs stale.
docker ps -a --format '{{.Names}}' | grep -E '^[0-9a-f]{12}_pidev-' | xargs -r docker rm -f || true
'''
            }
        }

        stage('Build Base Services') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose build web javafx
./scripts/ci/compose up -d db web
'''
            }
        }

        stage('Symfony Validate') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose exec -T web composer install --no-interaction --prefer-dist

./scripts/ci/compose exec -T web php -v

./scripts/ci/compose exec -T web sh -lc 'if [ -x bin/console ]; then php bin/console lint:yaml config --parse-tags; fi'
'''
            }
        }

        stage('Symfony Tests') {
            when {
                expression { return params.RUN_TESTS }
            }
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose exec -T web sh -lc '
if [ -x bin/phpunit ]; then
  bin/phpunit
elif [ -x vendor/bin/phpunit ]; then
  vendor/bin/phpunit
else
  echo "phpunit introuvable, etape ignoree"
fi
'
'''
            }
        }

        stage('JavaFX Build') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose run --rm javafx bash -lc 'cd /workspace/javafx-app && mvn -B -DskipTests clean package'
'''
            }
        }

        stage('JavaFX Tests') {
            when {
                expression { return params.RUN_TESTS }
            }
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose run --rm javafx bash -lc 'cd /workspace/javafx-app && mvn -B test'
'''
            }
        }

        stage('Deploy Observability') {
            when {
                expression { return params.ENABLE_OBSERVABILITY }
            }
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose --profile observability up -d

if [ -x observability/kibana/provision-kibana-dashboard.sh ]; then
  ./observability/kibana/provision-kibana-dashboard.sh
fi
'''
            }
        }

        stage('Smoke Checks') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

curl -fsS http://localhost:8000 >/dev/null || true

if [ "${ENABLE_OBSERVABILITY}" = "true" ]; then
  curl -fsS http://localhost:9090/-/healthy >/dev/null
  curl -fsS http://localhost:9200 >/dev/null
  curl -fsS http://localhost:5601/api/status >/dev/null
fi
'''
            }
        }
    }

    post {
        always {
            sh '''#!/usr/bin/env bash
set +e

cd "$PROJECT_DIR"

./scripts/ci/compose ps || true

./scripts/ci/compose logs --no-color > compose.log || true
'''
            archiveArtifacts artifacts: 'compose.log', fingerprint: true, allowEmptyArchive: true
        }

        cleanup {
            script {
                if (params.CLEANUP_AFTER_BUILD) {
                    sh '''#!/usr/bin/env bash
set +e

cd "$PROJECT_DIR"

./scripts/ci/compose down --remove-orphans || true
'''
                }
            }
        }
    }
}
