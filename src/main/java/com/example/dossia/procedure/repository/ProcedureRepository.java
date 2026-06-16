package com.example.dossia.procedure.repository;

import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureCategory;
import com.example.dossia.procedure.domain.ProcedureStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedureRepository extends JpaRepository<Procedure, UUID> {

    Optional<Procedure> findBySlug(String slug);

    Optional<Procedure> findBySlugAndStatus(String slug, ProcedureStatus status);

    boolean existsBySlug(String slug);

    @Query("""
            SELECT p FROM Procedure p
            WHERE p.slug = :slug AND p.status = :status
            """)
    Optional<Procedure> findPublishedDetailBySlug(
            @Param("slug") String slug, @Param("status") ProcedureStatus status);

    @Query("""
            SELECT p FROM Procedure p
            WHERE p.status = :status
              AND (:category IS NULL OR p.category = :category)
              AND (
                    :query IS NULL OR :query = ''
                    OR LOWER(p.titleFr) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.titleAr) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.titleTn) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.ministry) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            """)
    Page<Procedure> searchPublished(
            @Param("status") ProcedureStatus status,
            @Param("category") ProcedureCategory category,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            SELECT p FROM Procedure p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:category IS NULL OR p.category = :category)
              AND (
                    :query IS NULL OR :query = ''
                    OR LOWER(p.titleFr) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.titleAr) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.titleTn) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.ministry) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            """)
    Page<Procedure> searchAll(
            @Param("status") ProcedureStatus status,
            @Param("category") ProcedureCategory category,
            @Param("query") String query,
            Pageable pageable);
}
