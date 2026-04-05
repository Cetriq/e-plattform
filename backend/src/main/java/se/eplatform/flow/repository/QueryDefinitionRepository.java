package se.eplatform.flow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.eplatform.flow.domain.QueryDefinition;

import java.util.List;
import java.util.UUID;

@Repository
public interface QueryDefinitionRepository extends JpaRepository<QueryDefinition, UUID> {

    /**
     * Find query definitions by step ID ordered by sort order.
     */
    List<QueryDefinition> findByStepIdOrderBySortOrderAsc(UUID stepId);

    /**
     * Count query definitions in a step.
     */
    long countByStepId(UUID stepId);

    /**
     * Delete all query definitions for a step.
     */
    void deleteByStepId(UUID stepId);
}
