package se.eplatform.flow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.eplatform.flow.domain.Step;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepRepository extends JpaRepository<Step, UUID> {

    /**
     * Find steps by flow ID ordered by sort order.
     */
    List<Step> findByFlowIdOrderBySortOrderAsc(UUID flowId);

    /**
     * Count steps in a flow.
     */
    long countByFlowId(UUID flowId);

    /**
     * Delete all steps for a flow.
     */
    void deleteByFlowId(UUID flowId);
}
