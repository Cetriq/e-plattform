package se.eplatform.cases.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.cases.domain.*;
import se.eplatform.cases.repository.CaseRepository;
import se.eplatform.cases.repository.ExternalMessageRepository;
import se.eplatform.flow.service.StatusTransitionService;
import se.eplatform.flow.domain.Flow;
import se.eplatform.flow.domain.QueryDefinition;
import se.eplatform.flow.domain.StatusDefinition;
import se.eplatform.flow.domain.StatusType;
import se.eplatform.flow.domain.Step;
import se.eplatform.flow.repository.FlowRepository;
import se.eplatform.notification.service.NotificationService;
import se.eplatform.user.domain.User;
import se.eplatform.user.repository.UserRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
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
    private final NotificationService notificationService;
    private final StatusTransitionService statusTransitions;
    private final ExternalMessageRepository externalMessageRepository;

    /** Roles that handle cases and can be assigned to them. */
    public static final List<String> STAFF_ROLES = List.of("MANAGER", "ADMIN");

    public CaseService(CaseRepository caseRepository, FlowRepository flowRepository,
                       UserRepository userRepository, NotificationService notificationService,
                       StatusTransitionService statusTransitions,
                       ExternalMessageRepository externalMessageRepository) {
        this.caseRepository = caseRepository;
        this.flowRepository = flowRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.statusTransitions = statusTransitions;
        this.externalMessageRepository = externalMessageRepository;
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
            org.hibernate.Hibernate.initialize(c.getAssignedTo());

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

            // Initialize flow structure for PDF generation
            if (c.getFlow() != null) {
                Flow flow = c.getFlow();
                org.hibernate.Hibernate.initialize(flow.getSteps());
                flow.getSteps().forEach(step -> {
                    org.hibernate.Hibernate.initialize(step.getQueryDefinitions());
                });
            }
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
                .filter(s -> s.getStatusType() == StatusType.DRAFT)
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
                .filter(s -> s.getStatusType() == StatusType.SUBMITTED)
                .findFirst()
                .ifPresent(status -> caseEntity.changeStatus(status, caseEntity.getCreatedBy(), null));

        Case savedCase = caseRepository.save(caseEntity);

        // Send notification to the user who submitted the case
        notificationService.notifyCaseSubmitted(savedCase, savedCase.getCreatedBy());

        return savedCase;
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

        if (caseEntity.isDraft()) {
            throw new IllegalStateException("Ärendet är inte inskickat än");
        }

        StatusDefinition oldStatus = caseEntity.getStatus();
        List<StatusDefinition> flowStatuses = caseEntity.getFlow().getStatusDefinitionsSorted();

        StatusDefinition newStatus = flowStatuses.stream()
                .filter(s -> s.getId().equals(statusId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Status not found: " + statusId));

        StatusTransitionService.Transition transition = statusTransitions.find(oldStatus, newStatus, flowStatuses)
                .orElseThrow(() -> new IllegalStateException(
                        "Ärendet kan inte gå från " + (oldStatus != null ? oldStatus.getName() : "–")
                                + " till " + newStatus.getName()));
        if (transition.requiresComment() && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("En kommentar krävs för att ändra status till " + newStatus.getName());
        }

        caseEntity.changeStatus(newStatus, user, comment);

        Case savedCase = caseRepository.save(caseEntity);

        // Notify case owner about status change
        User caseOwner = savedCase.getCreatedBy();
        if (newStatus.getStatusType() == StatusType.COMPLETED) {
            notificationService.notifyCaseCompleted(savedCase, caseOwner);
        } else {
            notificationService.notifyCaseStatusChanged(savedCase, caseOwner, oldStatus, newStatus);
        }

        return savedCase;
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
        Case caseEntity = caseRepository.findByIdWithAllData(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        ExternalMessage msg = fromManager
                ? ExternalMessage.fromManager(caseEntity, user, message)
                : ExternalMessage.fromUser(caseEntity, user, message);

        caseEntity.getExternalMessages().add(msg);
        caseEntity.addEvent(CaseEvent.messageSent(caseEntity, user, true));

        caseRepository.save(caseEntity);

        // Send notifications
        if (fromManager) {
            // Notify case owner about new message from manager
            User caseOwner = caseEntity.getCreatedBy();
            notificationService.notifyNewMessageFromManager(caseEntity, caseOwner, msg);
        } else if (caseEntity.getAssignedTo() != null) {
            // Notify the handläggare responsible for the case
            notificationService.notifyManagersNewMessage(caseEntity, caseEntity.getAssignedTo(), msg);
        }

        return msg;
    }

    /**
     * Statuses the case may move to next, for the manager's status menu.
     */
    @Transactional(readOnly = true)
    public List<StatusTransitionService.Transition> allowedTransitions(Case caseEntity) {
        if (caseEntity.isDraft()) {
            return List.of();
        }
        return statusTransitions.allowedFrom(caseEntity.getStatus(), caseEntity.getFlow().getStatusDefinitionsSorted());
    }

    /**
     * Messages between the citizen and staff, oldest first.
     */
    @Transactional(readOnly = true)
    public List<ExternalMessage> getExternalMessages(UUID caseId) {
        return externalMessageRepository.findByCaseId(caseId);
    }

    /**
     * Send a message from the citizen to the handläggare.
     */
    @Transactional
    public ExternalMessage addCitizenMessage(UUID caseId, UUID userId, String message) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        if (caseEntity.isDraft()) {
            throw new IllegalStateException("Skicka in ärendet innan du skickar meddelanden");
        }
        if (caseEntity.getStatus() != null && !caseEntity.getStatus().isUserCanMessage()) {
            throw new IllegalStateException("Det går inte att skicka meddelanden i ärendets nuvarande status");
        }
        return addExternalMessage(caseId, userId, message, false);
    }

    /**
     * Mark the messages from the other party as read by this user.
     */
    @Transactional
    public void markMessagesRead(UUID caseId, UUID readerId, boolean readerIsStaff) {
        User reader = userRepository.findById(readerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + readerId));
        // Staff read the citizen's messages; the citizen reads the staff's
        externalMessageRepository.markRead(caseId, !readerIsStaff, reader, Instant.now());
    }

    /**
     * Number of unread messages per case. fromStaff=true counts messages the
     * citizen hasn't read; false counts messages staff haven't read.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> unreadMessageCounts(Collection<UUID> caseIds, boolean fromStaff) {
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : externalMessageRepository.countUnread(caseIds, fromStaff)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * Assign the case to a handläggare, or clear the assignment with null.
     */
    @Transactional
    public Case assign(UUID caseId, UUID assigneeId, UUID assignedById) {
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        User assignedBy = userRepository.findById(assignedById)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + assignedById));

        User assignee = null;
        if (assigneeId != null) {
            assignee = userRepository.findById(assigneeId)
                    .filter(User::isActive)
                    .filter(u -> STAFF_ROLES.stream().anyMatch(u::hasRole))
                    .orElseThrow(() -> new IllegalArgumentException("Användaren kan inte handlägga ärenden"));
        }

        User previous = caseEntity.getAssignedTo();
        boolean unchanged = previous == null ? assignee == null
                : assignee != null && previous.getId().equals(assignee.getId());
        if (!unchanged) {
            caseEntity.setAssignedTo(assignee);
            caseEntity.addEvent(CaseEvent.assigned(caseEntity, assignee, assignedBy));
        }
        Case saved = caseRepository.save(caseEntity);
        // The controller maps the result to a summary after the transaction
        org.hibernate.Hibernate.initialize(saved.getFlow());
        org.hibernate.Hibernate.initialize(saved.getFlow().getSteps());
        org.hibernate.Hibernate.initialize(saved.getStatus());
        org.hibernate.Hibernate.initialize(saved.getAssignedTo());
        return saved;
    }

    /**
     * Users who can be assigned to cases.
     */
    @Transactional(readOnly = true)
    public List<User> getAssignableUsers() {
        return userRepository.findActiveWithAnyRole(STAFF_ROLES);
    }

    /**
     * Submitted cases for the manager list. mine/unassigned narrow the list.
     */
    @Transactional(readOnly = true)
    public Page<Case> getSubmittedCases(UUID assignedTo, boolean unassignedOnly, Pageable pageable) {
        return caseRepository.findSubmitted(assignedTo, unassignedOnly, pageable);
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
