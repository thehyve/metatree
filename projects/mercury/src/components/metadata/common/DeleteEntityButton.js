import React, {useContext, useState} from 'react';
import {IconButton} from "@material-ui/core";
import Delete from "@material-ui/icons/Delete";
import {DeleteButton, ProgressButton} from "../../common";
import useIsMounted from "../../../utils/useIsMounted";
import LinkedDataContext from "../LinkedDataContext";
import ErrorDialog from "../../common/ErrorDialog";

const DeleteEntityButton = ({subject, isDeletable}) => {
    const {deleteLinkedDataEntity, hasEditRight} = useContext(LinkedDataContext);
    const [isDeleting, setDeleting] = useState(false);

    const isMounted = useIsMounted();

    const handleDelete = () => {
        setDeleting(true);

        deleteLinkedDataEntity(subject)
            .catch(e => ErrorDialog.showError(e, "An error occurred deleting the entity"))
            .then(() => isMounted() && setDeleting(false));
    };

    const canDelete = hasEditRight && isDeletable;

    return (
        <ProgressButton active={isDeleting}>
            <DeleteButton
                numItems={1}
                onClick={handleDelete}
                disabled={!canDelete}
            >
                <IconButton
                    aria-label="Delete this resource"
                    title="Delete"
                    disabled={!canDelete}
                >
                    <Delete />
                </IconButton>
            </DeleteButton>
        </ProgressButton>
    );
};

export default DeleteEntityButton;
