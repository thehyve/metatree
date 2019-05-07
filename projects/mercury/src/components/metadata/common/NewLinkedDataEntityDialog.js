import React from 'react';
import PropTypes from 'prop-types';
import {connect} from 'react-redux';
import {Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@material-ui/core";

import {generateUuid, getLabel} from "../../../utils/linkeddata/metadataUtils";
import LinkedDataEntityFormContainer from "./LinkedDataEntityFormContainer";
import {hasLinkedDataFormUpdates, hasLinkedDataFormValidationErrors} from "../../../reducers/linkedDataFormReducers";

class NewLinkedDataEntityDialog extends React.Component {
    state = {
        formKey: generateUuid(),
        id: generateUuid()
    };

    componentDidUpdate(prevProps) {
        // Reset form key and new identifier when the
        // dialog opens to prevent reusing the same
        // identifier
        if (!prevProps.open && this.props.open) {
            this.resetDialog();
        }
    }

    resetDialog() {
        this.setState({
            formKey: generateUuid(),
            id: generateUuid()
        });
    }

    closeDialog = (e) => {
        if (e) e.stopPropagation();
        this.props.onClose();
    };

    createEntity = (e) => {
        if (e) e.stopPropagation();
        this.props.onCreate(this.state.formKey, this.props.shape, this.state.id);
    };

    handleInputChange = event => this.setState({id: event.target.value});

    render() {
        const {shape, open, linkedData, storeState} = this.props;
        const {id, formKey} = this.state;
        const typeLabel = getLabel(shape);

        return (
            <Dialog
                open={open}
                onClose={this.closeDialog}
                aria-labelledby="form-dialog-title"
                fullWidth
                maxWidth="sm"
            >
                <DialogTitle id="form-dialog-title">{typeLabel}</DialogTitle>

                <DialogContent style={{overflowX: 'hidden'}}>
                    <TextField
                        autoFocus
                        id="name"
                        label="Id"
                        value={id}
                        name="id"
                        onChange={this.handleInputChange}
                        fullWidth
                        required
                        error={!this.hasValidId()}
                    />

                    <LinkedDataEntityFormContainer
                        formKey={formKey}
                        properties={linkedData}
                    />

                </DialogContent>
                <DialogActions>
                    <Button
                        onClick={this.closeDialog}
                        color="secondary"
                    >
                        Cancel
                    </Button>
                    <Button
                        onClick={this.createEntity}
                        color="primary"
                        disabled={!this.canCreate() || !hasLinkedDataFormUpdates(storeState, formKey) || hasLinkedDataFormValidationErrors(storeState, formKey)}
                    >
                        Create
                    </Button>
                </DialogActions>
            </Dialog>
        );
    }

    hasValidId() {
        return new URL(`http://example.com/${this.state.id}`).toString() === `http://example.com/${this.state.id}`;
    }

    canCreate() {
        return this.state.id && this.hasValidId();
    }
}

NewLinkedDataEntityDialog.propTypes = {
    shape: PropTypes.object,
    onCreate: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
    open: PropTypes.bool
};

const mapStateToProps = (state) => ({
    storeState: state,
});

export default connect(mapStateToProps)(NewLinkedDataEntityDialog);
