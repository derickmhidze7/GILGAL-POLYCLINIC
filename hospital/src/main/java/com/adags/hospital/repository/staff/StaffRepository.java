package com.adags.hospital.repository.staff;

import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Optional<Staff> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Staff> findByStaffRoleAndActiveTrue(Role role);

    @Query("SELECT s FROM Staff s WHERE s.active = true AND " +
           "(LOWER(s.firstName) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(s.lastName) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(s.specialization) LIKE LOWER(CONCAT('%',:query,'%')))")
    Page<Staff> searchStaff(String query, Pageable pageable);

    Page<Staff> findByActiveTrue(Pageable pageable);
}
