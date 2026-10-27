package com.paymatch.counterparty;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    Optional<Counterparty> findByNip(String nip);

    Optional<Counterparty> findFirstByNipIsNullAndNameIgnoreCase(String name);

    boolean existsByNipAndIdNot(String nip, Long id);

    boolean existsByNip(String nip);

    Page<Counterparty> findByNameContainingIgnoreCaseOrNipContaining(String name, String nip, Pageable pageable);
}
