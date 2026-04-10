package com.adags.hospital.repository.pricing;

import com.adags.hospital.domain.pricing.PriceChangeProposal;
import com.adags.hospital.domain.pricing.PriceProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PriceChangeProposalRepository extends JpaRepository<PriceChangeProposal, UUID> {

    List<PriceChangeProposal> findByStatusOrderByCreatedAtDesc(PriceProposalStatus status);

    List<PriceChangeProposal> findByProposedByIdOrderByCreatedAtDesc(UUID proposedById);

    List<PriceChangeProposal> findByStatusAndProposedByIdOrderByCreatedAtDesc(
            PriceProposalStatus status, UUID proposedById);

    long countByStatus(PriceProposalStatus status);

    @Query("SELECT p FROM PriceChangeProposal p " +
           "LEFT JOIN FETCH p.proposedBy " +
           "LEFT JOIN FETCH p.priceItem " +
           "WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<PriceChangeProposal> findByStatusWithDetails(@Param("status") PriceProposalStatus status);

    @Query("SELECT p FROM PriceChangeProposal p " +
           "LEFT JOIN FETCH p.proposedBy " +
           "LEFT JOIN FETCH p.reviewedBy " +
           "LEFT JOIN FETCH p.priceItem " +
           "ORDER BY p.createdAt DESC")
    List<PriceChangeProposal> findAllWithDetails();

    java.util.Optional<PriceChangeProposal> findTopByProductCodeStartingWithOrderByProductCodeDesc(String prefix);

    java.util.Optional<PriceChangeProposal> findTopByItemIdNotNullOrderByItemIdDesc();
}
