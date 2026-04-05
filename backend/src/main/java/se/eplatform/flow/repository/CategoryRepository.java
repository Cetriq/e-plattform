package se.eplatform.flow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.eplatform.flow.domain.Category;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByFlowTypeIdOrderBySortOrderAsc(UUID flowTypeId);
}
