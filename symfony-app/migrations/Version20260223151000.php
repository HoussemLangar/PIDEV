<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260223151000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create article_scores table for ArticleScore entity';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('CREATE TABLE article_scores (contenu_id INT NOT NULL, score_article DOUBLE PRECISION DEFAULT 0 NOT NULL, nb_commentaires INT DEFAULT 0 NOT NULL, updated_at DATETIME NOT NULL, PRIMARY KEY(contenu_id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
        $this->addSql('ALTER TABLE article_scores ADD CONSTRAINT FK_EAC7D4A30756C23D FOREIGN KEY (contenu_id) REFERENCES contenu (id) ON DELETE CASCADE');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE article_scores DROP FOREIGN KEY FK_EAC7D4A30756C23D');
        $this->addSql('DROP TABLE article_scores');
    }
}
