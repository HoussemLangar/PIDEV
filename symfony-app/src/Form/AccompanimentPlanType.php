<?php

namespace App\Form;

use App\Entity\AccompanimentPlan;
use App\Entity\Patient;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\IntegerType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class AccompanimentPlanType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('title', TextType::class, [
                'label' => 'Titre du plan',
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'ex: Plan de remise en forme',
                ],
                'constraints' => [
                    new Assert\NotBlank(message: 'Le titre est obligatoire'),
                    new Assert\Length(['min' => 3, 'max' => 255]),
                ],
            ])
            ->add('objectives', TextareaType::class, [
                'label' => 'Objectifs',
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'Décrivez les objectifs du plan...',
                    'rows' => 4,
                ],
                'constraints' => [
                    new Assert\NotBlank(message: 'Les objectifs sont obligatoires'),
                ],
            ])
            ->add('description', TextareaType::class, [
                'label' => 'Description (optionnel)',
                'required' => false,
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'Informations supplémentaires sur le plan...',
                    'rows' => 3,
                ],
            ])
            ->add('durationWeeks', IntegerType::class, [
                'label' => 'Durée (semaines)',
                'attr' => [
                    'class' => 'form-control',
                    'min' => 1,
                    'max' => 52,
                ],
                'data' => 12,
            ])
            ->add('status', ChoiceType::class, [
                'label' => 'Statut',
                'choices' => [
                    'Actif' => 'active',
                    'Pausé' => 'paused',
                    'Complété' => 'completed',
                    'Annulé' => 'cancelled',
                ],
                'attr' => [
                    'class' => 'form-control',
                ],
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => AccompanimentPlan::class,
        ]);
    }
}
