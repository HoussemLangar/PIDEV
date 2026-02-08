<?php

namespace App\Form;

use App\Entity\SymptomeQuotidien;
use App\Entity\SymptomeListe;
use App\Repository\SymptomeListeRepository;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\IntegerType;
use Symfony\Component\Form\Extension\Core\Type\RangeType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\SubmitType;
use Symfony\Component\Form\Extension\Core\Type\HiddenType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class SymptomeQuotidienType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $categorieSelectionnee = $options['categorie_selectionnee'] ?? '';
        
        $builder
            ->add('dateSymptome', DateType::class, [
                'label' => '📅 Date du symptôme',
                'widget' => 'single_text',
                'data' => new \DateTime(),
                'format' => 'yyyy-MM-dd',
                'attr' => [
                    'class' => 'form-control',
                    'required' => true,
                    'type' => 'date'
                ]
            ])
            ->add('categorie', ChoiceType::class, [
                'label' => '🏷️ Catégorie',
                'choices' => [
                    'Cardiovasculaire' => 'Cardiovasculaire',
                    'Dermatologique' => 'Dermatologique',
                    'Digestif' => 'Digestif',
                    'Général' => 'Général',
                    'Musculo-squelettique' => 'Musculo-squelettique',
                    'Neurologique' => 'Neurologique',
                    'Ophtalmologique' => 'Ophtalmologique',
                    'ORL' => 'ORL',
                    'Psychologique' => 'Psychologique',
                    'Respiratoire' => 'Respiratoire'
                ],
                'mapped' => false,
                'required' => false,
                'data' => $categorieSelectionnee,
                'attr' => [
                    'class' => 'form-control'
                ]
            ])
            ->add('symptome', EntityType::class, [
                'label' => '🩺 Symptôme',
                'class' => SymptomeListe::class,
                'choice_label' => 'nom',
                'query_builder' => function (SymptomeListeRepository $repository) use ($categorieSelectionnee) {
                    $qb = $repository->createQueryBuilder('s');
                    
                    if (!empty($categorieSelectionnee)) {
                        $qb->where('s.categorie = :categorie')
                           ->setParameter('categorie', $categorieSelectionnee);
                    }
                    
                    return $qb->orderBy('s.nom', 'ASC');
                },
                'attr' => [
                    'class' => 'form-control'
                ],
                'constraints' => [
                    new Assert\NotNull(['message' => 'Veuillez sélectionner un symptôme.'])
                ]
            ])
            ->add('intensite', RangeType::class, [
                'label' => '📊 Intensité (1-10)',
                'attr' => [
                    'min' => 1,
                    'max' => 10,
                    'class' => 'intensity-slider'
                ],
                'constraints' => [
                    new Assert\Range([
                        'min' => 1,
                        'max' => 10,
                        'notInRangeMessage' => 'L\'intensité doit être entre {{ min }} et {{ max }}.'
                    ])
                ]
            ])
            ->add('duree', TextType::class, [
                'label' => '⏱️ Durée',
                'required' => false,
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'Ex: 30 minutes, 2 heures...'
                ]
            ])
            ->add('notes', TextareaType::class, [
                'label' => '📝 Notes complémentaires',
                'required' => false,
                'attr' => [
                    'class' => 'form-control',
                    'rows' => 3,
                    'placeholder' => 'Décrivez les circonstances, les déclencheurs...'
                ]
            ])
            ->add('submit', SubmitType::class, [
                'label' => '💾 Enregistrer le symptôme',
                'attr' => [
                    'class' => 'btn-save-beautiful'
                ]
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => SymptomeQuotidien::class,
            'translation_domain' => false,
            'categorie_selectionnee' => ''
        ]);
        
        $resolver->setAllowedTypes('categorie_selectionnee', 'string');
    }
}