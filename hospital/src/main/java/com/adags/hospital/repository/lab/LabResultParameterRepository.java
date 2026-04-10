package com.adags.hospital.repository.lab;

import com.adags.hospital.domain.lab.LabResultParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabResultParameterRepository extends JpaRepository<LabResultParameter, UUID> {

    List<LabResultParameter> findByLabResultId(UUID labResultId);

    void deleteByLabResultId(UUID labResultId);
}
