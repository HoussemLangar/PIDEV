<?php

namespace App\Security;

class GeoIpService
{
    private string $dbPath;
    private string $apiUrl;

    public function __construct(string $geoIpDbPath, string $geoIpApiUrl)
    {
        $this->dbPath = $geoIpDbPath;
        $this->apiUrl = $geoIpApiUrl;
    }

    public function getCountry(?string $ip): ?string
    {
        if ($ip === null || $ip === '') {
            return null;
        }

        if ($this->isPrivateIp($ip)) {
            return 'LOCAL';
        }

        if (!class_exists(\GeoIp2\Database\Reader::class)) {
            return $this->getCountryFromApi($ip);
        }

        if (!is_file($this->dbPath)) {
            return $this->getCountryFromApi($ip);
        }

        try {
            $reader = new \GeoIp2\Database\Reader($this->dbPath);
            $record = $reader->country($ip);
            return $record->country->isoCode ?: $this->getCountryFromApi($ip);
        } catch (\Throwable) {
            return $this->getCountryFromApi($ip);
        }
    }

    private function getCountryFromApi(string $ip): ?string
    {
        if ($this->apiUrl === '') {
            return 'UNKNOWN';
        }

        $url = str_replace('{ip}', rawurlencode($ip), $this->apiUrl);
        $context = stream_context_create([
            'http' => [
                'timeout' => 2,
            ],
        ]);
        $response = @file_get_contents($url, false, $context);
        if ($response === false) {
            return 'UNKNOWN';
        }

        $data = json_decode($response, true);
        if (!is_array($data)) {
            return 'UNKNOWN';
        }

        if (!empty($data['country_code'])) {
            return strtoupper((string) $data['country_code']);
        }

        if (!empty($data['country'])) {
            return strtoupper((string) $data['country']);
        }

        return 'UNKNOWN';
    }

    private function isPrivateIp(string $ip): bool
    {
        if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV4) === false) {
            return false;
        }

        return str_starts_with($ip, '10.')
            || str_starts_with($ip, '192.168.')
            || (str_starts_with($ip, '172.') && $this->isPrivate172($ip))
            || $ip === '127.0.0.1';
    }

    private function isPrivate172(string $ip): bool
    {
        $parts = explode('.', $ip);
        if (count($parts) < 2) {
            return false;
        }
        $second = (int) $parts[1];
        return $second >= 16 && $second <= 31;
    }
}
