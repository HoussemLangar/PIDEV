<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260207190000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add description to contenu and update default status';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE contenu ADD description LONGTEXT DEFAULT NULL');
        $this->addSql("ALTER TABLE contenu CHANGE statut statut VARCHAR(20) DEFAULT 'en_attente' NOT NULL");
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE contenu DROP description');
        $this->addSql("ALTER TABLE contenu CHANGE statut statut VARCHAR(20) DEFAULT 'publie' NOT NULL");
    }
}
