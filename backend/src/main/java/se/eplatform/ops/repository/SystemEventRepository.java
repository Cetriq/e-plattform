package se.eplatform.ops.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.ops.domain.SystemEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SystemEventRepository extends JpaRepository<SystemEvent, UUID> {

    @Query("SELECT e FROM SystemEvent e WHERE (:level IS NULL OR e.level = :level) " +
           "AND (:source = '' OR e.source LIKE CONCAT(:source, '%')) ORDER BY e.timestamp DESC")
    Page<SystemEvent> search(@Param("level") SystemEvent.Level level, @Param("source") String source, Pageable pageable);

    /** Latest event per source with the given prefix, e.g. "job:" for the last run of each job. */
    @Query(value = "SELECT DISTINCT ON (source) * FROM system_events WHERE source LIKE CONCAT(:prefix, '%') " +
                   "ORDER BY source, timestamp DESC", nativeQuery = true)
    List<SystemEvent> latestPerSource(@Param("prefix") String prefix);

    long countByLevelAndTimestampAfter(SystemEvent.Level level, Instant after);

    @Modifying
    @Query("DELETE FROM SystemEvent e WHERE e.timestamp < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
