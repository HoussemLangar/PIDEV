<?php

namespace App\Form;

use App\Entity\StockPharmacy;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\IntegerType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints\Length;
use Symfony\Component\Validator\Constraints\Range;

class StockUpdateType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('quantite', IntegerType::class, [
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Range(min: 0)],
            ])
            ->add('prixVente', TextType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Length(max: 20)],
            ])
            ->add('seuilAlerte', IntegerType::class, [
                'row_attr' => ['class' => 'form-field'],
                'constraints' => [new Range(min: 1)],
            ])
            ->add('dateExpiration', DateType::class, [
                'required' => false,
                'row_attr' => ['class' => 'form-field'],
                'widget' => 'single_text',
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => StockPharmacy::class,
        ]);
    }
}
