<?php

namespace App\Service;

use App\Entity\Pharmacy;

class PharmacySearchService
{
    /**
     * @param Pharmacy[] $pharmacies
     * @return array<int, array<string, mixed>>
     */
    public function withDistance(array $pharmacies, ?float $lat, ?float $lng): array
    {
        $items = [];
        foreach ($pharmacies as $pharmacy) {
            $distance = null;
            if ($lat !== null && $lng !== null && $pharmacy->getLatitude() && $pharmacy->getLongitude()) {
                $distance = $this->haversine($lat, $lng, (float) $pharmacy->getLatitude(), (float) $pharmacy->getLongitude());
            }
            $items[] = [
                'id' => $pharmacy->getId(),
                'nom' => $pharmacy->getNom(),
                'adresse' => $pharmacy->getAdresse(),
                'telephone' => $pharmacy->getTelephone(),
                'email' => $pharmacy->getEmail(),
                'horaires' => $pharmacy->getHoraires(),
                'latitude' => $pharmacy->getLatitude(),
                'longitude' => $pharmacy->getLongitude(),
                'distance' => $distance,
            ];
        }

        usort($items, fn ($a, $b) => ($a['distance'] ?? PHP_FLOAT_MAX) <=> ($b['distance'] ?? PHP_FLOAT_MAX));
        return $items;
    }

    private function haversine(float $lat1, float $lon1, float $lat2, float $lon2): float
    {
        $earth = 6371;
        $dLat = deg2rad($lat2 - $lat1);
        $dLon = deg2rad($lon2 - $lon1);
        $a = sin($dLat / 2) ** 2 + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLon / 2) ** 2;
        $c = 2 * asin(min(1, sqrt($a)));
        return $earth * $c;
    }
}
