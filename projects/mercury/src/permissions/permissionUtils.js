import {comparing, compareBy} from "../common/utils/genericUtils";

export const AccessRights = ['Read', 'Write', 'Manage'];

const permissionLevel = p => AccessRights.indexOf(p.access);

export const sortPermissions = (permissions) => {
    if (!permissions) {
        return [];
    }

    return permissions.sort(comparing(compareBy(permissionLevel, false), compareBy('name')));
};

/**
 * Check if collaborator can alter permission. User can alter permission if:
 * - has manage access to a resource
 * - permission is not his/hers
 */
export const canAlterPermission = (userCanManage, permission, currentLoggedUser) => {
    const isSomeoneElsePermission = currentLoggedUser.iri !== permission.user;
    return userCanManage && isSomeoneElsePermission;
};