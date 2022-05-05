import {render} from "@testing-library/react";
import React from "react";
import '@testing-library/jest-dom/extend-expect';
import {shallow} from "enzyme";
import TableRow from "@material-ui/core/TableRow";
import {TableBody} from "@material-ui/core";
import {MetadataViewTable} from "../MetadataViewTable";
// eslint-disable-next-line jest/no-mocks-import
import {mockRows, mockViews} from "../__mocks__/MetadataViewAPI";
import {RESOURCES_VIEW} from "../metadataViewUtils";

describe('MetadataViewTable', () => {
    const historyMock = {
        push: jest.fn()
    };

    it('renders correct header and values columns', () => {
        const view = 'Sample';
        const {columns} = mockViews().find(v => v.name === view);
        const data = {rows: mockRows(view)};
        const {queryByText, queryAllByText} = render(
            <MetadataViewTable
                columns={columns}
                visibleColumnNames={columns.map(c => c.name)}
                data={data}
                locationContext=""
                toggleRow={() => {}}
                history={historyMock}
                idColumn={columns[0]}
                collections={[]}
                textFiltersObject={{}}
                setTextFiltersObject={() => {}}
                loading={false}
            />
        );

        expect(queryByText('Sample')).toBeInTheDocument();
        expect(queryByText('Sample type')).toBeInTheDocument();
        expect(queryByText('Topography')).toBeInTheDocument();
        expect(queryByText('Nature')).toBeInTheDocument();
        expect(queryByText('Origin')).toBeInTheDocument();
        expect(queryByText('Files')).toBeInTheDocument();
        expect(queryByText('S01')).toBeInTheDocument();
        expect(queryByText('S02')).toBeInTheDocument();
        expect(queryAllByText('Tissue').length).toBe(2);
        expect(queryAllByText('DNA').length).toBe(2);
        expect(queryByText('Lip')).toBeInTheDocument();
        expect(queryByText('Tongue')).toBeInTheDocument();
    });

    it('renders visible columns only', () => {
        const view = 'Sample';
        const {columns} = mockViews().find(v => v.name === view);
        const data = {rows: mockRows(view)};
        const {queryByText, queryAllByText} = render(
            <MetadataViewTable
                columns={columns}
                visibleColumnNames={['Sample', 'Sample_sampleType', 'Sample_origin']}
                data={data}
                view=""
                locationContext=""
                toggleRow={() => {}}
                history={historyMock}
                idColumn={columns[0]}
                collections={[]}
                textFiltersObject={{}}
                setTextFiltersObject={() => {}}
            />
        );

        expect(queryByText('Sample')).toBeInTheDocument();
        expect(queryByText('Sample type')).toBeInTheDocument();
        expect(queryByText('Lip')).not.toBeInTheDocument();
        expect(queryByText('S01')).toBeInTheDocument();
        expect(queryByText('S02')).toBeInTheDocument();
        expect(queryAllByText('Tissue').length).toBe(2);
        expect(queryByText('Topography')).not.toBeInTheDocument();
        expect(queryByText('Nature')).not.toBeInTheDocument();
        expect(queryByText('Origin')).toBeInTheDocument();
        expect(queryByText('Files')).not.toBeInTheDocument();
        expect(queryByText('DNA')).not.toBeInTheDocument();
        expect(queryByText('Tongue')).not.toBeInTheDocument();
    });

    it('should redirect when opening collection entry', () => {
        const view = RESOURCES_VIEW;
        const {columns} = mockViews().find(v => v.name === view);
        const data = {rows: mockRows(view)};
        const wrapper = shallow(<MetadataViewTable
            view={view}
            columns={columns}
            visibleColumnNames={columns.map(c => c.name)}
            data={data}
            collections={[{iri: 'http://localhost:8080/api/webdav/c01'}]}
            locationContext=""
            toggleRow={() => {}}
            history={historyMock}
            idColumn={columns[0]}
            loading={false}
            textFiltersObject={{}}
            setTextFiltersObject={() => {}}
        />);

        const tableRows = wrapper.find(TableBody).find(TableRow);
        expect(tableRows.length).toEqual(1);
        tableRows.first().prop("onDoubleClick")();

        expect(historyMock.push).toHaveBeenCalledTimes(1);
        expect(historyMock.push).toHaveBeenCalledWith('/metadata?iri=http://localhost:8080/api/webdav/c01');
    });
});
