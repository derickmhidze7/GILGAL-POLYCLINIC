package com.adags.hospital.repository.staff;

import com.adags.hospital.domain.staff.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByName(String name);

    boolean existsByName(String name);
}
