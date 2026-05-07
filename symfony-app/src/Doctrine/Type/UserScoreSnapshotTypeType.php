<?php

namespace App\Doctrine\Type;

use App\Enum\UserScoreSnapshotType;
use Doctrine\DBAL\Platforms\AbstractPlatform;
use Doctrine\DBAL\Types\ConversionException;
use Doctrine\DBAL\Types\Type;

class UserScoreSnapshotTypeType extends Type
{
    public const NAME = 'user_score_snapshot_type';

    public function getName(): string
    {
        return self::NAME;
    }

    public function getSQLDeclaration(array $column, AbstractPlatform $platform): string
    {
        $column['length'] = $column['length'] ?? 50;

        return $platform->getStringTypeDeclarationSQL($column);
    }

    public function convertToDatabaseValue($value, AbstractPlatform $platform): ?string
    {
        if ($value === null) {
            return null;
        }

        if (!$value instanceof UserScoreSnapshotType) {
            throw new ConversionException(sprintf(
                'Invalid value for type "%s": expected %s|null, got %s.',
                self::NAME,
                UserScoreSnapshotType::class,
                get_debug_type($value)
            ));
        }

        return $value->value;
    }

    public function convertToPHPValue($value, AbstractPlatform $platform): ?UserScoreSnapshotType
    {
        if ($value === null || $value === '') {
            return null;
        }

        $enum = UserScoreSnapshotType::tryFrom((string) $value);

        if ($enum === null) {
            throw new ConversionException(sprintf(
                'Invalid value "%s" for type "%s". Allowed values: %s.',
                (string) $value,
                self::NAME,
                implode(', ', array_map(static fn (UserScoreSnapshotType $case): string => $case->value, UserScoreSnapshotType::cases()))
            ));
        }

        return $enum;
    }

    public function requiresSQLCommentHint(AbstractPlatform $platform): bool
    {
        return true;
    }
}
