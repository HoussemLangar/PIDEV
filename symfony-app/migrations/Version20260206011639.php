<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260206011639 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        // this up() migration is auto-generated, please modify it to your needs
        $this->addSql('ALTER TABLE sante_quotidienne ADD user_id INT NOT NULL, CHANGE date date DATETIME NOT NULL');
        $this->addSql('ALTER TABLE sante_quotidienne ADD CONSTRAINT FK_7D27C4F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id)');
        $this->addSql('CREATE INDEX IDX_7D27C4F7A76ED395 ON sante_quotidienne (user_id)');
    }

    public function down(Schema $schema): void
    {
        // this down() migration is auto-generated, please modify it to your needs
        $this->addSql('ALTER TABLE sante_quotidienne DROP FOREIGN KEY FK_7D27C4F7A76ED395');
        $this->addSql('DROP INDEX IDX_7D27C4F7A76ED395 ON sante_quotidienne');
        $this->addSql('ALTER TABLE sante_quotidienne DROP user_id, CHANGE date date VARCHAR(255) NOT NULL');
    }
}
