package io.fairspace.saturn.services.metadata;

import io.fairspace.saturn.vocabulary.FS;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;

import static io.fairspace.saturn.TestUtils.ensureRecentInstant;
import static io.fairspace.saturn.TestUtils.setupRequestContext;
import static io.fairspace.saturn.auth.RequestContext.ADD_SHARED_METADATA;
import static io.fairspace.saturn.vocabulary.FS.createdBy;
import static io.fairspace.saturn.vocabulary.FS.dateCreated;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createStringLiteral;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetadataEntityLifeCycleManagerTest {
    private MetadataEntityLifeCycleManager lifeCycleManager;
    private Dataset ds;
    private Model model;

    private final Node userIri = createURI("http://localhost/iri/userid");
    private Resource userResource = createResource("http://localhost/iri/userid");

    private Resource resource = createResource("http://resource");
    private Property property = ResourceFactory.createProperty("http://property");
    private Resource otherResource = createResource("http://resource2");

    @Before
    public void setUp() {
        ds = createTxnMem();
        model = ds.getDefaultModel();

        setupRequestContext(ADD_SHARED_METADATA);

        lifeCycleManager = new MetadataEntityLifeCycleManager(model);
    }

    @Test
    public void testCreationInformationStorage() {
        Model delta = ModelFactory.createDefaultModel();
        delta.add(resource, property, "test");
        delta.add(otherResource, property, "other-literal");

        lifeCycleManager.updateLifecycleMetadata(delta);

        assertTrue(model.contains(resource, createdBy, userResource));
        assertTrue(model.contains(otherResource, createdBy, userResource));

        String dateCreatedValue = model.getRequiredProperty(resource, dateCreated).getString();
        ensureRecentInstant(Instant.parse(dateCreatedValue));
    }

    @Test
    public void testNoCreationInformationStorageForObjects() {
        Model delta = ModelFactory.createDefaultModel();
        delta.add(resource, property, otherResource);

        lifeCycleManager.updateLifecycleMetadata(delta);

        assertFalse(model.contains(otherResource, createdBy, userResource));
    }

    @Test
    public void testStoreNotAddingCreationInformationForExistingEntities() {
        // Setup existing entity in model
        model.add(resource, createdBy, "someone else");

        // Try to add the information about the resource
        Model delta = ModelFactory.createDefaultModel();
        delta.add(resource, property, "test");

        lifeCycleManager.updateLifecycleMetadata(delta);

        assertFalse(model.contains(resource, createdBy, userResource));
        assertFalse(model.contains(resource, dateCreated));
    }

    @Test
    public void testStorageOfEmptyModel() {
        lifeCycleManager.updateLifecycleMetadata(ModelFactory.createDefaultModel());
        lifeCycleManager.updateLifecycleMetadata(null);

        assertTrue(model.isEmpty());
    }

    @Test
    public void testStorageOfBlankNodes() {
        // Try to add the information about the resource
        Model delta = ModelFactory.createDefaultModel();
        delta.add(createResource(), property, "test");

        lifeCycleManager.updateLifecycleMetadata(delta);

        // Expect no information to be added for blank nodes
        assertTrue(model.isEmpty());
    }

    @Test
    public void testStorageOfPermissions() {
        Model delta = ModelFactory.createDefaultModel();
        delta.add(resource, property, otherResource);

        lifeCycleManager.updateLifecycleMetadata(delta);
    }

    @Test
    public void testSoftDelete() {
        model.add(resource, RDFS.label, createStringLiteral("label"));

        assertTrue(lifeCycleManager.softDelete(resource));

        assertTrue(model.contains(resource, FS.dateDeleted));
        assertTrue(model.contains(resource, FS.deletedBy, createResource(userIri.getURI())));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSoftDeleteDoesNotDeleteSystemEntities() {
        model.add(resource, RDF.type, FS.File);

        lifeCycleManager.softDelete(resource);
    }

    @Test
    public void testSoftDeleteFailsWhenCalledTwice() {
        model.add(resource, RDFS.label, createStringLiteral("label"));

        assertTrue(lifeCycleManager.softDelete(resource));
        assertFalse(lifeCycleManager.softDelete(resource));
    }

    @Test
    public void testSoftDeleteFailsWhenCalledForNonExistingResource() {
        assertFalse(lifeCycleManager.softDelete(resource));
    }
}
