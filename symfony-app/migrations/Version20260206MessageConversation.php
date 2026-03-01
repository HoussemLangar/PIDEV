<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260206MessageConversation extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create message and conversation tables for messaging system';
    }

    public function up(Schema $schema): void
    {
        // Create conversations table
        $this->addSql('CREATE TABLE conversations (
            id INT AUTO_INCREMENT NOT NULL,
            user_one_id INT NOT NULL,
            user_two_id INT NOT NULL,
            created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            last_message_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            PRIMARY KEY(id),
            KEY IDX_CONVERSATION_USER_ONE (user_one_id),
            KEY IDX_CONVERSATION_USER_TWO (user_two_id),
            CONSTRAINT FK_CONVERSATION_USER_ONE FOREIGN KEY (user_one_id) REFERENCES users (id) ON DELETE CASCADE,
            CONSTRAINT FK_CONVERSATION_USER_TWO FOREIGN KEY (user_two_id) REFERENCES users (id) ON DELETE CASCADE
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');

        // Create messages table
        $this->addSql('CREATE TABLE messages (
            id INT AUTO_INCREMENT NOT NULL,
            conversation_id INT NOT NULL,
            sender_id INT NOT NULL,
            recipient_id INT NOT NULL,
            content LONGTEXT NOT NULL,
            created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            read_at DATETIME NULL COMMENT \'(DC2Type:datetime_immutable)\',
            is_read TINYINT(1) DEFAULT 0,
            PRIMARY KEY(id),
            KEY IDX_MESSAGE_CONVERSATION (conversation_id),
            KEY IDX_MESSAGE_SENDER (sender_id),
            KEY IDX_MESSAGE_RECIPIENT (recipient_id),
            CONSTRAINT FK_MESSAGE_CONVERSATION FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
            CONSTRAINT FK_MESSAGE_SENDER FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
            CONSTRAINT FK_MESSAGE_RECIPIENT FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS messages');
        $this->addSql('DROP TABLE IF EXISTS conversations');
    }
}
