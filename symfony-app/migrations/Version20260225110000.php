<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260225110000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add payment_session_id to abonnements for Stripe idempotence';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE abonnements ADD payment_session_id VARCHAR(255) DEFAULT NULL');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_ABONNEMENTS_PAYMENT_SESSION_ID ON abonnements (payment_session_id)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX UNIQ_ABONNEMENTS_PAYMENT_SESSION_ID ON abonnements');
        $this->addSql('ALTER TABLE abonnements DROP payment_session_id');
    }
}
