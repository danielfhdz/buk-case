package com.bukcase.modules.vacations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VacationRequestRepository
        extends JpaRepository<VacationRequest, Long>, JpaSpecificationExecutor<VacationRequest> {
}
