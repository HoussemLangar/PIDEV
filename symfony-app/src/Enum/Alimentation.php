<?php

namespace App\Enum;

enum Alimentation: string
{
    case FAIBLE         = 'faible';
    case MOYENNE        = 'moyenne';
    case BONNE          = 'bonne';
    case TRES_BONNE     = 'tres_bonne';
    case EXCELLENTE     = 'excellente';

    public function getLabel(): string
    {
        return match($this) {
            self::FAIBLE      => 'Faible 😕',
            self::MOYENNE     => 'Moyenne 😐',
            self::BONNE       => 'Bonne 🙂',
            self::TRES_BONNE  => 'Très bonne 😊',
            self::EXCELLENTE  => 'Excellente 🔥',
        };
    }

    public function getDescription(): string
    {
        return match($this) {
            self::FAIBLE      => 'Peu d’aliments sains, beaucoup de transformés / fast-food / sucré',
            self::MOYENNE     => 'Mélange équilibré certains jours, mais irrégulier',
            self::BONNE       => 'Majoritairement saine, légumes, protéines, peu de produits ultra-transformés',
            self::TRES_BONNE  => 'Très bonne variété, aliments frais, peu de sucres ajoutés',
            self::EXCELLENTE  => 'Excellente qualité, très équilibrée, riche en nutriments, bien planifiée',
        };
    }

    // Optionnel : pour afficher des couleurs / badges
    public function getColor(): string
    {
        return match($this) {
            self::FAIBLE      => '#ef4444',   // rouge
            self::MOYENNE     => '#f59e0b',   // orange
            self::BONNE       => '#84cc16',   // vert clair
            self::TRES_BONNE  => '#22c55e',   // vert
            self::EXCELLENTE  => '#15803d',   // vert foncé
        };
    }
}