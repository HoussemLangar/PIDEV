pipeline {
    agent any

    options {
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
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Preflight') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

command -v docker >/dev/null
command -v docker-compose >/dev/null

docker-compose version
docker version

docker-compose config >/dev/null

# Evite le bug docker-compose v1 "ContainerConfig" sur des conteneurs stale.
docker ps -a --format '{{.Names}}' | grep -E '^[0-9a-f]{12}_pidev-' | xargs -r docker rm -f || true
'''
            }
        }

        stage('Build Base Services') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

docker-compose build web javafx
docker-compose up -d db web
'''
            }
        }

        stage('Symfony Validate') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

docker-compose exec -T web composer install --no-interaction --prefer-dist

docker-compose exec -T web php -v

docker-compose exec -T web sh -lc 'if [ -x bin/console ]; then php bin/console lint:yaml config --parse-tags; fi'
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

docker-compose exec -T web sh -lc '
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

docker-compose run --rm javafx bash -lc 'cd /workspace/javafx-app && mvn -B -DskipTests clean package'
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

docker-compose run --rm javafx bash -lc 'cd /workspace/javafx-app && mvn -B test'
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

docker-compose --profile observability up -d

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

docker-compose ps

docker-compose logs --no-color > compose.log
'''
            archiveArtifacts artifacts: 'compose.log', fingerprint: true, allowEmptyArchive: true
        }

        cleanup {
            script {
                if (params.CLEANUP_AFTER_BUILD) {
                    sh '''#!/usr/bin/env bash
set +e

docker-compose down --remove-orphans
'''
                }
            }
        }
    }
}
