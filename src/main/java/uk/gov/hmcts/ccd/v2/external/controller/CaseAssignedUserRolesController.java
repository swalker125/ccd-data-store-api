package uk.gov.hmcts.ccd.v2.external.controller;

import com.google.common.collect.Lists;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.ccd.auditlog.LogAudit;
import uk.gov.hmcts.ccd.domain.model.std.CaseAssignedUserRole;
import uk.gov.hmcts.ccd.domain.service.cauroles.CaseAssignedUserRolesOperation;
import uk.gov.hmcts.ccd.domain.service.common.UIDService;
import uk.gov.hmcts.ccd.endpoint.exceptions.BadRequestException;
import uk.gov.hmcts.ccd.v2.V2;
import uk.gov.hmcts.ccd.v2.external.domain.AddCaseAssignedUserRolesResponse;
import uk.gov.hmcts.ccd.v2.external.resource.CaseAssignedUserRolesResource;

import static uk.gov.hmcts.ccd.auditlog.AuditOperationType.ADD_CASE_ASSIGNED_USER_ROLES;
import static uk.gov.hmcts.ccd.auditlog.aop.AuditContext.CASE_ID_SEPARATOR;
import static uk.gov.hmcts.ccd.auditlog.aop.AuditContext.MAX_CASE_IDS_LIST;

@RestController
@RequestMapping(path = "/")
public class CaseAssignedUserRolesController {

    public static final String ADD_SUCCESS_MESSAGE = "Case-User-Role assignments created successfully";

    private final UIDService caseReferenceService;
    private final CaseAssignedUserRolesOperation caseAssignedUserRolesOperation;

    @Autowired
    public CaseAssignedUserRolesController(UIDService caseReferenceService,
                                           @Qualifier("authorised") CaseAssignedUserRolesOperation caseAssignedUserRolesOperation) {
        this.caseReferenceService = caseReferenceService;
        this.caseAssignedUserRolesOperation = caseAssignedUserRolesOperation;
    }

    @PostMapping(
        path = "/case-users"
    )
    @ApiOperation(
        value = "Add Case-Assigned Users and Roles"
    )
    @ApiResponses({
        @ApiResponse(
            code = 200,
            message = ADD_SUCCESS_MESSAGE,
            response = AddCaseAssignedUserRolesResponse.class
        ),
        @ApiResponse(
            code = 400,
            message = "One or more of the following reasons:\n"
                + "1. " + V2.Error.EMPTY_CASE_USER_ROLE_LIST + "\n"
                + "2. " + V2.Error.CASE_ID_INVALID + "\n"
                + "3. " + V2.Error.USER_ID_INVALID + "\n"
                + "4. " + V2.Error.CASE_ROLE_FORMAT_INVALID + "."
        ),
        @ApiResponse(
            code = 403,
            message = V2.Error.CLIENT_SERVICE_NOT_AUTHORISED_FOR_OPERATION
        )
    })
    @LogAudit(
        operationType = ADD_CASE_ASSIGNED_USER_ROLES,
        caseId = "T(uk.gov.hmcts.ccd.v2.external.controller.CaseAssignedUserRolesController).buildCaseIds(#caseAssignedUserRoles)",
        targetIdamId = "T(uk.gov.hmcts.ccd.v2.external.controller.CaseAssignedUserRolesController).buildUserIds(#caseAssignedUserRoles)",
        targetCaseRoles = "T(uk.gov.hmcts.ccd.v2.external.controller.CaseAssignedUserRolesController).buildCaseRoles(#caseAssignedUserRoles)"
    )
    public ResponseEntity<AddCaseAssignedUserRolesResponse> addCaseUserRoles(@RequestBody CaseAssignedUserRolesResource caseAssignedUserRoles) {
        validateRequestParams(caseAssignedUserRoles);
        this.caseAssignedUserRolesOperation.addCaseUserRoles(caseAssignedUserRoles.getCaseAssignedUserRoles());
        return ResponseEntity.ok(new AddCaseAssignedUserRolesResponse(ADD_SUCCESS_MESSAGE));
    }

