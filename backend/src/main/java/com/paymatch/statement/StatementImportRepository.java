package com.paymatch.statement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {

    @Override
    @EntityGraph(attributePaths = {"importedBy", "undoneBy"})
    Page<StatementImport> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"importedBy", "undoneBy"})
    Optional<StatementImport> findWithUsersById(Long id);

    Optional<StatementImport> findFirstByFileSha256AndStatusOrderByImportedAtDesc(String fileSha256, ImportStatus status);
}
