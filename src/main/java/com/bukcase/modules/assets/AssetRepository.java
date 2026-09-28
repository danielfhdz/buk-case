package com.bukcase.modules.assets;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    @Override
    @EntityGraph(attributePaths = {"category", "employee", "employee.position"})
    List<Asset> findAll(Specification<Asset> spec);

    @Override
    @EntityGraph(attributePaths = {"category", "employee", "employee.position"})
    Page<Asset> findAll(Specification<Asset> spec, Pageable pageable);
}
