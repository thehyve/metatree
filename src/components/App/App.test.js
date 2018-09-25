import React from 'react';
import ReactDOM from 'react-dom';
import App from './App';
import Config from "../generic/Config/Config";
import configFile from "../../config";

beforeAll(() => {
    window.fetch = jest.fn(() => Promise.resolve({
        ok: true,
        json: () => {}
    }));

    Config.setConfig(Object.assign(configFile, {
        "externalConfigurationFiles": [],
    }));

    return Config.init();
});

it('renders without crashing', () => {
  const div = document.createElement('div');
  ReactDOM.render(<App />, div);
  ReactDOM.unmountComponentAtNode(div);
});
