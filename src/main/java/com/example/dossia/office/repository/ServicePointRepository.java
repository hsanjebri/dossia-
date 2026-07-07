package com.example.dossia.office.repository;

import com.example.dossia.office.domain.OfficeType;
import com.example.dossia.office.domain.ServicePoint;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicePointRepository extends JpaRepository<ServicePoint, UUID> {

    List<ServicePoint> findByOfficeTypeIn(Collection<OfficeType> officeTypes);

    @Query("SELECT s FROM ServicePoint s WHERE s.category = :category OR s.officeType IN :types")
    List<ServicePoint> findByCategoryOrTypes(
            @Param("category") String category, @Param("types") Collection<OfficeType> types);
}
