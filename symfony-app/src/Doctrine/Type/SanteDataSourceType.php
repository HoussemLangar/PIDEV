<?php

namespace App\Doctrine\Type;

use App\Enum\SanteDataSource;
use Doctrine\DBAL\Platforms\AbstractPlatform;
use Doctrine\DBAL\Types\ConversionException;
use Doctrine\DBAL\Types\Type;

class SanteDataSourceType extends Type
{
    public const NAME = 'sante_data_source';

    public function getName(): string
    {
        return self::NAME;
    }

    public function getSQLDeclaration(array $column, AbstractPlatform $platform): string
    {
        $column['length'] = $column['length'] ?? 20;

        return $platform->getStringTypeDeclarationSQL($column);
    }

    public function convertToDatabaseValue($value, AbstractPlatform $platform): ?string
    {
        if ($value === null) {
            return null;
        }

        if (!$value instanceof SanteDataSource) {
            throw new ConversionException(sprintf(
                'Invalid value for type "%s": expected %s|null, got %s.',
                self::NAME,
                SanteDataSource::class,
                get_debug_type($value)
            ));
        }

        return $value->value;
    }

    public function convertToPHPValue($value, AbstractPlatform $platform): ?SanteDataSource
    {
        if ($value === null || $value === '') {
            return null;
        }

        $enum = SanteDataSource::tryFrom((string) $value);

        if ($enum === null) {
            throw new ConversionException(sprintf(
                'Invalid value "%s" for type "%s". Allowed values: %s.',
                (string) $value,
                self::NAME,
                implode(', ', array_map(static fn (SanteDataSource $case): string => $case->value, SanteDataSource::cases()))
            ));
        }

        return $enum;
    }

    public function requiresSQLCommentHint(AbstractPlatform $platform): bool
    {
        return true;
    }
}
