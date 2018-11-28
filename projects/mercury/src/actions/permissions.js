import PermissionAPI from '../services/PermissionAPI/PermissionAPI'
import {createErrorHandlingPromiseAction} from "../utils/redux";
import {PERMISSIONS, ALTER_PERMISSION} from "./actionTypes";

export const fetchPermissions = createErrorHandlingPromiseAction((collectionId, useCache = true) =>  ({
        type: PERMISSIONS,
        payload: PermissionAPI.getCollectionPermissions(collectionId, useCache)
}));

export const alterPermission = createErrorHandlingPromiseAction((userId, collectionId, access) => ({
    type: ALTER_PERMISSION,
    payload: PermissionAPI.alterCollectionPermission(userId,  collectionId, access)
}));

