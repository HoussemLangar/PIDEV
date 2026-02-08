<?php

namespace App\Enum;

enum NiveauActivite: string
{
    case SEDENTAIRE  = 'sedentaire';
    case LEGER       = 'leger';
    case MODERE      = 'modere';
    case ACTIF       = 'actif';
    case TRES_ACTIF  = 'tres_actif';

    // optionnel : méthodes utiles
    public function label(): string
    {
        return match($this) {
            self::SEDENTAIRE => 'Sédentaire',
            self::LEGER      => 'Léger',
            self::MODERE     => 'Modéré',
            self::ACTIF      => 'Actif',
            self::TRES_ACTIF => 'Très actif',
        };
    }
}