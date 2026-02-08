<?php

namespace App\Form;

use App\Entity\SymptomeListe;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Form\Extension\Core\Type\SubmitType;

class SymptomeListeType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            // Nom du symptôme - obligatoire
            ->add('nom', TextType::class, [
                'label' => 'Nom du symptôme',
                'label_attr' => ['class' => 'form-label'],
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'Ex : Maux de tête, Fatigue intense, Nausées...',
                    'maxlength' => 100,
                ],
                'translation_domain' => false,
            ])

            // Catégorie - sélection parmi les catégories existantes
            ->add('categorie', ChoiceType::class, [
                'label' => 'Catégorie',
                'label_attr' => ['class' => 'form-label'],
                'choices' => $options['categories'] ?? [],
                'attr' => [
                    'class' => 'form-control',
                ],
                'empty_data' => '',
                'choice_translation_domain' => false,
                'translation_domain' => false,
            ])
        ;
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => SymptomeListe::class,            
            'categories' => [],            
                        // Forcer la locale française
            'locale' => 'fr',
            'translation_domain' => false,
                        // Pour améliorer l’affichage des erreurs
            'error_mapping' => [
                'nom' => 'nom',
            ],
        ]);
    }
}