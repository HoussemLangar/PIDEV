<?php

namespace App\Repository;

use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;
use Symfony\Component\Security\Core\Exception\UnsupportedUserException;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Security\Core\User\PasswordUpgraderInterface;
use Symfony\Bridge\Doctrine\Security\User\UserLoaderInterface;
use Doctrine\ORM\QueryBuilder;

/**
 * @extends ServiceEntityRepository<User>
 *
 * @method User|null find($id, $lockMode = null, $lockVersion = null)
 * @method User|null findOneBy(array $criteria, array $orderBy = null)
 * @method User[]    findAll()
 * @method User[]    findBy(array $criteria, array $orderBy = null, $limit = null, $offset = null)
 */
class UserRepository extends ServiceEntityRepository implements PasswordUpgraderInterface, UserLoaderInterface
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
        return $this->createQueryBuilder('u')
            ->andWhere('u.email = :email')
            ->andWhere('u.deletedAt IS NULL')
            ->setParameter('email', $email)
            ->getQuery()
            ->getOneOrNullResult();
    }

    /**
     * Trouve un utilisateur par son username
     */
    public function findByUsername(string $username): ?User
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.username = :username')
            ->andWhere('u.deletedAt IS NULL')
            ->setParameter('username', $username)
            ->getQuery()
            ->getOneOrNullResult();
    }

    /**
     * Trouve tous les utilisateurs par rôle
     */
    public function findByRole(string $role): array
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.role = :role')
            ->andWhere('u.deletedAt IS NULL')
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
            ->andWhere('u.deletedAt IS NULL')
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
            ->andWhere('u.deletedAt IS NULL')
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
            ->andWhere('u.deletedAt IS NULL')
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
            ->andWhere('u.deletedAt IS NULL')
            ->setParameter('role', $role)
            ->orderBy('u.nom', 'ASC')
            ->getQuery()
            ->getResult();
    }

    /**
     * Charge un utilisateur pour l'authentification (exclut les comptes soft-deleted).
     */
    public function loadUserByIdentifier(string $identifier): ?User
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.email = :identifier')
            ->andWhere('u.deletedAt IS NULL')
            ->setParameter('identifier', $identifier)
            ->getQuery()
            ->getOneOrNullResult();
    }

    public function findPendingApprovals(): array
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.adminApproved = false')
            ->orderBy('u.createdAt', 'ASC')
            ->getQuery()
            ->getResult();
    }

    public function findByEmailVerificationToken(string $token): ?User
    {
        return $this->createQueryBuilder('u')
            ->andWhere('u.emailVerificationToken = :token')
            ->setParameter('token', $token)
            ->getQuery()
            ->getOneOrNullResult();
    }

    public function createValidationQueryBuilder(array $filters): \Doctrine\ORM\QueryBuilder
    {
        $qb = $this->createQueryBuilder('u')
            ->andWhere('u.deletedAt IS NULL');

        if (!empty($filters['q'])) {
            $qb->andWhere('u.email LIKE :q OR u.nom LIKE :q OR u.prenom LIKE :q')
                ->setParameter('q', '%' . $filters['q'] . '%');
        }

        if (($filters['email_verified'] ?? '') === 'yes') {
            $qb->andWhere('u.emailVerified = true');
        } elseif (($filters['email_verified'] ?? '') === 'no') {
            $qb->andWhere('u.emailVerified = false');
        }

        if (($filters['admin_approved'] ?? '') === 'yes') {
            $qb->andWhere('u.adminApproved = true');
        } elseif (($filters['admin_approved'] ?? '') === 'no') {
            $qb->andWhere('u.adminApproved = false');
        }

        return $qb->orderBy('u.createdAt', 'ASC');
    }

    public function getValidationStats(): array
    {
        $total = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->getQuery()
            ->getSingleScalarResult();

        $emailVerified = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.emailVerified = true')
            ->getQuery()
            ->getSingleScalarResult();

        $emailNotVerified = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.emailVerified = false')
            ->getQuery()
            ->getSingleScalarResult();

        $adminApproved = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.adminApproved = true')
            ->getQuery()
            ->getSingleScalarResult();

        $adminPending = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.adminApproved = false')
            ->getQuery()
            ->getSingleScalarResult();

        return [
            'total' => $total,
            'emailVerified' => $emailVerified,
            'emailNotVerified' => $emailNotVerified,
            'adminApproved' => $adminApproved,
            'adminPending' => $adminPending,
        ];
    }

    public function createFilteredQueryBuilder(array $filters): QueryBuilder
    {
        $qb = $this->createQueryBuilder('u');

        $status = $filters['status'] ?? '';
        if ($status === 'deleted') {
            $qb->andWhere('u.deletedAt IS NOT NULL');
        } else {
            $qb->andWhere('u.deletedAt IS NULL');
        }

        if (!empty($filters['role'])) {
            $qb->andWhere('u.role = :role')
                ->setParameter('role', $filters['role']);
        }

        if (!empty($filters['q'])) {
            $qb->andWhere('u.nom LIKE :q OR u.prenom LIKE :q OR u.email LIKE :q OR u.username LIKE :q')
                ->setParameter('q', '%' . $filters['q'] . '%');
        }

        if ($status === 'banned') {
            $qb->andWhere('u.isBanned = true')
                ->andWhere('u.banUntil IS NULL OR u.banUntil > :now')
                ->setParameter('now', new \DateTimeImmutable());
        } elseif ($status === 'active') {
            $qb->andWhere('u.isBanned = false');
        }

        if (!empty($filters['from'])) {
            try {
                $from = new \DateTimeImmutable($filters['from'] . ' 00:00:00');
                $qb->andWhere('u.createdAt >= :from')->setParameter('from', $from);
            } catch (\Throwable) {
            }
        }

        if (!empty($filters['to'])) {
            try {
                $to = new \DateTimeImmutable($filters['to'] . ' 23:59:59');
                $qb->andWhere('u.createdAt <= :to')->setParameter('to', $to);
            } catch (\Throwable) {
            }
        }

        return $qb->orderBy('u.createdAt', 'DESC');
    }

    public function getUserStats(): array
    {
        $total = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->getQuery()
            ->getSingleScalarResult();

        $active = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.isBanned = false')
            ->getQuery()
            ->getSingleScalarResult();

        $banned = (int) $this->createQueryBuilder('u')
            ->select('COUNT(u.id)')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.isBanned = true')
            ->andWhere('u.banUntil IS NULL OR u.banUntil > :now')
            ->setParameter('now', new \DateTimeImmutable())
            ->getQuery()
            ->getSingleScalarResult();

        $roles = $this->createQueryBuilder('u')
            ->select('u.role as role, COUNT(u.id) as count')
            ->andWhere('u.deletedAt IS NULL')
            ->groupBy('u.role')
            ->getQuery()
            ->getResult();

        return [
            'total' => $total,
            'active' => $active,
            'banned' => $banned,
            'roles' => $roles,
        ];
    }

    public function getRegistrationsChart(int $days = 30): array
    {
        $since = new \DateTimeImmutable('-' . $days . ' days');
        $rows = $this->createQueryBuilder('u')
            ->select('u.createdAt')
            ->andWhere('u.deletedAt IS NULL')
            ->andWhere('u.createdAt >= :since')
            ->setParameter('since', $since)
            ->orderBy('u.createdAt', 'ASC')
            ->getQuery()
            ->getResult();

        $labels = [];
        $data = [];
        $cursor = $since;
        for ($i = 0; $i <= $days; $i++) {
            $labels[] = $cursor->format('Y-m-d');
            $data[$cursor->format('Y-m-d')] = 0;
            $cursor = $cursor->modify('+1 day');
        }

        foreach ($rows as $row) {
            $key = $row['createdAt']->format('Y-m-d');
            if (isset($data[$key])) {
                $data[$key]++;
            }
        }

        return [
            'labels' => $labels,
            'data' => array_values($data),
        ];
    }
}
