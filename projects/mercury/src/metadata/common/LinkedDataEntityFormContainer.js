import React, {useContext, useState} from "react";
import PropTypes from "prop-types";
import {Button, CircularProgress, Grid, IconButton} from "@material-ui/core";
import {Edit} from '@material-ui/icons';

import {ConfirmationDialog} from '../../common';
import LinkedDataEntityForm from "./LinkedDataEntityForm";
import useFormData from '../UseFormData';
import LinkedDataContext from "../LinkedDataContext";
import FormContext from "./FormContext";
import useFormSubmission from "../UseFormSubmission";
import useNavigationBlocker from "../../common/hooks/UseNavigationBlocker";
import useLinkedData from "../UseLinkedData";

const LinkedDataEntityFormContainer = ({
    subject, editable = true, showEditButtons = false, fullpage = false,
    properties, values, linkedDataLoading, linkedDataError, updateLinkedData, isDeleted, ...otherProps
}) => {
    const [editingEnabled, setEditingEnabled] = useState(!showEditButtons);
    const {submitLinkedDataChanges, extendProperties, hasEditRight} = useContext(LinkedDataContext);

    const {
        addValue, updateValue, deleteValue, clearForm, getUpdates, hasFormUpdates, valuesWithUpdates,
        validateAll, validationErrors, isValid
    } = useFormData(values);

    const {isUpdating, submitForm} = useFormSubmission(
        () => submitLinkedDataChanges(subject, getUpdates())
            .then(() => {
                clearForm();
                updateLinkedData();
            }),
        subject
    );

    const canEdit = editingEnabled && hasEditRight && !isDeleted;

    const {
        confirmationShown, hideConfirmation, executeNavigation
    } = useNavigationBlocker(hasFormUpdates && canEdit);

    // Apply context-specific logic to the properties and filter on visibility
    const extendedProperties = extendProperties({properties, subject, isEntityEditable: canEdit});

    const validateAndSubmit = () => {
        const hasErrors = validateAll(extendedProperties);

        if (!hasErrors) submitForm();
    };

    const formId = `entity-form-${subject}`;
    let footer;

    if (isUpdating) {
        footer = <CircularProgress />;
    } else if (canEdit) {
        footer = (
            <Button
                type="submit"
                form={formId}
                variant={fullpage ? 'contained' : 'text'}
                color="primary"
                onClick={validateAndSubmit}
                disabled={!hasFormUpdates || !isValid}
            >
                Update
            </Button>
        );
    }

    return (
        <Grid container direction="row">
            <Grid item xs={11}>
                <FormContext.Provider value={{submit: validateAndSubmit}}>
                    <Grid container>
                        <Grid item xs={12}>
                            <LinkedDataEntityForm
                                {...otherProps}
                                id={formId}
                                editable={canEdit}
                                onSubmit={validateAndSubmit}
                                errorMessage={linkedDataError}
                                loading={linkedDataLoading}
                                properties={extendedProperties}
                                values={valuesWithUpdates}
                                validationErrors={validationErrors}
                                onAdd={addValue}
                                onChange={updateValue}
                                onDelete={deleteValue}
                            />
                        </Grid>
                        {footer && <Grid item>{footer}</Grid>}
                    </Grid>
                </FormContext.Provider>
                {confirmationShown && (
                    <ConfirmationDialog
                        open
                        title="Unsaved changes"
                        content={'You have unsaved changes, are you sure you want to navigate away?'
                        + ' Your pending changes will be lost.'}
                        agreeButtonText="Navigate"
                        disagreeButtonText="back to form"
                        onAgree={() => executeNavigation()}
                        onDisagree={hideConfirmation}
                    />
                )}
            </Grid>
            {editable && showEditButtons ? (
                <Grid item xs={1}>
                    {canEdit ? (
                        <Button
                            type="submit"
                            color="primary"
                            onClick={() => {
                                clearForm();
                                setEditingEnabled(false);
                            }}
                        >Cancel
                        </Button>
                    ) : (
                        <IconButton
                            aria-label="Edit"
                            onClick={() => {
                                setEditingEnabled(true);
                            }}
                        ><Edit />
                        </IconButton>
                    )}
                </Grid>
            ) : null}
        </Grid>
    );
};

LinkedDataEntityFormContainer.propTypes = {
    subject: PropTypes.string.isRequired,
    isEditable: PropTypes.bool,
};


export const LinkedDataEntityFormWithLinkedData = ({subject, isMetaDataEditable}) => {
    const {properties, values, linkedDataLoading, linkedDataError, updateLinkedData} = useLinkedData(subject);

    return (
        <LinkedDataEntityFormContainer
            subject={subject}
            isEntityEditable={isMetaDataEditable}
            properties={properties}
            values={values}
            linkedDataLoading={linkedDataLoading}
            linkedDataError={linkedDataError}
            updateLinkedData={updateLinkedData}
        />
    );
};

export default LinkedDataEntityFormContainer;
