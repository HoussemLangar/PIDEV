<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260206220000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Link abonnements to users';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE abonnements ADD user_id INT DEFAULT NULL');
        $this->addSql('ALTER TABLE abonnements ADD CONSTRAINT FK_9F79D15BA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL');
        $this->addSql('CREATE INDEX IDX_9F79D15BA76ED395 ON abonnements (user_id)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE abonnements DROP FOREIGN KEY FK_9F79D15BA76ED395');
        $this->addSql('DROP INDEX IDX_9F79D15BA76ED395 ON abonnements');
        $this->addSql('ALTER TABLE abonnements DROP user_id');
    }
}
