<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260223102000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Ajoute la date d\'attribution du mois gratuit IA (score >= 95)';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD ai_free_month_granted_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\'');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users DROP ai_free_month_granted_at');
    }
}
