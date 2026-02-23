<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260223145000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Repair google_fit_accounts schema: id primary key auto_increment + unique user_id + foreign key';
    }

    public function up(Schema $schema): void
    {
        $tableExists = (int) $this->connection->fetchOne(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'google_fit_accounts'"
        );
        if ($tableExists === 0) {
            return;
        }

        $hasPrimaryKey = (int) $this->connection->fetchOne(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'google_fit_accounts'
               AND CONSTRAINT_TYPE = 'PRIMARY KEY'"
        );
        if ($hasPrimaryKey === 0) {
            $this->addSql('ALTER TABLE google_fit_accounts ADD PRIMARY KEY (id)');
        }

        $idExtra = (string) ($this->connection->fetchOne(
            "SELECT COALESCE(EXTRA, '') FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'google_fit_accounts'
               AND COLUMN_NAME = 'id'"
        ) ?? '');
        if (stripos($idExtra, 'auto_increment') === false) {
            $this->addSql('ALTER TABLE google_fit_accounts MODIFY id INT AUTO_INCREMENT NOT NULL');
        }

        $hasUniqueUser = (int) $this->connection->fetchOne(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
             INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
                     ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                    AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
                    AND tc.TABLE_NAME = kcu.TABLE_NAME
             WHERE tc.TABLE_SCHEMA = DATABASE()
               AND tc.TABLE_NAME = 'google_fit_accounts'
               AND tc.CONSTRAINT_TYPE = 'UNIQUE'
               AND kcu.COLUMN_NAME = 'user_id'"
        );
        if ($hasUniqueUser === 0) {
            $this->addSql('CREATE UNIQUE INDEX UNIQ_ABCBC1DBA76ED395 ON google_fit_accounts (user_id)');
        }

        $hasForeignKeyUser = (int) $this->connection->fetchOne(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'google_fit_accounts'
               AND COLUMN_NAME = 'user_id'
               AND REFERENCED_TABLE_NAME = 'users'
               AND REFERENCED_COLUMN_NAME = 'id'"
        );
        if ($hasForeignKeyUser === 0) {
            $this->addSql('ALTER TABLE google_fit_accounts ADD CONSTRAINT FK_ABCBC1DBA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        // no-op
    }
}

