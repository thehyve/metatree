const defaultState = {};

const jsonLdBySubject = (state = defaultState, action) => {
    switch (action.type) {
        case "METADATA_PENDING":
            return {
                ...state,
                [action.meta.subject]: {
                    pending: true,
                    error: false,
                    invalidated: false,
                    items: {}
                }
            }
        case "METADATA_FULFILLED":
            return {
                ...state,
                [action.meta.subject]: {
                    ...state[action.meta.subject],
                    pending: false,
                    items: action.payload
                }
            }
        case "METADATA_REJECTED":
            return {
                ...state,
                [action.meta.subject]: {
                    ...state[action.meta.subject],
                    pending: false,
                    error: action.payload || true
                }
            }
        case "UPDATE_METADATA_FULFILLED":
        case "INVALIDATE_METADATA":
            return {
                ...state,
                [action.meta.subject]: {
                    ...state[action.meta.subject],
                    invalidated: true
                }
            }
        default:
            return state;
    }
};

export default jsonLdBySubject;
