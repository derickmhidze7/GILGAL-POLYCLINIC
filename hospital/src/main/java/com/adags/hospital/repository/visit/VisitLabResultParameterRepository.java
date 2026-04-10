package com.adags.hospital.repository.visit;

import com.adags.hospital.domain.visit.VisitLabResultParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VisitLabResultParameterRepository extends JpaRepository<VisitLabResultParameter, UUID> {
}
