package io.fairspace.saturn.services.metadata.validation;

import io.fairspace.saturn.vocabulary.FS;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.fairspace.saturn.rdf.ModelUtils.EMPTY_MODEL;
import static io.fairspace.saturn.rdf.ModelUtils.modelOf;
import static org.apache.jena.rdf.model.ResourceFactory.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class InverseForUsedPropertiesValidatorTest {
    private static final Resource CLASS_SHAPE1 = createResource("http://ex.com/class_shape1");
    private static final Resource CLASS_SHAPE2 = createResource("http://ex.com/class_shape2");
    private static final Resource PROPERTY_SHAPE1 = createResource("http://ex.com/property_shape1");
    private static final Resource PROPERTY_SHAPE2 = createResource("http://ex.com/property_shape2");
    private static final Property PROPERTY1 = createProperty("http://ex.com/property1");
    private static final Property PROPERTY2 = createProperty("http://ex.com/property2");
    private static final Resource ENTITY1 = createResource("http://ex.com/entity1");
    private static final Resource ENTITY2 = createResource("http://ex.com/entity2");
    private static final Resource CLASS1 = createResource("http://ex.com/class1");
    private static final Resource CLASS2 = createResource("http://ex.com/class2");

    private Dataset ds = DatasetFactory.create();
    private final InverseForUsedPropertiesValidator validator = new InverseForUsedPropertiesValidator(ds);

    @Mock
    private ViolationHandler violationHandler;


    @Test
    public void settingAnInverseForAnUnusedPropertyIsAllowed() {
        var model = modelOf(
                PROPERTY_SHAPE1, SHACLM.path, PROPERTY1,
                PROPERTY_SHAPE2, SHACLM.path, PROPERTY2,
                PROPERTY_SHAPE1, FS.inverseRelation, PROPERTY_SHAPE2,
                PROPERTY_SHAPE2, FS.inverseRelation, PROPERTY_SHAPE1);
        validator.validate(EMPTY_MODEL, model, EMPTY_MODEL, model, null, violationHandler);

        verifyZeroInteractions(violationHandler);
    }

    @Test
    public void settingAnInverseForAnUsedPropertyIsNotAllowed() {
        ds.getDefaultModel()
                .add(ENTITY1, RDF.type, CLASS1)
                .add(ENTITY1, PROPERTY1, ENTITY2)
                .add(ENTITY2, RDF.type, CLASS2)
                .add(ENTITY2, PROPERTY2, ENTITY1);

        var toAdd = modelOf(
                CLASS_SHAPE1, SHACLM.targetClass, CLASS1,
                CLASS_SHAPE2, SHACLM.targetClass, CLASS2,
                CLASS_SHAPE1, SHACLM.property, PROPERTY_SHAPE1,
                CLASS_SHAPE2, SHACLM.property, PROPERTY_SHAPE2,
                PROPERTY_SHAPE1, SHACLM.path, PROPERTY1,
                PROPERTY_SHAPE1, FS.domainIncludes, CLASS_SHAPE1,
                PROPERTY_SHAPE2, SHACLM.path, PROPERTY2,
                PROPERTY_SHAPE2, FS.domainIncludes, CLASS_SHAPE2,
                PROPERTY_SHAPE1, FS.inverseRelation, PROPERTY_SHAPE2,
                PROPERTY_SHAPE2, FS.inverseRelation, PROPERTY_SHAPE1);


        validator.validate(EMPTY_MODEL, toAdd, EMPTY_MODEL, toAdd, null, violationHandler);

        verify(violationHandler).onViolation("Cannot set fs:inverseRelation for a property that has been used already", createStatement(PROPERTY_SHAPE1, FS.inverseRelation, PROPERTY_SHAPE2));
        verify(violationHandler).onViolation("Cannot set fs:inverseRelation for a property that has been used already", createStatement(PROPERTY_SHAPE2, FS.inverseRelation, PROPERTY_SHAPE1));
        verifyNoMoreInteractions(violationHandler);
    }

    @Test
    public void unsettingAnInverseIsAlwaysAllowed() {
        var vocabulary = modelOf(
                CLASS_SHAPE1, SHACLM.targetClass, CLASS1,
                CLASS_SHAPE2, SHACLM.targetClass, CLASS2,
                CLASS_SHAPE1, SHACLM.property, PROPERTY_SHAPE1,
                CLASS_SHAPE2, SHACLM.property, PROPERTY_SHAPE2,
                PROPERTY_SHAPE1, SHACLM.path, PROPERTY1,
                PROPERTY_SHAPE1, FS.domainIncludes, CLASS_SHAPE1,
                PROPERTY_SHAPE2, SHACLM.path, PROPERTY2,
                PROPERTY_SHAPE2, FS.domainIncludes, CLASS_SHAPE2,
                PROPERTY_SHAPE1, FS.inverseRelation, PROPERTY_SHAPE2,
                PROPERTY_SHAPE2, FS.inverseRelation, PROPERTY_SHAPE1);

        ds.getDefaultModel()
                .add(ENTITY1, RDF.type, CLASS1)
                .add(ENTITY2, RDF.type, CLASS2)
                .add(ENTITY1, PROPERTY1, ENTITY2)
                .add(ENTITY2, PROPERTY2, ENTITY1);


        var toDelete = modelOf(
                PROPERTY_SHAPE1, FS.inverseRelation, PROPERTY_SHAPE2,
                PROPERTY_SHAPE2, FS.inverseRelation, PROPERTY_SHAPE1
        );

        validator.validate(vocabulary, vocabulary.difference(toDelete), toDelete, EMPTY_MODEL, null, violationHandler);

        verifyZeroInteractions(violationHandler);
    }
}