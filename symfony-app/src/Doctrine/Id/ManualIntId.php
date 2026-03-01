<?php

namespace App\Doctrine\Id;

final class ManualIntId
{
    public static function generate(): int
    {
        return random_int(1, 2147483647);
    }
}
