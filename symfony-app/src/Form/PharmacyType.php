<?php

namespace App\Form;

use App\Entity\Pharmacy;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\CheckboxType;
use Symfony\Component\Form\Extension\Core\Type\EmailType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints\Email;
use Symfony\Component\Validator\Constraints\Length;
use Symfony\Component\Validator\Constraints\Regex;

class PharmacyType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('nom', TextType::class, [
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Length(max: 150)],
            ])
            ->add('adresse', TextType::class, [
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Length(max: 255)],
            ])
            ->add('ville', TextType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Length(max: 100)],
            ])
            ->add('telephone', TextType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [
                    new Length(max: 20),
                    new Regex(pattern: '/^[\d\s\+\-\(\)]+$/', message: 'Téléphone invalide.'),
                ],
            ])
            ->add('email', EmailType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Email(message: 'Email invalide.')],
            ])
            ->add('horaires', TextType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Length(max: 255)],
            ])
            ->add('latitude', TextType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'empty_data' => '36.806500',
                'constraints' => [new Regex(pattern: '/^-?\d+(\.\d+)?$/', message: 'Latitude invalide.')],
            ])
            ->add('longitude', TextType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'empty_data' => '10.181500',
                'constraints' => [new Regex(pattern: '/^-?\d+(\.\d+)?$/', message: 'Longitude invalide.')],
            ])
            ->add('active', CheckboxType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'label' => 'Actif',
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Pharmacy::class,
        ]);
    }
}
