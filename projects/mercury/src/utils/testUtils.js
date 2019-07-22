import React from 'react';
import {mount} from 'enzyme'; // eslint-disable-line import/no-extraneous-dependencies

export const mockResponse = (response, status = 200, statusText = 'OK', headers = {'Content-type': 'application/json'}) => (
    new window.Response(response, {
        status,
        statusText,
        headers
    })
);

export const testNoChangedOnUnknownActionType = (description, reducer) => {
    describe(description, () => {
        it('should return the same state unchanged if action type is unknown by reducer', () => {
            const state = {'say what?': 'you can not touch this'};
            expect(reducer(state, {
                type: 'ACTION_THAT_DOES_NOT_EXIST'
            })).toEqual({'say what?': 'you can not touch this'});
        });
    });
};

// TestHook method is borrowed from https://medium.com/@nitinpatel_20236/unit-testing-custom-react-hooks-caa86f58510
const TestHook = ({callback}) => {
    callback();
    return null;
};

export const testHook = (callback) => {
    mount(<TestHook callback={callback} />);
};
