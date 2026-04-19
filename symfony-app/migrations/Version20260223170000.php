<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260223170000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Repair missing health_risk_predictions table when migration history is out of sync';
    }

    public function up(Schema $schema): void
    {
        $tableExists = (int) $this->connection->fetchOne(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'health_risk_predictions'"
        );

        if ($tableExists > 0) {
            return;
        }

        $this->addSql('CREATE TABLE health_risk_predictions (
            id INT AUTO_INCREMENT NOT NULL,
            user_id INT NOT NULL,
            prediction_date DATE NOT NULL,
            risk_htn DOUBLE PRECISION NOT NULL DEFAULT 0,
            risk_diabetes DOUBLE PRECISION NOT NULL DEFAULT 0,
            risk_depression DOUBLE PRECISION NOT NULL DEFAULT 0,
            risk_respiratory DOUBLE PRECISION NOT NULL DEFAULT 0,
            level_htn VARCHAR(20) NOT NULL DEFAULT \'LOW\',
            level_diabetes VARCHAR(20) NOT NULL DEFAULT \'LOW\',
            level_depression VARCHAR(20) NOT NULL DEFAULT \'LOW\',
            level_respiratory VARCHAR(20) NOT NULL DEFAULT \'LOW\',
            explanations_json JSON NOT NULL,
            feature_snapshot JSON DEFAULT NULL,
            updated_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            INDEX IDX_7FDCD3D5A76ED395 (user_id),
            UNIQUE INDEX uniq_health_risk_user_day (user_id, prediction_date),
            PRIMARY KEY(id)
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
        $this->addSql('ALTER TABLE health_risk_predictions ADD CONSTRAINT FK_7FDCD3D5A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
    }

    public function down(Schema $schema): void
    {
        $tableExists = (int) $this->connection->fetchOne(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'health_risk_predictions'"
        );

        if ($tableExists === 0) {
            return;
        }

        $this->addSql('ALTER TABLE health_risk_predictions DROP FOREIGN KEY FK_7FDCD3D5A76ED395');
        $this->addSql('DROP TABLE health_risk_predictions');
    }
}
