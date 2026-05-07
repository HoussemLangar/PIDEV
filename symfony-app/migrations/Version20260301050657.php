<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260301050657 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        // this up() migration is auto-generated, please modify it to your needs
        $this->addSql('ALTER TABLE google_fit_accounts DROP FOREIGN KEY FK_2E7F8B6FA76ED395');
        $this->addSql('ALTER TABLE google_fit_accounts ADD CONSTRAINT FK_ABCBC1DBA76ED395 FOREIGN KEY (user_id) REFERENCES users (id)');
        $this->addSql('ALTER TABLE sante_quotidienne DROP FOREIGN KEY FK_7D27C4F7A76ED395');
        $this->addSql('ALTER TABLE sante_quotidienne CHANGE poids poids DOUBLE PRECISION DEFAULT NULL, CHANGE taille taille DOUBLE PRECISION DEFAULT NULL, CHANGE humeur humeur JSON NOT NULL, CHANGE date date DATETIME DEFAULT NULL, CHANGE calories calories INT DEFAULT NULL');
        $this->addSql('ALTER TABLE sante_quotidienne ADD CONSTRAINT FK_7D27C4F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE shared_documents CHANGE file_content file_content LONGTEXT DEFAULT NULL');
        $this->addSql('ALTER TABLE users CHANGE avatar_data avatar_data LONGTEXT DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        // this down() migration is auto-generated, please modify it to your needs
        $this->addSql('ALTER TABLE google_fit_accounts DROP FOREIGN KEY FK_ABCBC1DBA76ED395');
        $this->addSql('ALTER TABLE google_fit_accounts ADD CONSTRAINT FK_2E7F8B6FA76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE CASCADE');
        $this->addSql('ALTER TABLE sante_quotidienne DROP FOREIGN KEY FK_7D27C4F7A76ED395');
        $this->addSql('ALTER TABLE sante_quotidienne CHANGE poids poids DOUBLE PRECISION NOT NULL, CHANGE taille taille DOUBLE PRECISION NOT NULL, CHANGE humeur humeur LONGTEXT NOT NULL, CHANGE calories calories DOUBLE PRECISION DEFAULT NULL, CHANGE date date DATETIME NOT NULL');
        $this->addSql('ALTER TABLE sante_quotidienne ADD CONSTRAINT FK_7D27C4F7A76ED395 FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE NO ACTION');
        $this->addSql('ALTER TABLE shared_documents CHANGE file_content file_content LONGBLOB NOT NULL');
        $this->addSql('ALTER TABLE users CHANGE avatar_data avatar_data LONGBLOB DEFAULT NULL');
    }
}
