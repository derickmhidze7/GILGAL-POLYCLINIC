package com.adags.hospital.repository.surgery;

import com.adags.hospital.domain.surgery.SurgeryPostopCare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SurgeryPostopCareRepository extends JpaRepository<SurgeryPostopCare, UUID> {

    List<SurgeryPostopCare> findBySurgeryOrderIdOrderByRecordedAtDesc(UUID surgeryOrderId);

    @Query("SELECT c FROM SurgeryPostopCare c LEFT JOIN FETCH c.nurse " +
           "WHERE c.surgeryOrder.id = :surgeryOrderId ORDER BY c.recordedAt DESC")
    List<SurgeryPostopCare> findWithNurseBySurgeryOrderId(UUID surgeryOrderId);
}
