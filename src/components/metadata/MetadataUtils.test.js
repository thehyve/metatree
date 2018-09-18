import combine from './MetadataUtils';

const vocabulary = [
    {
        '@id': 'http://fairspace.io/ontology#name',
        'http://www.w3.org/2000/01/rdf-schema#label': [{ '@value': 'Name' }],
        'http://www.w3.org/2000/01/rdf-schema#domain': [{ '@id': 'http://fairspace.io/ontology#Collection' }]
    },
    {
        '@id': 'http://fairspace.io/ontology#description',
        'http://www.w3.org/2000/01/rdf-schema#label': [{ '@value': 'Description' }],
        'http://www.w3.org/2000/01/rdf-schema#domain': [{ '@id': 'http://fairspace.io/ontology#Collection' }]
    },
    {
        '@id': 'http://schema.org/Creator',
        'http://www.w3.org/2000/01/rdf-schema#label': [{ '@value': 'Creator' }]
    },
    {
        '@id': 'http://schema.org/CreatedDate',
        'http://www.w3.org/2000/01/rdf-schema#label': [{ '@value': 'Created date' }],
        'http://www.w3.org/2000/01/rdf-schema#domain': [{ '@id': 'http://fairspace.io/ontology#Collection' }]
    },
    {
        '@id': 'http://fairspace.io/ontology#Collection',
        'http://www.w3.org/2000/01/rdf-schema#label': [{ '@value': 'Collection' }]
    }
];

describe('combination of vocabulary and metadata', () =>{
    it('returns an empty array when no properties are set', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
        }];

        let result = combine(vocabulary, metadata);
        expect(result).toEqual([]);
    });

    it('returns the type in a proper format', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            '@type': 'http://fairspace.io/ontology#Collection'
        }];

        let result = combine(vocabulary, metadata);
        expect(result.length).toEqual(1);
        expect(result[0].key).toEqual("@type");
        expect(result[0].label).toEqual("Type");
        expect(result[0].values.length).toEqual(1);
        expect(result[0].values[0]['@id']).toEqual('http://fairspace.io/ontology#Collection');
        expect(result[0].values[0]['rdfs:label']).toEqual('Collection');
    });

    xit('returns nothing without type', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            'http://fairspace.io/ontology#name': { '@value': 'Collection 1' }
        }];

        let result = combine(vocabulary, metadata);
        expect(result).toEqual([]);
    });


    it('returns values in vocabulary properly', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            '@type': 'http://fairspace.io/ontology#Collection',
            'http://fairspace.io/ontology#name': { '@value': 'Collection 1' }
        }];

        let result = combine(vocabulary, metadata);

        expect(result.length).toEqual(2);
        expect(result[0].key).toEqual("http://fairspace.io/ontology#name");
        expect(result[0].label).toEqual("Name");
        expect(result[0].values.length).toEqual(1);
        expect(result[0].values[0]['@value']).toEqual('Collection 1');
    });

    it('returns multiple values for one predicate in vocabulary properly', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            '@type': 'http://fairspace.io/ontology#Collection',
            'http://fairspace.io/ontology#description': [
                {
                    '@value': 'My first collection'
                },
                {
                    '@value': 'Some more info'
                }
            ]
        }];

        let result = combine(vocabulary, metadata);
        expect(result.length).toEqual(2);
        expect(result[0].key).toEqual("http://fairspace.io/ontology#description");
        expect(result[0].values.length).toEqual(2);
        expect(result[0].values[0]['@value']).toEqual('My first collection');
        expect(result[0].values[1]['@value']).toEqual('Some more info');
    });

    it('sorts properties in ascending order by label', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            '@type': 'http://fairspace.io/ontology#Collection',
            'http://fairspace.io/ontology#name': [
                {
                    '@value': 'My first collection'
                }
            ],
            'http://schema.org/CreatedDate': [
                {
                    '@value': 'yesterday'
                }
            ]
        }];

        let result = combine(vocabulary, metadata);
        expect(result.length).toEqual(3);
        expect(result[0].key).toEqual("http://schema.org/CreatedDate");
        expect(result[1].key).toEqual("http://fairspace.io/ontology#name");
        expect(result[2].key).toEqual("@type");
    });

    it('only returns properties in the vocabulary', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            '@type': 'http://fairspace.io/ontology#Collection',
            'http://fairspace.io/ontology#non-existing': [
                {
                    '@value': 'My first collection'
                }
            ]
        }];

        let result = combine(vocabulary, metadata);
        expect(result.length).toEqual(1);
        expect(result[0].key).toEqual('@type');
    });

    xit('adds all properties allowed for the specific type', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            '@type': 'http://fairspace.io/ontology#Collection',
            'http://fairspace.io/ontology#name': [
                {
                    '@value': 'My first collection'
                }
            ]
        }];

        let result = combine(vocabulary, metadata);
        expect(result.length).toEqual(2);
        expect(result[0].key).toEqual("http://schema.org/CreatedDate");
        expect(result[1].key).toEqual("http://fairspace.io/ontology#name");
    });

    xit('does not return properties not allowed for a specific type', () => {
        const metadata = [{
            '@id': 'http://fairspace.com/iri/collections/1',
            '@type': 'http://fairspace.io/ontology#Collection',
            'http://schema.org/Creator': [
                {
                    '@value': 'Ygritte'
                }
            ]
        }];

        let result = combine(vocabulary, metadata);
        expect(result.length).toEqual(1);
        expect(result[0].key).toEqual('@type');
    });
})
