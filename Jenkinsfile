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
        booleanParam(name: 'CLEANUP_AFTER_BUILD', defaultValue: true, description: 'Arreter les conteneurs en fin de pipeline')
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
# On purge les conteneurs du projet (noms fixes + anciens noms prefixed)
# pour forcer un create propre et eviter le chemin "recreate" de compose v1.
docker ps -a --format '{{.Names}}' | grep -E '^(pidev-|[0-9a-f]{12}_pidev-)' | xargs -r docker rm -f || true

# Purge des volumes DB HA pour garantir un bootstrap Galera deterministe en CI.
docker volume ls --format '{{.Name}}' | grep -E '(^|_)pidev_db-node[123]-data$|(^|_)pidev_proxysql-data$' | xargs -r docker volume rm -f || true
'''
            }
        }

        stage('Build Base Services') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose build web javafx
COMPOSE_CMD="./scripts/ci/compose" bash ./scripts/galera/start-cluster.sh 80 3
./scripts/ci/compose up -d web
'''
            }
        }

                stage('Galera Cluster Ready') {
                    steps {
                        sh '''#!/usr/bin/env bash
        set -euo pipefail

        cd "$PROJECT_DIR"

        COMPOSE_CMD="./scripts/ci/compose" bash ./scripts/galera/check-cluster.sh 80 3
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

./scripts/ci/compose run --rm -e DB_HOST=db-node1 -e DB_PORT=3306 javafx bash -lc 'cd /workspace/javafx-app && mvn -B test'
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

cd "$PROJECT_DIR"

wait_http_from_web() {
    local url="$1"
    local attempts="${2:-45}"
    local sleep_seconds="${3:-2}"

    for i in $(seq 1 "$attempts"); do
        if ./scripts/ci/compose exec -T web sh -lc "curl -fsS --connect-timeout 3 --max-time 10 '${url}' >/dev/null" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$sleep_seconds"
    done

    echo "Smoke check failed for URL: ${url}" >&2
    return 1
}

wait_web_from_web() {
    local attempts="${1:-45}"
    local sleep_seconds="${2:-2}"

    for i in $(seq 1 "$attempts"); do
        if ./scripts/ci/compose exec -T web sh -lc "curl -ksS --connect-timeout 3 --max-time 10 https://127.0.0.1/ >/dev/null || curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1/ >/dev/null" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$sleep_seconds"
    done

    echo "Smoke check failed for web endpoint: https://127.0.0.1/ or http://127.0.0.1/" >&2
    return 1
}

wait_web_from_web

wait_sql_proxy() {
    local attempts="${1:-40}"
    local sleep_seconds="${2:-3}"

    for i in $(seq 1 "$attempts"); do
        if ./scripts/ci/compose exec -T db-node2 mariadb --ssl=0 -h db -P 3306 -uroot -proot -e "SELECT 1 AS proxy_ok;" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$sleep_seconds"
    done

    echo "Smoke check failed for SQL endpoint via ProxySQL" >&2
    return 1
}

if [ "${ENABLE_OBSERVABILITY}" = "true" ]; then
    wait_http_from_web "http://prometheus:9090/-/healthy"
    wait_http_from_web "http://elasticsearch:9200"
    wait_http_from_web "http://kibana:5601/api/status" "90"
    wait_http_from_web "http://prometheus:9090/api/v1/query?query=mysql_global_status_wsrep_cluster_size" "90"
fi

wait_sql_proxy
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
