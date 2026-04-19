<?php

namespace App\Enum;

enum Humeur: string
{
    case EXCELLENTE     = 'excellente';
    case HEUREUSE       = 'heureuse';
    case JOYEUSE        = 'joyeuse';
    case CONTENTE       = 'contente';
    case CALME          = 'calme';
    case PAISIBLE       = 'paisible';
    case NEUTRE         = 'neutre';
    case FATIGUEE       = 'fatiguee';
    case ENNUYEE        = 'ennuyee';
    case STRESSEE       = 'stressee';
    case ANXIEUSE       = 'anxieuse';
    case IRRITEE        = 'irritee';
    case FRUSTREE       = 'frustree';
    case TRISTE         = 'triste';
    case DEPRIMEE       = 'deprimee';
    case EN_COLERE      = 'en_colere';

    public function getLabel(): string
    {
        return match ($this) {
            self::EXCELLENTE    => 'Excellente 😄',
            self::HEUREUSE      => 'Heureuse 🙂',
            self::JOYEUSE       => 'Joyeuse 😊',
            self::CONTENTE      => 'Contente 😃',
            self::CALME         => 'Calme 😌',
            self::PAISIBLE      => 'Paisible 🌿',
            self::NEUTRE        => 'Neutre 😐',
            self::FATIGUEE      => 'Fatiguée 😴',
            self::ENNUYEE       => 'Ennuyée 😑',
            self::STRESSEE      => 'Stressée 😓',
            self::ANXIEUSE      => 'Anxieuse 😟',
            self::IRRITEE       => 'Irritée 😣',
            self::FRUSTREE      => 'Frustrée 😤',
            self::TRISTE        => 'Triste 😔',
            self::DEPRIMEE      => 'Déprimée 😞',
            self::EN_COLERE     => 'En colère 😠',
        };
    }

    // Optionnel : couleur associée (utile pour l'affichage)
    public function getColor(): string
    {
        return match ($this) {
            self::EXCELLENTE    => '#22c55e',  // vert vif
            self::HEUREUSE      => '#86efac',
            self::JOYEUSE       => '#fbbf24',
            self::CONTENTE      => '#fde047',
            self::CALME         => '#a5f3fc',
            self::PAISIBLE      => '#67e8f9',
            self::NEUTRE        => '#9ca3af',  // gris
            self::FATIGUEE      => '#d1d5db',
            self::ENNUYEE       => '#6b7280',
            self::STRESSEE      => '#f59e0b',  // orange
            self::ANXIEUSE      => '#f97316',
            self::IRRITEE       => '#ef4444',
            self::FRUSTREE      => '#dc2626',
            self::TRISTE        => '#6366f1',  // violet/bleu triste
            self::DEPRIMEE      => '#4f46e5',
            self::EN_COLERE     => '#b91c1c',  // rouge foncé
        };
    }

    
}