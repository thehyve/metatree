import FileAPI from "../FileAPI";
import Config from "../../common/services/Config";

beforeAll(() => {
    Config.setConfig({
        urls: {
            files: "/files",
            download: "/download"
        }
    });

    return Config.init();
});

describe('FileAPI', () => {
    describe('Uploading', () => {
        it('uploads a file', async () => {
            FileAPI.webDavClient = {putFileContents: jest.fn(() => Promise.resolve({}))};
            const file = {file: 'FILE_OBJECT', destinationFilename: 'destination.txt', destinationPath: '/test/path'};

            const result = await FileAPI.upload(file);
            expect(result).toEqual({});
            expect(FileAPI.webDavClient.putFileContents.mock.calls[0][0]).toEqual('/test/path/destination.txt');
            expect(FileAPI.webDavClient.putFileContents.mock.calls[0][1]).toEqual('FILE_OBJECT');
        });

        it('should result in a clear error on 403 response', () => {
            // eslint-disable-next-line prefer-promise-reject-errors
            FileAPI.webDavClient = {putFileContents: jest.fn(() => Promise.reject({response: {status: 403}}))};
            const file = {file: 'FILE_OBJECT', destinationFilename: 'destination.txt', destinationPath: '/test/path'};

            return expect(FileAPI.upload(file)).rejects.toEqual(new Error("You do not have authorization to add files to this collection."));
        });
    });

    describe('Moving', () => {
        it('ignores cut-and-paste into same folder', async () => {
            FileAPI.webDavClient = {moveFile: jest.fn(() => Promise.resolve())};

            await FileAPI.move('/coll/path/file.ext', '/coll/path/file.ext');

            expect(FileAPI.webDavClient.moveFile.mock.calls.length).toEqual(0);
        });

        it('should result in a clear error on 400 response', () => {
            // eslint-disable-next-line prefer-promise-reject-errors
            FileAPI.webDavClient = {moveFile: jest.fn(() => Promise.reject({response: {status: 400}}))};

            return expect(FileAPI.move("/test", "special-characters"))
                .rejects.toEqual(new Error("Could not move one or more files. Possibly the filename contains special characters."));
        });

        it('should result in a clear error on 403 response', () => {
            // eslint-disable-next-line prefer-promise-reject-errors
            FileAPI.webDavClient = {moveFile: jest.fn(() => Promise.reject({response: {status: 403}}))};

            return expect(FileAPI.move("/test", "special-characters"))
                .rejects.toEqual(new Error("Could not move one or more files. Do you have write permission to both the source and destination collection?"));
        });

        it('should result in a clear error on 409 response', () => {
            // eslint-disable-next-line prefer-promise-reject-errors
            FileAPI.webDavClient = {moveFile: jest.fn(() => Promise.reject({response: {status: 409}}))};

            return expect(FileAPI.move("/test", "special-characters"))
                .rejects.toEqual(new Error("Could not move one or more files. The destination can not be copied to."));
        });

        it('should result in a clear error on 412 response', () => {
            // eslint-disable-next-line prefer-promise-reject-errors
            FileAPI.webDavClient = {moveFile: jest.fn(() => Promise.reject({response: {status: 412}}))};

            return expect(FileAPI.move("/test", "special-characters"))
                .rejects.toEqual(new Error("Could not move one or more files. The destination file already exists."));
        });
    });

    describe('Deleting', () => {
        it('should result in a clear error on 403 response', () => {
            // eslint-disable-next-line prefer-promise-reject-errors
            FileAPI.webDavClient = {deleteFile: jest.fn(() => Promise.reject({response: {status: 403}}))};

            return expect(FileAPI.delete('path'))
                .rejects.toEqual(new Error("Could not delete file or directory. Do you have write permissions to the collection?"));
        });
    });

    describe('uniqueDestinationPaths', () => {
        it('generates unique names', async () => {
            FileAPI.list = jest.fn(() => Promise.resolve([{basename: 'file.ext'}, {basename: 'file (1).ext'}, {basename: 'file (2).ext'}]));
            const result = await FileAPI.uniqueDestinationPaths(['/src/file.ext', '/src/file (2).ext'], '/dst');

            expect(result).toEqual([
                ['/src/file.ext', '/dst/file (3).ext'],
                ['/src/file (2).ext', '/dst/file (2) (1).ext']]);
        });

        it('leaves already unique names untouched', async () => {
            FileAPI.list = jest.fn(() => Promise.resolve([{basename: 'file.ext'}]));
            const result = await FileAPI.uniqueDestinationPaths(['/src/new.ext'], '/dst');

            expect(result).toEqual([['/src/new.ext', '/dst/new.ext']]);
        });

        it('handles multiple files with smae name', async () => {
            FileAPI.list = jest.fn(() => Promise.resolve([{basename: 'file.ext'}]));
            const result = await FileAPI.uniqueDestinationPaths(['/src1/file.ext', '/src2/file.ext'], '/dst');

            expect(result).toEqual([['/src1/file.ext', '/dst/file (1).ext'], ['/src2/file.ext', '/dst/file (2).ext']]);
        });
    });

    describe('createDirectory', () => {
        it('should result in clear error on 405 response', () => {
            // eslint-disable-next-line prefer-promise-reject-errors
            FileAPI.webDavClient = {createDirectory: jest.fn(() => Promise.reject({response: {status: 405}}))};

            return expect(FileAPI.createDirectory("/test")).rejects.toEqual(new Error("A directory or file with this name already exists. Please choose another name"));
        });
    });

    it('Generates proper download link', () => {
        const downloadLink = FileAPI.getDownloadLink('/filePath');
        expect(downloadLink).toEqual('/download/filePath');
    });
});
