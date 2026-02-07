<?php

namespace App\Form;

use App\Entity\Medicament;
use Symfony\Component\Form\AbstractType;
<<<<<<< HEAD
=======
use Symfony\Component\Form\Extension\Core\Type\CheckboxType;
>>>>>>> 31f0b74 (integration user + gestion pharmacie)
use Symfony\Component\Form\Extension\Core\Type\IntegerType;
use Symfony\Component\Form\Extension\Core\Type\MoneyType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class MedicamentType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('nom', TextType::class, [
                'required' => true,
            ])
            ->add('type', TextType::class, [
                'required' => false,
            ])
<<<<<<< HEAD
            ->add('description', TextareaType::class, [
                'required' => false,
            ])
=======
>>>>>>> 31f0b74 (integration user + gestion pharmacie)
            ->add('forme', TextType::class, [
                'required' => false,
            ])
            ->add('dosage', TextType::class, [
                'required' => false,
            ])
<<<<<<< HEAD
=======
            ->add('molecule', TextType::class, [
                'required' => false,
            ])
            ->add('effet', TextType::class, [
                'required' => false,
            ])
            ->add('laboratoire', TextType::class, [
                'required' => false,
            ])
            ->add('codeBarres', TextType::class, [
                'required' => false,
            ])
>>>>>>> 31f0b74 (integration user + gestion pharmacie)
            ->add('prix', MoneyType::class, [
                'required' => false,
                'currency' => 'EUR',
                'scale' => 2,
            ])
<<<<<<< HEAD
=======
            ->add('description', TextareaType::class, [
                'required' => false,
            ])
>>>>>>> 31f0b74 (integration user + gestion pharmacie)
            ->add('stock', IntegerType::class, [
                'required' => true,
                'empty_data' => '0',
            ])
<<<<<<< HEAD
            ->add('laboratoire', TextType::class, [
                'required' => false,
=======
            ->add('active', CheckboxType::class, [
                'required' => false,
                'label' => 'Actif',
>>>>>>> 31f0b74 (integration user + gestion pharmacie)
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Medicament::class,
        ]);
    }
}
