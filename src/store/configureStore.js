if (process.env.NODE_ENV === 'production') {
    module.exports = require('./configureStore.prod')
} else {
    module.exports = require('./configureStore.dev')
}

/*
Expected state:

{
    cache: {
        collections: {
            pending: false,
            error: false,
            items: {
                4: {
                    id: 4,
                    name: 'John Snow\'s collection',
                    description: 'Around the world...',
                    location: 'john_snow_s_collection-4',
                    uri: 'http://workspace.uri/iri/collections/4'
                },
                6: {
                    id: 6,
                    name: 'John Snow\'s collection',
                    description: 'Around the world...',
                    location: 'john_snow_s_collection-6',
                    uri: 'http://workspace.uri/iri/collections/6'
                }
            }
        },
        files: {
            4: {
                '/': {
                    pending: false,
                    error: false,
                    items: [
                        {
                            name: 'file.txt',
                            size: 292
                        },
                        {
                            name: 'subdir',
                            type: 'dir'
                        }
                    ]
                },
                '/subdir': {
                    pending: true,
                    error: false,
                    items: []
                }
            }
        }
    }
    collections: {
        selectedCollection: 4,
        selectedPath: '/subdirectory/file.txt',

        openedCollection: 4,
        openedPath: '/subdirectory',
    },
    account: {
        user: {
            pending: false,
            error: false,
            item: {
                id: 'a2ecd794-faa8-44ef-8fae-d70af8f437ee',
                name: 'Ygritte'
            }
        }
        authorizations: {
            pending: false,
            error: false,
            items: [
                'user-workspace-ci',
                'admin-workspace-ci',
                'uma-authorization'
            ]
        }
    },
    ui: {
        informationPanelOpened: true
    }
}
 */