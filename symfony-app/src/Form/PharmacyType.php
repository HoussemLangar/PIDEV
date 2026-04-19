<?php

namespace App\Form;

use App\Entity\Pharmacy;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\CheckboxType;
use Symfony\Component\Form\Extension\Core\Type\EmailType;
use Symfony\Component\Form\Extension\Core\Type\HiddenType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class PharmacyType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('nom', TextType::class, [
                'required' => true,
                'attr' => [
                    'id' => 'pharmacyName',
                    'placeholder' => 'Nom pharmacie *',
                ],
            ])
            ->add('adresse', TextType::class, [
                'required' => true,
                'attr' => [
                    'id' => 'pharmacyAddress',
                    'placeholder' => 'Adresse *',
                ],
            ])
            ->add('telephone', TextType::class, [
                'required' => true,
                'attr' => [
                    'id' => 'pharmacyPhone',
                    'placeholder' => 'Téléphone',
                ],
            ])
            ->add('email', EmailType::class, [
                'required' => true,
                'attr' => [
                    'id' => 'pharmacyEmail',
                    'placeholder' => 'Email',
                ],
            ])
            ->add('horaires', HiddenType::class, [
                'required' => false,
                'attr' => [
                    'id' => 'pharmacyHours',
                    'placeholder' => 'Horaires',
                ],
            ])
            ->add('latitude', NumberType::class, [
                'required' => false,
                'scale' => 6,
                'attr' => [
                    'id' => 'pharmacyLat',
                    'placeholder' => 'Latitude',
                ],
            ])
            ->add('longitude', NumberType::class, [
                'required' => false,
                'scale' => 6,
                'attr' => [
                    'id' => 'pharmacyLng',
                    'placeholder' => 'Longitude',
                ],
            ])
            ->add('isActive', CheckboxType::class, [
                'required' => false,
                'label' => 'Active',
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Pharmacy::class,
        ]);
    }
}