    @GetMapping(
        path = "/case-users"
    )
    @ApiOperation(
        value = "Get Case-Assigned Users and Roles"
    )
    @ApiResponses({
        @ApiResponse(
            code = 200,
            message = "Case-User-Role assignments returned successfully",
            response = CaseAssignedUserRolesResource.class
        ),
        @ApiResponse(
            code = 400,
            message = V2.Error.CASE_ID_INVALID
        ),
        @ApiResponse(
            code = 400,
            message = V2.Error.EMPTY_CASE_ID_LIST
        ),
        @ApiResponse(
            code = 400,
            message = V2.Error.USER_ID_INVALID
        ),
        @ApiResponse(
            code = 403,
            message = V2.Error.OTHER_USER_CASE_ROLE_ACCESS_NOT_GRANTED
        )
    })
    public ResponseEntity<CaseAssignedUserRolesResource> getCaseUserRoles(@RequestParam("case_ids") List<String> caseIds,
                                                                          @RequestParam(value = "user_ids", required = false)
                                                                              Optional<List<String>> optionalUserIds) {
        List<String> userIds = optionalUserIds.orElseGet(Lists::newArrayList);
        validateRequestParams(caseIds, userIds);
        List<CaseAssignedUserRole> caseAssignedUserRoles = this.caseAssignedUserRolesOperation.findCaseUserRoles(caseIds
            .stream()
            .map(Long::valueOf)
            .collect(Collectors.toCollection(ArrayList::new)), userIds);
        return ResponseEntity.ok(new CaseAssignedUserRolesResource(caseAssignedUserRoles));
    }

    private void validateRequestParams(List<String> caseIds, List<String> userIds) {
        List<String> errorMessages = Lists.newArrayList("Invalid data provided for the following inputs to the request:");
        if (caseIds == null || caseIds.isEmpty()) {
            errorMessages.add(V2.Error.EMPTY_CASE_ID_LIST);
        } else {
            caseIds.forEach(caseId -> {
                if (!caseReferenceService.validateUID(caseId)) {
                    errorMessages.add(V2.Error.CASE_ID_INVALID);
                }
            });
        }

        userIds.forEach(userId -> {
            if (StringUtils.isAllBlank(userId)) {
                errorMessages.add(V2.Error.USER_ID_INVALID);
            }
        });

        if (errorMessages.size() > 1) {
            String message = String.join("\n", errorMessages);
            throw new BadRequestException(message);
        }
    }

    private void validateRequestParams(CaseAssignedUserRolesResource caseAssignedUserRoles) {
        final Pattern caseRolePattern = Pattern.compile("^\\[.+]$");

        List<String> errorMessages = Lists.newArrayList("Invalid data provided for the following inputs to the request:");

        List<CaseAssignedUserRole> caseUserRoles =
            caseAssignedUserRolesToStream(caseAssignedUserRoles).collect(Collectors.toList());

        /// case-users: must be none empty
        if (caseUserRoles.isEmpty()) {
            errorMessages.add(V2.Error.EMPTY_CASE_USER_ROLE_LIST);
        } else {
            caseUserRoles.forEach(caseRole -> {
                // case_id: has to be a valid 16-digit Luhn number)
                if (!caseReferenceService.validateUID(caseRole.getCaseDataId())) {
                    errorMessages.add(V2.Error.CASE_ID_INVALID);
                }
                // user_id: has to be a string of length > 0
                if (StringUtils.isAllBlank(caseRole.getUserId())) {
                    errorMessages.add(V2.Error.USER_ID_INVALID);
                }
                // case_role: has to be a none-empty string in square brackets
                if (caseRole.getCaseRole() == null || !caseRolePattern.matcher(caseRole.getCaseRole()).matches()) {
                    errorMessages.add(V2.Error.CASE_ROLE_FORMAT_INVALID);
                }
            });
        }

        if (errorMessages.size() > 1) {
            String message = String.join("\n", errorMessages);
            throw new BadRequestException(message);
        }
    }

    public static String buildCaseIds(CaseAssignedUserRolesResource caseAssignedUserRoles) {
        return caseAssignedUserRolesToStream(caseAssignedUserRoles).limit(MAX_CASE_IDS_LIST)
            .map(CaseAssignedUserRole::getCaseDataId)
            .collect(Collectors.joining(CASE_ID_SEPARATOR));
    }

    public static String buildCaseRoles(CaseAssignedUserRolesResource caseAssignedUserRoles) {
        // NB: match Case ID list size and separator configuration
        return caseAssignedUserRolesToStream(caseAssignedUserRoles).limit(MAX_CASE_IDS_LIST)
            .map(CaseAssignedUserRole::getCaseRole)
            .collect(Collectors.joining(CASE_ID_SEPARATOR));
    }

    public static String buildUserIds(CaseAssignedUserRolesResource caseAssignedUserRoles) {
        // NB: match Case ID list size and separator configuration
        return caseAssignedUserRolesToStream(caseAssignedUserRoles).limit(MAX_CASE_IDS_LIST)
            .map(CaseAssignedUserRole::getUserId)
            .collect(Collectors.joining(CASE_ID_SEPARATOR));
    }

    private static Stream<CaseAssignedUserRole> caseAssignedUserRolesToStream(CaseAssignedUserRolesResource caseAssignedUserRoles) {
        return caseAssignedUserRoles != null
            ? Optional.ofNullable(caseAssignedUserRoles.getCaseAssignedUserRoles())
                .map(Collection::stream)
                .orElseGet(Stream::empty)
            : Stream.empty();
    }

}
