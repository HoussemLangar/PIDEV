<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260301224500 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add performance indexes for messaging conversations and unread count queries';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('CREATE INDEX idx_message_recipient_read ON messages (recipient_id, is_read)');
        $this->addSql('CREATE INDEX idx_message_conversation_created ON messages (conversation_id, created_at)');
        $this->addSql('CREATE INDEX idx_conversation_user_one_last ON conversations (user_one_id, last_message_at)');
        $this->addSql('CREATE INDEX idx_conversation_user_two_last ON conversations (user_two_id, last_message_at)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX idx_message_recipient_read ON messages');
        $this->addSql('DROP INDEX idx_message_conversation_created ON messages');
        $this->addSql('DROP INDEX idx_conversation_user_one_last ON conversations');
        $this->addSql('DROP INDEX idx_conversation_user_two_last ON conversations');
    }
}
