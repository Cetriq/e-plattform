package se.eplatform.cases.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.cases.domain.*;
import se.eplatform.cases.repository.CaseRepository;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.QueryDefinition;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.flow.domain.Step;
import se.eplatform.flow.repository.FlowRepository;
import se.eplatform.user.domain.User;
import se.eplatform.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CaseService {

    private final CaseRepository caseRepository;
    private final FlowRepository flowRepository;
    private final UserRepository userRepository;

    public CaseService(CaseRepository caseRepository, FlowRepository flowRepository,
                       UserRepository userRepository) {
        this.caseRepository = caseRepository;
        this.flowRepository = flowRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get a case by ID with details.
     */
    @Transactional(readOnly = true)
    public Optional<Case> getCase(UUID id) {
        Optional<Case> caseOpt = caseRepository.findByIdWithDetails(id);
        caseOpt.ifPresent(c -> {
            // Initialize lazy collections
            org.hibernate.Hibernate.initialize(c.getQueryInstances());
            c.getQueryInstances().forEach(qi ->
                org.hibernate.Hibernate.initialize(qi.getQueryDefinition())
            );
        });
        return caseOpt;
    }

    /**
     * Get a case with all manager-related data loaded.
     */
    @Transactional(readOnly = true)
    public Optional<Case> getCaseForManager(UUID id) {
        Optional<Case> caseOpt = caseRepository.findByIdWithAllData(id);
        caseOpt.ifPresent(c -> {
            // Initialize lazy collections needed for manager view
            org.hibernate.Hibernate.initialize(c.getEvents());
            org.hibernate.Hibernate.initialize(c.getInternalMessages());
            org.hibernate.Hibernate.initialize(c.getExternalMessages());
            org.hibernate.Hibernate.initialize(c.getOwners());

            // Initialize createdBy for each event (needed for DTO mapping)
            c.getEvents().forEach(event -> {
                if (event.getCreatedBy() != null) {
                    org.hibernate.Hibernate.initialize(event.getCreatedBy());
                }
            });

            // Initialize createdBy for each message
            c.getInternalMessages().forEach(msg -> {
                org.hibernate.Hibernate.initialize(msg.getCreatedBy());
            });
            c.getExternalMessages().forEach(msg -> {
                org.hibernate.Hibernate.initialize(msg.getCreatedBy());
            });
        });
        return caseOpt;
    }

    /**
     * Get a case by reference number.
     */
    public Optional<Case> getCaseByReferenceNumber(String referenceNumber) {
        return caseRepository.findByReferenceNumber(referenceNumber);
    }

    /**
     * Get cases for a user (as owner).
     */
    public Page<Case> getCasesForUser(UUID userId, Pageable pageable) {
        return caseRepository.findByOwnerId(userId, pageable);
    }

    /**
     * Get draft cases for a user.
     */
    public List<Case> getDraftsForUser(UUID userId) {
        return caseRepository.findDraftsByUserId(userId);
    }

    /**
     * Get all submitted cases.
     */
    public Page<Case> getSubmittedCases(Pageable pageable) {
        return caseRepository.findAllSubmitted(pageable);
    }

    /**
     * Search cases.
     */
    public Page<Case> searchCases(String query, Pageable pageable) {
        return caseRepository.search(query, pageable);
    }

    /**
     * Create a new case.
     */
    @Transactional
    public Case createCase(UUID flowId, UUID userId) {
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Flow not found: " + flowId));

        if (!flow.isAccessible()) {
            throw new IllegalStateException("Flow is not accessible");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Case newCase = Case.create(flow, user);

        // Initialize query instances for all query definitions
        for (Step step : flow.getStepsSorted()) {
            for (QueryDefinition queryDef : step.getQueryDefinitions()) {
                QueryInstance instance = new QueryInstance(queryDef);
                newCase.addQueryInstance(instance);
            }
        }

        // Set initial status (draft)
        flow.getStatusDefinitionsSorted().stream()
                .filter(s -> s.getStatusType() == se.eplatform.flow.domain.StatusType.DRAFT)
                .findFirst()
                .ifPresent(newCase::setStatus);

        // Add created event
        newCase.addEvent(CaseEvent.created(newCase, user));

        return caseRepository.save(newCase);
    }

    /**
     * Update case values.
     */
    @Transactional
    public Case updateCaseValues(UUID caseId, Map<UUID, Object> values) {
        Case caseEntity = caseRepository.findByIdWithAllData(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (!caseEntity.isDraft()) {
            throw new IllegalStateException("Cannot update submitted case");
        }

        for (QueryInstance instance : caseEntity.getQueryInstances()) {
            UUID queryDefId = instance.getQueryDefinition().getId();
            if (values.containsKey(queryDefId)) {
                instance.setValue(values.get(queryDefId));
            }
        }

        return caseRepository.save(caseEntity);
    }

    /**
     * Submit a case.
     */
    @Transactional
    public Case submitCase(UUID caseId) {
        Case caseEntity = caseRepository.findByIdWithAllData(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (!caseEntity.isDraft()) {
            throw new IllegalStateException("Case already submitted");
        }

        // Validate all required fields
        for (QueryInstance instance : caseEntity.getQueryInstances()) {
            if (!instance.validate()) {
                throw new IllegalStateException("Validation failed for field: " +
                        instance.getQueryDefinition().getName());
            }
        }

        caseEntity.submit();

        // Change to submitted status
        caseEntity.getFlow().getStatusDefinitionsSorted().stream()
                .filter(s -> s.getStatusType() == se.eplatform.flow.domain.StatusType.SUBMITTED)
                .findFirst()
                .ifPresent(status -> caseEntity.changeStatus(status, caseEntity.getCreatedBy(), null));

        return caseRepository.save(caseEntity);
    }

    /**
     * Change case status.
     */
    @Transactional
    public Case changeStatus(UUID caseId, UUID statusId, UUID userId, String comment) {
        Case caseEntity = caseRepository.findByIdWithAllData(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        StatusDefinition newStatus = caseEntity.getFlow().getStatusDefinitionsSorted().stream()
                .filter(s -> s.getId().equals(statusId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Status not found: " + statusId));

        caseEntity.changeStatus(newStatus, user, comment);

        return caseRepository.save(caseEntity);
    }

    /**
     * Add an internal message.
     */
    @Transactional
    public InternalMessage addInternalMessage(UUID caseId, UUID userId, String message) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        InternalMessage msg = new InternalMessage(caseEntity, user, message);
        caseEntity.getInternalMessages().add(msg);
        caseEntity.addEvent(CaseEvent.messageSent(caseEntity, user, false));

        caseRepository.save(caseEntity);
        return msg;
    }

    /**
     * Add an external message.
     */
    @Transactional
    public ExternalMessage addExternalMessage(UUID caseId, UUID userId, String message, boolean fromManager) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        ExternalMessage msg = fromManager
                ? ExternalMessage.fromManager(caseEntity, user, message)
                : ExternalMessage.fromUser(caseEntity, user, message);

        caseEntity.getExternalMessages().add(msg);
        caseEntity.addEvent(CaseEvent.messageSent(caseEntity, user, true));

        caseRepository.save(caseEntity);
        return msg;
    }

    /**
     * Delete a draft case.
     */
    @Transactional
    public void deleteCase(UUID caseId) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (!caseEntity.isDraft()) {
            throw new IllegalStateException("Can only delete draft cases");
        }

        caseRepository.delete(caseEntity);
    }
}
