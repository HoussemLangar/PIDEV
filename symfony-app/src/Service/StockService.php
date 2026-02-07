<?php

namespace App\Service;

use App\Entity\StockPharmacy;
use App\Repository\StockPharmacyRepository;

class StockService
{
    public function __construct(private StockPharmacyRepository $stockRepository) {}

    public function decreaseStock(StockPharmacy $stock, int $qty): void
    {
        $newQty = max(0, $stock->getQuantite() - $qty);
        $stock->setQuantite($newQty);
        $stock->setUpdatedAt(new \DateTimeImmutable());
        $this->stockRepository->save($stock);
    }
}
