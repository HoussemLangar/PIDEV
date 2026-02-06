<?php

namespace App\Repository;

use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;
use Symfony\Component\Security\Core\Exception\UnsupportedUserException;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Security\Core\User\PasswordUpgraderInterface;

/**
 * @extends ServiceEntityRepository<User>
 *
 * @method User|null find($id, $lockMode = null, $lockVersion = null)
 * @method User|null findOneBy(array $criteria, array $orderBy = null)
 * @method User[]    findAll()
 * @method User[]    findBy(array $criteria, array $orderBy = null, $limit = null, $offset = null)
 */
class UserRepository extends ServiceEntityRepository implements PasswordUpgraderInterface
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, User::class);
    }

    /**
     * Sauvegarde un utilisateur dans la base de données
     */
    public function save(User $user, bool $flush = true): void
    {
        $this->getEntityManager()->persist($user);
        
        if ($flush) {
            $this->getEntityManager()->flush();
        }
    }

    /**
     * Supprime un utilisateur de la base de données
     */
    public function delete(User $user, bool $flush = true): void
    {
        $this->getEntityManager()->remove($user);
        
        if ($flush) {
            $this->getEntityManager()->flush();
        }
    }

    /**
     * Trouve un utilisateur par son ID
     */
    public function findById(int $id): ?User
    {
        return $this->find($id);
    }

    /**
     * Trouve un utilisateur par son email
     */
    public function findByEmail(string $email): ?User
    {
        return $this->findOneBy(['email' => $email]);
    }

    /**
     * Trouve un utilisateur par son username
     */
    public function findByUsername(string $username): ?User
    {
        return $this->findOneBy(['username' => $username]);
    }

    /**
     * Trouve tous les utilisateurs par rôle
     */
    public function findByRole(string $role): array
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.role = :role')
            ->setParameter('role', $role)
            ->orderBy('u.createdAt', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Trouve les utilisateurs créés récemment
     */
    public function findRecentUsers(int $limit = 10): array
    {
        return $this->createQueryBuilder('u')
            ->orderBy('u.createdAt', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Compte le nombre d'utilisateurs par rôle
     */
    public function countByRole(string $role): int
    {
        return $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.role = :role')
            ->setParameter('role', $role)
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Utilisé pour mettre à niveau (rehash) automatiquement le mot de passe au fil du temps.
     */
    public function upgradePassword(PasswordAuthenticatedUserInterface $user, string $newHashedPassword): void
    {
        if (!$user instanceof User) {
            throw new UnsupportedUserException(sprintf('Instances of "%s" are not supported.', \get_class($user)));
        }

        $user->setPassword($newHashedPassword);
        $this->save($user);
    }

    /**
     * Recherche des utilisateurs par nom ou prénom
     */
    public function searchByName(string $searchTerm): array
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.nom LIKE :search OR u.prenom LIKE :search OR u.username LIKE :search')
            ->setParameter('search', '%' . $searchTerm . '%')
            ->orderBy('u.nom', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Trouve les utilisateurs avec un rôle spécifique et actifs
     * (Vous pouvez ajouter un champ 'active' si nécessaire)
     */
    public function findActiveUsersByRole(string $role): array
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.role = :role')
            ->setParameter('role', $role)
            ->orderBy('u.nom', 'ASC')
            ->getQuery()
            ->getResult();
    }
}