package com.adags.hospital.repository.ward;

import com.adags.hospital.domain.ward.WardPatientAssignment;
import com.adags.hospital.domain.ward.WardPatientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WardPatientAssignmentRepository extends JpaRepository<WardPatientAssignment, UUID> {

    List<WardPatientAssignment> findByStatusIn(List<WardPatientStatus> statuses);

    Page<WardPatientAssignment> findByPatientId(UUID patientId, Pageable pageable);

    Optional<WardPatientAssignment> findFirstByPatientIdAndStatusNotOrderByAdmitDateDesc(
            UUID patientId, WardPatientStatus status);

    List<WardPatientAssignment> findByAssignedNurseIdAndStatusIn(
            UUID nurseId, List<WardPatientStatus> statuses);

    List<WardPatientAssignment> findByAssignedByDoctorIdAndStatusNotInOrderByAdmitDateDesc(
            UUID doctorId, List<WardPatientStatus> excludedStatuses);

    /**
     * Returns the patient IDs whose most-recent ward assignment has one of the given statuses.
     * Used by the receptionist to hide currently-discharged patients from the patient list.
     */
    @Query("""
            SELECT a.patient.id FROM WardPatientAssignment a
            WHERE a.status IN :statuses
              AND a.admitDate = (
                  SELECT MAX(a2.admitDate) FROM WardPatientAssignment a2
                  WHERE a2.patient.id = a.patient.id
              )
            """)
    List<UUID> findPatientIdsWithLatestStatusIn(@Param("statuses") List<WardPatientStatus> statuses);

    @Query("SELECT w FROM WardPatientAssignment w " +
           "LEFT JOIN FETCH w.patient p " +
           "LEFT JOIN FETCH w.assignedNurse " +
           "LEFT JOIN FETCH w.assignedByDoctor " +
           "WHERE w.status NOT IN :excludedStatuses " +
           "ORDER BY w.admitDate ASC")
    List<WardPatientAssignment> findActiveAssignments(
            @Param("excludedStatuses") List<WardPatientStatus> excludedStatuses);

    @Query("SELECT w FROM WardPatientAssignment w " +
           "LEFT JOIN FETCH w.patient " +
           "LEFT JOIN FETCH w.assignedNurse " +
           "LEFT JOIN FETCH w.assignedByDoctor " +
           "WHERE w.id = :id")
    Optional<WardPatientAssignment> findByIdWithDetails(@Param("id") UUID id);

    /** Check whether a patient currently has an active ward admission. */
    boolean existsByPatientIdAndStatusIn(UUID patientId, List<WardPatientStatus> statuses);

    /** Get the most-recent active ward assignment for a patient (used for discharge from surgery page). */
    Optional<WardPatientAssignment> findFirstByPatientIdAndStatusInOrderByAdmitDateDesc(
            UUID patientId, List<WardPatientStatus> statuses);

    /** All assignments (any status) where this nurse was the assigned nurse — for history page. */
    @Query("SELECT w FROM WardPatientAssignment w " +
           "LEFT JOIN FETCH w.patient " +
           "LEFT JOIN FETCH w.assignedNurse " +
           "LEFT JOIN FETCH w.assignedByDoctor " +
           "WHERE w.assignedNurse.id = :nurseId " +
           "ORDER BY w.admitDate DESC")
    List<WardPatientAssignment> findByNurseIdAllHistory(@Param("nurseId") UUID nurseId);
}
