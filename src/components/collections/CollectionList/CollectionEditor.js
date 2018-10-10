import React from 'react';
import Dialog from '@material-ui/core/Dialog';
import DialogTitle from '@material-ui/core/DialogTitle';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogActions from '@material-ui/core/DialogActions';
import TextField from '@material-ui/core/TextField';
import Button from '@material-ui/core/Button';
import Select from "@material-ui/core/Select/Select";
import MenuItem from "@material-ui/core/MenuItem/MenuItem";
import FormControl from "@material-ui/core/FormControl/FormControl";
import InputLabel from "@material-ui/core/InputLabel/InputLabel";

class CollectionEditor extends React.Component {
    constructor(props) {
        super(props);

        this.state = {};
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.state.editing !== this.props.editing) {
            this.setState({
                title: this.props.title,
                name: this.props.name || '',
                description: this.props.description || '',
                type: this.props.type || 'LOCAL_FILE',
                editing: this.props.editing
            });
        }
    }

    close() {
        this.setState({editing: false});
    }

    handleCancel() {
        this.close();
        if (this.props.onCancel) {
            this.props.onCancel();
        }
    }

    handleSave() {
        if(!this.state.name) {
            return;
        }

        this.close();
        if (this.props.onSave) {
            this.props.onSave(this.state.name, this.state.description, this.state.type);
        }
    }

    handleInputChange(event) {
        this.setState({[event.target.name]: event.target.value});
    }

    render() {
        return (<Dialog
                open={this.state.editing}
                onClose={this.close.bind(this)}
                aria-labelledby='form-dialog-title'>
                <DialogTitle id='form-dialog-title'>{this.state.title}</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        You can edit the collection name and description here.
                    </DialogContentText>
                    <TextField
                        autoFocus
                        margin='dense'
                        id='name'
                        label='Name'
                        value={this.state.name}
                        name='name'
                        onChange={this.handleInputChange.bind(this)}
                        fullWidth
                        required={true}
                    />
                    <TextField
                        autoFocus
                        margin='dense'
                        multiline={true}
                        id='description'
                        label='Description'
                        name='description'
                        value={this.state.description}
                        onChange={this.handleInputChange.bind(this)}
                        fullWidth
                    />
                    {this.props.editType ?
                        <FormControl>
                            <InputLabel>Type</InputLabel>
                            <Select
                                name={'type'}
                                value={this.state.type}
                                onChange={this.handleInputChange.bind(this)}
                            >
                                <MenuItem value={'LOCAL_FILE'}>Local Filesystem</MenuItem>
                                <MenuItem value={'S3_BUCKET'}>S3 Mount</MenuItem>
                            </Select>
                        </FormControl>
                        : null}

                </DialogContent>
                <DialogActions>
                    <Button onClick={this.handleCancel.bind(this)} color='secondary'>
                        Cancel
                    </Button>
                    <Button onClick={this.handleSave.bind(this)} color='primary'>
                        Save
                    </Button>
                </DialogActions>
            </Dialog>
        );
    }
}

export default CollectionEditor;
