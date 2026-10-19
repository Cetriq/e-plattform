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
 * The one place that decides who may see and change a case. No endpoint
 * makes its own check beside it.
 *
 * Citizens reach the cases they created or own, whichever organisation
 * handles them. Staff reach a case only when all four hold:
 *
 *   1. organisation  it belongs to the organisation they are working for
 *   2. role          they are MANAGER or ADMIN there
 *   3. scope         the e-service's access scope lets them (whole organisation,
 *                    the case's handling group, or assigned to them)
 *   4. secrecy       its confidentiality is at most their clearance
 *
 * Belonging to the organisation is necessary but never enough on its own.
 * Conditions 1, 3 and 4 are one query condition (CaseRepository.STAFF_ACCESS)
 * used both here and in every staff list, so a list never shows a case a
 * direct read would refuse. Cases the user may not reach are reported as
 * "not found", so ids cannot be probed.
 */
@Service
public class CaseAccessPolicy {

    private final CaseRepository caseRepository;

    public CaseAccessPolicy(CaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    /**
     * What a staff member may reach, as parameters for the list queries.
     * @param clearance 0 NORMAL, 1 RESTRICTED, 2 CONFIDENTIAL
     */
    public record StaffScope(UUID organizationId, UUID staffId, int clearance) {}

    /** Staff role in the active organisation (condition 2). */
    public static boolean hasStaffRole(UserInfo user) {
        return user.activeOrganization() != null && CurrentUser.isStaff(user);
    }

    /** The staff scope of the logged-in user, or 403 for users without a staff role. */
    public StaffScope requireStaffScope() {
        UserInfo user = CurrentUser.require();
        if (!hasStaffRole(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kräver handläggarbehörighet");
        }
        return scopeOf(user);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID caseId, UserInfo user) {
        return caseRepository.isOwnedBy(caseId, UUID.fromString(user.id()));
    }

    /** All four staff conditions. */
    @Transactional(readOnly = true)
    public boolean staffMayReach(UUID caseId, UserInfo user) {
        if (!hasStaffRole(user)) {
            return false;
        }
        StaffScope scope = scopeOf(user);
        return caseRepository.isVisibleToStaff(caseId, scope.organizationId(), scope.staffId(), scope.clearance());
    }

    @Transactional(readOnly = true)
    public boolean canRead(UUID caseId, UserInfo user) {
        return isOwner(caseId, user) || staffMayReach(caseId, user);
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
     * Require that the logged-in user may handle the case as staff: assign it,
     * change its status, write notes and messages to the applicant.
     */
    @Transactional(readOnly = true)
    public UserInfo requireStaff(UUID caseId) {
        UserInfo user = CurrentUser.require();
        if (!hasStaffRole(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kräver handläggarbehörighet");
        }
        if (!staffMayReach(caseId, user)) {
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
            throw staffMayReach(caseId, user)
                ? new ResponseStatusException(HttpStatus.FORBIDDEN, "Endast ärendets ägare kan göra detta")
                : notFound();
        }
        return user;
    }

    private static StaffScope scopeOf(UserInfo user) {
        return new StaffScope(UUID.fromString(user.activeOrganization().id()), UUID.fromString(user.id()),
                clearanceRank(user.clearance()));
    }

    static int clearanceRank(String clearance) {
        if (clearance == null) return 0;
        return switch (clearance) {
            case "CONFIDENTIAL" -> 2;
            case "RESTRICTED" -> 1;
            default -> 0;
        };
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ärendet hittades inte");
    }
}
