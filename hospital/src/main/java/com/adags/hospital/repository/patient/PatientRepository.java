package com.adags.hospital.repository.patient;

import com.adags.hospital.domain.patient.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByNationalId(String nationalId);

    boolean existsByNationalId(String nationalId);

    @Query("SELECT COUNT(p) > 0 FROM Patient p WHERE " +
           "LOWER(p.firstName) = LOWER(:firstName) AND " +
           "LOWER(p.lastName) = LOWER(:lastName) AND " +
           "((:middleName IS NULL AND p.middleName IS NULL) OR LOWER(p.middleName) = LOWER(:middleName))")
    boolean existsByFullName(@Param("firstName") String firstName,
                             @Param("middleName") String middleName,
                             @Param("lastName") String lastName);

    @Query("SELECT p FROM Patient p WHERE " +
           "LOWER(p.firstName) = LOWER(:firstName) AND " +
           "LOWER(p.lastName) = LOWER(:lastName) AND " +
           "((:middleName IS NULL AND p.middleName IS NULL) OR LOWER(p.middleName) = LOWER(:middleName))")
    Optional<Patient> findByFullName(@Param("firstName") String firstName,
                                     @Param("middleName") String middleName,
                                     @Param("lastName") String lastName);

    @Query("SELECT p FROM Patient p WHERE p.active = true AND (" +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(p.nationalId) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(p.phone) LIKE LOWER(CONCAT('%',:query,'%')))") 
    Page<Patient> searchPatients(String query, Pageable pageable);

    Page<Patient> findByActiveTrue(Pageable pageable);

    List<Patient> findByActiveTrue();

    List<Patient> findByActiveTrueAndIdNotIn(Collection<UUID> excludedIds);

    @Query("SELECT p FROM Patient p WHERE (" +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(p.lastName)  LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(p.nationalId) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(p.phone)     LIKE LOWER(CONCAT('%',:query,'%')))")
    Page<Patient> searchAllPatients(String query, Pageable pageable);
}
