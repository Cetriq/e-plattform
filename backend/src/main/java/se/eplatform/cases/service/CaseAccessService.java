package se.eplatform.cases.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.eplatform.auth.dto.AuthResponse.UserInfo;
import se.eplatform.cases.repository.CaseRepository;
import se.eplatform.common.security.CurrentUser;

import java.util.UUID;

/**
 * Decides who may see and change a case.
 *
 * - Staff (MANAGER, ADMIN) can read every case.
 * - Citizens can read and change only cases they created or own.
 *
 * Cases the user may not see are reported as "not found", so ids of other
 * people's cases cannot be probed.
 */
@Service
public class CaseAccessService {

    private final CaseRepository caseRepository;

    public CaseAccessService(CaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID caseId, UserInfo user) {
        return caseRepository.isOwnedBy(caseId, UUID.fromString(user.id()));
    }

    @Transactional(readOnly = true)
    public boolean canRead(UUID caseId, UserInfo user) {
        return CurrentUser.isStaff(user) || isOwner(caseId, user);
    }

    /**
     * Require that the logged-in user may read the case.
     */
    @Transactional(readOnly = true)
    public UserInfo requireRead(UUID caseId) {
        UserInfo user = CurrentUser.require();
        if (!canRead(caseId, user)) {
            throw notFound();
        }
        return user;
    }

    /**
     * Require that the logged-in user owns the case. Used for changes a citizen
     * makes to their own application, which staff should not make on their behalf.
     */
    @Transactional(readOnly = true)
    public UserInfo requireOwner(UUID caseId) {
        UserInfo user = CurrentUser.require();
        if (!isOwner(caseId, user)) {
            throw CurrentUser.isStaff(user)
                ? new ResponseStatusException(HttpStatus.FORBIDDEN, "Endast ärendets ägare kan göra detta")
                : notFound();
        }
        return user;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ärendet hittades inte");
    }
}
