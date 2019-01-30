const AuthAwareFileSystem = require("../auth/RestrictedFileSystem");
const webdav = require('webdav-server').v2;
const fixWebdavDestinationMiddleware = require('./fixWebdavDestinationMiddleware');
const jwtAuthentication = require('../auth/jwt-authentication');
const PrivilegeManager = require('../auth/NeptunePathPrivilegeManager');

module.exports = function setupWebdavMiddleware(app, params, permissions, serverConfigurer) {
    const server = new webdav.WebDAVServer({
        requireAuthentification: params.auth.enabled,
        httpAuthentication: jwtAuthentication,
        rootFileSystem: new AuthAwareFileSystem(params.rootPath, permissions),
        privilegeManager: new PrivilegeManager(permissions)
    });

    serverConfigurer(server);

    app.use(fixWebdavDestinationMiddleware(params.basePath));
    return app.use(webdav.extensions.express(params.basePath, server));
}