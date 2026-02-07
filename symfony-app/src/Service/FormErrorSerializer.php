<?php

namespace App\Service;

use Symfony\Component\Form\FormInterface;

class FormErrorSerializer
{
    /**
     * @return array<string, array<int, string>>
     */
    public function toArray(FormInterface $form): array
    {
        $errors = [];
        foreach ($form->getErrors(true) as $error) {
            $origin = $error->getOrigin();
            $name = $origin ? $origin->getName() : 'form';
            $errors[$name][] = $error->getMessage();
        }
        return $errors;
    }
}
