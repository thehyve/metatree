import React from 'react';
import {Paper} from "@material-ui/core";
import {BreadCrumbs} from "@fairspace/shared-frontend";
import {LinkedDataEntityFormContainer, LinkedDataEntityHeader} from '.';

export default ({subject}) => (
    <>
        <BreadCrumbs />
        <Paper style={{maxWidth: 800, padding: 20}}>
            <LinkedDataEntityHeader subject={subject} />
            <LinkedDataEntityFormContainer subject={subject} fullpage />
        </Paper>
    </>
);
