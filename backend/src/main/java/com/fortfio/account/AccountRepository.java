package com.fortfio.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

	Optional<Account> findByEmail(String email);

	@Modifying
	@Query("update Account a set a.credits = a.credits - :amount where a.id = :id and a.credits >= :amount")
	int tryCharge(@Param("id") Long id, @Param("amount") int amount);

	@Modifying
	@Query("update Account a set a.credits = a.credits + :amount where a.id = :id")
	int addCredits(@Param("id") Long id, @Param("amount") int amount);
}
