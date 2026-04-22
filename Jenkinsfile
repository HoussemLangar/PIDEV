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

# Defensive: Jenkins checkout may drop executable bit on this helper script.
chmod +x ./scripts/ci/compose || true

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

        stage('Symfony Database Migrations') {
            steps {
                sh '''#!/usr/bin/env bash
set -euo pipefail

cd "$PROJECT_DIR"

./scripts/ci/compose exec -T web sh -lc '
set -eu

echo "Preparing Doctrine database for APP_ENV=dev"
php bin/console doctrine:database:create --if-not-exists --no-interaction
php bin/console doctrine:migrations:sync-metadata-storage --no-interaction
php bin/console doctrine:migrations:migrate --no-interaction --allow-no-migration
php bin/console doctrine:query:sql "SELECT DATABASE() AS current_database;"

echo "Preparing Doctrine database for APP_ENV=test"
php bin/console doctrine:database:create --if-not-exists --no-interaction --env=test
php bin/console doctrine:migrations:sync-metadata-storage --no-interaction --env=test
php bin/console doctrine:migrations:migrate --no-interaction --allow-no-migration --env=test
php bin/console doctrine:query:sql "SELECT DATABASE() AS current_database;" --env=test
'
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
        if bash ./scripts/ci/compose exec -T web sh -lc "curl -fsS --connect-timeout 3 --max-time 10 '${url}' >/dev/null" >/dev/null 2>&1; then
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
        if bash ./scripts/ci/compose exec -T web sh -lc "curl -ksS --connect-timeout 3 --max-time 10 https://127.0.0.1/ >/dev/null || curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1/ >/dev/null" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$sleep_seconds"
    done

    echo "Smoke check failed for web endpoint: https://127.0.0.1/ or http://127.0.0.1/" >&2
    return 1
}

wait_web_from_web

normalize_proxysql_rules() {
    # Ensure ProxySQL runtime state is aligned with expected Galera mapping.
    bash ./scripts/ci/compose exec -T db mariadb --connect-timeout=3 --ssl=0 -h127.0.0.1 -P6032 -uadmin -padmin -Nse "
        UPDATE mysql_query_rules
        SET destination_hostgroup=20
        WHERE rule_id=100;

        UPDATE mysql_servers
        SET hostgroup_id=10
        WHERE hostname='db-node1';

        UPDATE mysql_servers
        SET hostgroup_id=20
        WHERE hostname IN ('db-node2','db-node3');

        LOAD MYSQL SERVERS TO RUNTIME;
        SAVE MYSQL SERVERS TO DISK;
        LOAD MYSQL QUERY RULES TO RUNTIME;
        SAVE MYSQL QUERY RULES TO DISK;

        SELECT hostgroup_id, hostname, status, weight
        FROM runtime_mysql_servers
        ORDER BY hostgroup_id, hostname;

        SELECT rule_id, destination_hostgroup, active
        FROM runtime_mysql_query_rules
        WHERE rule_id IN (90,100)
        ORDER BY rule_id;
    "
}

wait_proxysql_hostgroups() {
    local attempts="${1:-20}"
    local sleep_seconds="${2:-2}"
    local runtime_servers=""

    for i in $(seq 1 "$attempts"); do
        runtime_servers="$(bash ./scripts/ci/compose exec -T db mariadb --connect-timeout=2 --ssl=0 -h127.0.0.1 -P6032 -uadmin -padmin -Nse "SELECT hostgroup_id, hostname, status FROM runtime_mysql_servers ORDER BY hostgroup_id, hostname;" 2>/dev/null || true)"

        if echo "$runtime_servers" | awk '$1==10 && $3=="ONLINE" { writer=1 } $1==20 && $3=="ONLINE" { reader=1 } END { exit !(writer && reader) }'; then
            return 0
        fi

        sleep "$sleep_seconds"
    done

    echo "ProxySQL runtime hostgroups 10/20 are not ready (writer/reader)." >&2
    echo "$runtime_servers" >&2
    return 1
}

dump_sql_diagnostics() {
    echo "ProxySQL runtime_mysql_servers:" >&2
    bash ./scripts/ci/compose exec -T db mariadb --connect-timeout=3 --ssl=0 -h127.0.0.1 -P6032 -uadmin -padmin -Nse "SELECT hostgroup_id, hostname, status, weight FROM runtime_mysql_servers ORDER BY hostgroup_id, hostname;" || true

    echo "ProxySQL mysql_users:" >&2
    bash ./scripts/ci/compose exec -T db mariadb --connect-timeout=3 --ssl=0 -h127.0.0.1 -P6032 -uadmin -padmin -Nse "SELECT username, default_hostgroup, active FROM runtime_mysql_users ORDER BY username;" || true

    echo "Galera wsrep (db-node1):" >&2
    bash ./scripts/ci/compose exec -T db mariadb --connect-timeout=3 --ssl=0 -hdb-node1 -uroot -proot -Nse "SHOW STATUS WHERE Variable_name IN ('wsrep_cluster_size','wsrep_cluster_status','wsrep_local_state_comment');" || true
}

wait_sql_proxy() {
    local attempts="${1:-20}"
    local sleep_seconds="${2:-2}"

    for i in $(seq 1 "$attempts"); do
        if bash ./scripts/ci/compose exec -T db mariadb --connect-timeout=2 --ssl=0 -h127.0.0.1 -P3306 -usymfony -psymfony -D pidev -Nse "SELECT LAST_INSERT_ID() AS writer_ok;" >/dev/null 2>&1 &&
           bash ./scripts/ci/compose exec -T db mariadb --connect-timeout=2 --ssl=0 -h127.0.0.1 -P3306 -usymfony -psymfony -D pidev -Nse "SELECT 1 AS read_ok;" >/dev/null 2>&1; then
            return 0
        fi
        sleep "$sleep_seconds"
    done

    echo "Smoke check failed for SQL endpoint via ProxySQL" >&2

    dump_sql_diagnostics

    return 1
}

if [ "${ENABLE_OBSERVABILITY}" = "true" ]; then
    wait_http_from_web "http://prometheus:9090/-/healthy"
    wait_http_from_web "http://elasticsearch:9200"
    wait_http_from_web "http://kibana:5601/api/status" "90"
    wait_http_from_web "http://prometheus:9090/api/v1/query?query=mysql_global_status_wsrep_cluster_size" "90"
fi

COMPOSE_CMD="./scripts/ci/compose" bash ./scripts/galera/check-cluster.sh 40 3

normalize_proxysql_rules

if ! wait_proxysql_hostgroups; then
    dump_sql_diagnostics
    exit 1
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
