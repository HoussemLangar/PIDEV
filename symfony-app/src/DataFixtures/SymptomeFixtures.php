<?php

namespace App\DataFixtures;

use App\Entity\SymptomeListe;
use Doctrine\Bundle\FixturesBundle\Fixture;
use Doctrine\Persistence\ObjectManager;

class SymptomeFixtures extends Fixture
{
    public function load(ObjectManager $manager): void
    {
        $symptomes = [
            ['nom' => 'Maux de tête', 'categorie' => 'Neurologique'],
            ['nom' => 'Nausées', 'categorie' => 'Digestif'],
            ['nom' => 'Fatigue', 'categorie' => 'Général'],
            ['nom' => 'Douleurs abdominales', 'categorie' => 'Digestif'],
            ['nom' => 'Fièvre', 'categorie' => 'Général'],
            // Ajoutez vos symptômes ici
        ];

        foreach ($symptomes as $data) {
            $symptome = new SymptomeListe();
            $symptome->setNom($data['nom']);
            $symptome->setCategorie($data['categorie']);
            $symptome->setCreatedAt(new \DateTime());

            $manager->persist($symptome);
        }

        $manager->flush();
    }
}