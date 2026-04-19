<?php

namespace App\Service;

use App\Entity\Teleconsultation;
use App\Entity\User;
use Symfony\Component\DependencyInjection\ParameterBag\ParameterBagInterface;

/**
 * Service for managing Jitsi Meet integration
 * 
 * Configuration in .env:
 * JITSI_SERVER_URL=https://meet.jit.si
 * JITSI_ENABLE_AUDIO_VIDEO_RECORDING=false
 */
class JitsiService
{
    private string $serverUrl;
    private bool $startWithAudioMuted = true;
    private bool $startWithVideoMuted = false;

    public function __construct(ParameterBagInterface $params)
    {
        $this->serverUrl = $params->get('jitsi_server_url') ?? 'https://meet.jit.si';
    }

    /**
     * Generate a Jitsi room URL for a teleconsultation
     */
    public function generateRoomUrl(Teleconsultation $consultation, User $user): string
    {
        $roomName = $this->sanitizeRoomName($consultation->getRoomName());
        
        $params = [
            'userInfo.displayName' => $user->getUsername(),
            'config.startWithAudioMuted' => $this->startWithAudioMuted ? 'true' : 'false',
            'config.startWithVideoMuted' => $this->startWithVideoMuted ? 'true' : 'false',
            'config.disableSimulcast' => 'false',
            'config.p2p.enabled' => 'true',
        ];

        $query = http_build_query($params);
        
        return $this->serverUrl . '/' . $roomName . ($query ? '#' . $query : '');
    }

    /**
     * Generate the embedded iframe code
     */
    public function generateEmbedCode(Teleconsultation $consultation, User $user): string
    {
        $roomName = $this->sanitizeRoomName($consultation->getRoomName());
        $domain = $this->getServerDomain();
        
        $options = [
            'roomName' => $roomName,
            'width' => '100%',
            'height' => '600px',
            'parentNode' => 'meet',
            'userInfo' => [
                'displayName' => $user->getUsername(),
            ],
            'configOverwrite' => [
                'startWithAudioMuted' => $this->startWithAudioMuted,
                'startWithVideoMuted' => $this->startWithVideoMuted,
                'disableSimulcast' => false,
            ],
        ];

        $config = json_encode($options);

        return <<<HTML
        <script src="{$this->serverUrl}/external_api.js"></script>
        <div id="meet"></div>
        <script>
            var api = new JitsiMeetExternalAPI("{$domain}", {$config});
            api.addEventListener('videoConferenceJoined', function(event) {
                console.log('User joined the video conference');
            });
            api.addEventListener('videoConferenceLeft', function(event) {
                console.log('User left the video conference');
            });
        </script>
        HTML;
    }

    /**
     * Sanitize room name for Jitsi
     * Must be alphanumeric and hyphens
     */
    private function sanitizeRoomName(string $roomName): string
    {
        // Remove special characters
        $cleaned = preg_replace('/[^a-zA-Z0-9\-]/', '', $roomName);
        
        // Ensure it doesn't start or end with hyphen
        $cleaned = trim($cleaned, '-');
        
        // Ensure minimum length
        if (strlen($cleaned) < 3) {
            $cleaned = 'consultation-' . uniqid();
        }

        return strtolower($cleaned);
    }

    /**
     * Generate a unique room name for a consultation
     */
    public function generateRoomName(User $initiator, User $recipient): string
    {
        $timestamp = (new \DateTimeImmutable())->format('YmdHis');
        $hash = substr(md5($initiator->getId() . $recipient->getId() . $timestamp), 0, 8);
        
        return "call-{$timestamp}-{$hash}";
    }

    /**
     * Get server information
     */
    public function getServerUrl(): string
    {
        return $this->serverUrl;
    }

    public function getServerDomain(): string
    {
        $host = parse_url($this->serverUrl, PHP_URL_HOST);
        if (!empty($host)) {
            return $host;
        }

        return preg_replace('#^https?://#', '', rtrim($this->serverUrl, '/'));
    }

    /**
     * Set server URL (for testing or alternative servers)
     */
    public function setServerUrl(string $url): self
    {
        $this->serverUrl = $url;
        return $this;
    }

    /**
     * Check if Jitsi is properly configured
     */
    public function isConfigured(): bool
    {
        return !empty($this->serverUrl);
    }
}
