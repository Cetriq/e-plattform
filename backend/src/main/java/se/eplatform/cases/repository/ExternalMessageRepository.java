package se.eplatform.cases.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import se.eplatform.cases.domain.ExternalMessage;
import se.eplatform.user.domain.User;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExternalMessageRepository extends JpaRepository<ExternalMessage, UUID> {

    @Query("SELECT m FROM ExternalMessage m JOIN FETCH m.createdBy " +
           "WHERE m.caseEntity.id = :caseId ORDER BY m.createdAt ASC")
    List<ExternalMessage> findByCaseId(@Param("caseId") UUID caseId);

    /**
     * Unread messages per case, either from staff (unread by the citizen) or
     * from the citizen (unread by staff). Rows are [caseId, count].
     */
    @Query("SELECT m.caseEntity.id, COUNT(m) FROM ExternalMessage m " +
           "WHERE m.caseEntity.id IN :caseIds AND m.fromManager = :fromManager " +
           "AND m.readAt IS NULL AND m.systemMessage = false GROUP BY m.caseEntity.id")
    List<Object[]> countUnread(@Param("caseIds") Collection<UUID> caseIds, @Param("fromManager") boolean fromManager);

    @Modifying
    @Query("UPDATE ExternalMessage m SET m.readAt = :now, m.readBy = :reader " +
           "WHERE m.caseEntity.id = :caseId AND m.fromManager = :fromManager AND m.readAt IS NULL")
    int markRead(@Param("caseId") UUID caseId, @Param("fromManager") boolean fromManager,
                 @Param("reader") User reader, @Param("now") Instant now);
}
