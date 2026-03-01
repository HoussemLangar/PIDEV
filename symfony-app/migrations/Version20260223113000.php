<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260223113000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Ajoute l\'historique de score IA utilisateur';
    }

    public function up(Schema $schema): void
    {
        $schemaManager = $this->connection->createSchemaManager();
        if ($schemaManager->tablesExist(['user_score_history'])) {
            return;
        }

        $this->addSql("CREATE TABLE user_score_history (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, score SMALLINT NOT NULL, activity_score SMALLINT NOT NULL, seniority_score SMALLINT NOT NULL, rule_compliance_score SMALLINT NOT NULL, sanctions_history_score SMALLINT NOT NULL, snapshot_type VARCHAR(50) DEFAULT 'daily' NOT NULL, created_at DATETIME NOT NULL COMMENT '(DC2Type:datetime_immutable)', INDEX idx_user_score_history_user_created (user_id, created_at), INDEX IDX_2FA66D57A76ED395 (user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB");
        $this->addSql('ALTER TABLE user_score_history ADD CONSTRAINT FK_2FA66D57A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE user_score_history');
    }
}
