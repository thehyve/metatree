import React from 'react';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import Typography from "@material-ui/core/Typography";
import IconButton from "@material-ui/core/IconButton";
import ClearIcon from '@material-ui/icons/Clear';
import ListItemSecondaryAction from "@material-ui/core/ListItemSecondaryAction";
import {connect} from 'react-redux';
import ListItemText from "@material-ui/core/ListItemText";
import {compose} from "redux";
import {updateMetadata as updateMetadataAction} from "../../actions/metadataActions";
import ValueComponentFactory from "./values/ValueComponentFactory";
import ErrorDialog from "../common/ErrorDialog";
import withHovered from "../common/WithHovered";
import * as constants from '../../constants';

function MetadataProperty({
    editable, subject, property, updateMetadata, onItemMouseOut, onItemMouseOver, hovered
}) {
    // Function to save a certain value.
    // Calling it with an index provides you with a function that
    // will save a given value (if it has changed) along with the other
    // unchanged values.
    // E.g. handleSave(1) will return a function `value => { ... }` that
    // can be used as a callback for the component for index 1
    const handleSave = index => (newEntry) => {
        const currentEntry = property.values[index];
        if (currentEntry.value !== newEntry.value) {
            const updatedValues = property.values.map((el, idx) => ((idx === index) ? newEntry : el));
            return updateMetadata(subject, property.key, updatedValues)
                .catch(e => ErrorDialog.showError(e, "Error while saving metadata"));
        }
        return Promise.resolve();
    };

    const handleAdd = (newEntry) => {
        if (newEntry.value || newEntry.id) {
            const updatedValues = [...property.values, newEntry];

            return updateMetadata(subject, property.key, updatedValues)
                .catch(e => ErrorDialog.showError(e, "Error while adding metadata"));
        }
        return Promise.resolve();
    };

    const handleDelete = index => () => {
        const updatedValues = property.values.filter((el, idx) => idx !== index);
        return updateMetadata(subject, property.key, updatedValues)
            .catch(e => ErrorDialog.showError(e, "Error while deleting metadata"));
    };

    // Render the given entry as a list item
    const renderEntry = (entry, idx, PropertyValueComponent, labelledBy) => (
        <ListItem
            key={idx}
            onMouseOver={e => onItemMouseOver(idx, e)}
            onMouseOut={() => onItemMouseOut(idx)}
        >
            <ListItemText>
                <PropertyValueComponent
                    property={property}
                    entry={entry}
                    onSave={handleSave(idx)}
                    aria-labelledby={labelledBy}
                />
            </ListItemText>
            {
                editable
                    ? (
                        <ListItemSecondaryAction
                            onMouseOver={e => onItemMouseOver(idx, e)}
                            onMouseOut={() => onItemMouseOut(idx)}
                        >
                            <IconButton
                                style={{
                                    visibility: hovered !== idx ? 'hidden' : 'visible'
                                }}
                                size="small"
                                aria-label="Delete"
                                title="Delete"
                                onClick={handleDelete(idx)}
                            >
                                <ClearIcon />
                            </IconButton>
                        </ListItemSecondaryAction>
                    ) : null
            }
        </ListItem>
    );

    const renderAddComponent = (labelledBy) => {
        const ValueAddComponent = ValueComponentFactory.addComponent(property);
        return (
            <ListItem key={property.values.length}>
                <ListItemText>
                    <ValueAddComponent
                        property={property}
                        placeholder="Add new"
                        onSave={handleAdd}
                        aria-labelledby={labelledBy}
                    />
                </ListItemText>
            </ListItem>
        );
    };

    const isCollection = property.domain === constants.COLLECTION_URI;
    const isFile = property.domain === constants.FILE_URI;
    const isDirectory = property.domain === constants.DIRECTORY_URI;
    const isManaged = isCollection || isFile || isDirectory;
    if ((property.key === '@type')
        || (isManaged && property.key === constants.LABEL_URI)
        || (isCollection && property.key === constants.COMMENT_URI)) {
        return '';
    }
    // Do not show an add component if no multiples are allowed
    // and there is already a value
    const editableAndNotMachineOnly = editable && !property.machineOnly;
    const canAdd = editableAndNotMachineOnly && (property.allowMultiple || property.values.length === 0);
    const labelId = `label-${property.key}`;

    const ValueComponent = (editableAndNotMachineOnly && property.range !== constants.RESOURCE_URI)
        ? ValueComponentFactory.editComponent(property)
        : ValueComponentFactory.readOnlyComponent();

    return (
        <ListItem disableGutters key={property.key} style={{display: 'block'}}>
            <Typography variant="body1" component="label" id={labelId}>
                {property.label}
            </Typography>
            <List dense>
                {property.values.map((entry, idx) => renderEntry(entry, idx, ValueComponent, labelId))}
                {canAdd ? renderAddComponent(labelId) : null}
            </List>
        </ListItem>
    );
}

const mapDispatchToProps = {
    updateMetadata: updateMetadataAction
};

export default compose(connect(null, mapDispatchToProps), withHovered)(MetadataProperty);