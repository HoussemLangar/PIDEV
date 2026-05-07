<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260223112000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create article_scores table for sentiment-based content ranking';
    }

    public function up(Schema $schema): void
    {
        $schemaManager = $this->connection->createSchemaManager();
        if ($schemaManager->tablesExist(['article_scores'])) {
            return;
        }

        $this->addSql('CREATE TABLE article_scores (contenu_id INT NOT NULL, score_article DOUBLE PRECISION DEFAULT 0 NOT NULL, nb_commentaires INT DEFAULT 0 NOT NULL, updated_at DATETIME NOT NULL, PRIMARY KEY(contenu_id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
        $this->addSql('ALTER TABLE article_scores ADD CONSTRAINT FK_ARTICLE_SCORES_CONTENU FOREIGN KEY (contenu_id) REFERENCES contenu (id) ON DELETE CASCADE');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE article_scores DROP FOREIGN KEY FK_ARTICLE_SCORES_CONTENU');
        $this->addSql('DROP TABLE article_scores');
    }
}
