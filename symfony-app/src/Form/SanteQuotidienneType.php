<?php

namespace App\Form;

use App\Entity\SanteQuotidienne;
use App\Enum\Alimentation;
use App\Enum\NiveauActivite;
use App\Enum\Humeur;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\EnumType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class SanteQuotidienneType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('poids', NumberType::class, [
                'label' => 'Poids (kg)',
                'attr' => ['step' => '0.1', 'min' => '30'],
                'required' => true,
                'invalid_message' => 'Veuillez entrer un nombre valide',
            ])
            ->add('taille', NumberType::class, [
                'label' => 'Taille (cm)',
                'attr' => ['min' => '100', 'max' => '250'],
                'required' => true,
                'invalid_message' => 'Veuillez entrer un nombre valide',
            ])
            ->add('imc', NumberType::class, [
                'label' => false,
                'attr' => ['readonly' => true, 'style' => 'display:none;', 'disabled' => true],
                'required' => false,
                'mapped' => false,
            ])
            ->add('tensionArterielle', NumberType::class, [
                'label' => 'Tension artérielle',
                'required' => false,
                'invalid_message' => 'Veuillez entrer un nombre valide',
            ])
            ->add('sommeil', NumberType::class, [
                'label' => 'Heures de sommeil',
                'required' => false,
                'attr' => ['min' => '0', 'max' => '24', 'step' => '0.5'],
                'invalid_message' => 'Veuillez entrer un nombre valide',
            ])
            ->add('activitePhysique', EnumType::class, [
                'class' => NiveauActivite::class,
                'label' => 'Niveau d\'activité physique',
                'placeholder' => 'Sélectionner...',
                'required' => false,
            ])
            ->add('humeur', EnumType::class, [
                'class' => Humeur::class,
                'label' => 'Humeur(s) du jour',
                'multiple' => true,
                'expanded' => true,   // affiche des cases à cocher (checkboxes)
                'required' => true,
            ])
            ->add('alimentation', EnumType::class, [
                'class' => Alimentation::class,
                'label' => 'Qualité de l\'alimentation',
                'placeholder' => 'Sélectionner...',
                'required' => false,
            ])
            ->add('eauBue', NumberType::class, [
                'label' => 'Eau bue (litres)',
                'required' => false,
                'attr' => ['step' => '0.1', 'min' => '0'],
                'invalid_message' => 'Veuillez entrer un nombre valide',
            ])
            ->add('date', DateType::class, [
                'label' => 'Date',
                'widget' => 'single_text',
                'required' => true,
                'attr' => [
                    'max' => (new \DateTime())->format('Y-m-d'),
                ],
            ]);
    }

   public function configureOptions(OptionsResolver $resolver): void
{
    $resolver->setDefaults([
        'data_class' => SanteQuotidienne::class,
        'attr' => ['novalidate' => 'novalidate'],   // ← ajouté ici
    ]);
}
}