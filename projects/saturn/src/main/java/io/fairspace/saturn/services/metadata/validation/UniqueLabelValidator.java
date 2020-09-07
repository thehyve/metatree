package io.fairspace.saturn.services.metadata.validation;

import io.fairspace.saturn.vocabulary.FS;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

public class UniqueLabelValidator implements MetadataRequestValidator {
    @Override
    public void validate(Model before, Model after, Model removed, Model added, Model vocabulary, ViolationHandler violationHandler) {
        added.listSubjectsWithProperty(RDFS.label)
                .forEachRemaining(subject -> {
                    var resource = subject.inModel(after);
                    var type = resource.getPropertyResourceValue(RDF.type);
                    var label = resource.getProperty(RDFS.label).getString();
                    var conflictingResourceExists = after
                            .listSubjectsWithProperty(RDFS.label, label)
                            .filterDrop(subject::equals)
                            .filterKeep(res -> res.hasProperty(RDF.type, type))
                            .filterDrop(res -> res.hasProperty(FS.dateDeleted))
                            .hasNext();
                    if (conflictingResourceExists) {
                        violationHandler.onViolation("Duplicate label", resource, RDFS.label, null);
                    }
                });
    }
}