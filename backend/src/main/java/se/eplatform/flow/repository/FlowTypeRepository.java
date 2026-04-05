package se.eplatform.flow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.eplatform.flow.domain.FlowType;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlowTypeRepository extends JpaRepository<FlowType, UUID> {

    List<FlowType> findAllByOrderBySortOrderAsc();
}
