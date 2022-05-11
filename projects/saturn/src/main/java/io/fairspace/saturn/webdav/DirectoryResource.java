package io.fairspace.saturn.webdav;

import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ValidationException;
import io.fairspace.saturn.vocabulary.FS;
import io.milton.http.Auth;
import io.milton.http.FileItem;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.http.exceptions.NotFoundException;
import io.milton.resource.DeletableCollectionResource;
import io.milton.resource.FolderResource;
import io.milton.resource.Resource;
import org.apache.commons.csv.CSVFormat;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static io.fairspace.saturn.config.Services.METADATA_PERMISSIONS;
import static io.fairspace.saturn.config.Services.METADATA_SERVICE;
import static io.fairspace.saturn.rdf.ModelUtils.getStringProperty;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static io.fairspace.saturn.webdav.DavUtils.*;
import static io.fairspace.saturn.webdav.PathUtils.*;
import static io.fairspace.saturn.webdav.WebDAVServlet.getBlob;
import static io.fairspace.saturn.webdav.WebDAVServlet.setErrorMessage;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;

class DirectoryResource extends BaseResource implements FolderResource, DeletableCollectionResource {
    public DirectoryResource(DavFactory factory, org.apache.jena.rdf.model.Resource subject, Access access) {
        super(factory, subject, access);
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return switch (method) {
            case COPY -> access.canRead();
            case MKCOL -> {
                try {
                    yield writeAllowed();
                } catch (BadRequestException e) {
                    setErrorMessage(e.getMessage());
                    yield false;
                }
            }
            default -> super.authorise(request, method, auth);
        };
    }

    @Override
    public Date getModifiedDate() {
        return null;
    }

    /**
     * Directories in Fairspace are linked to entities. Each directory has an entitytype attribute and an entity iri attribute.
     * When a new directory is created at the client side, the request is processed here.
     *
     * First we create the directory, than create the new entity of the specified type, and save the iri of the
     * new entity as directory attribute.
     *
     * In the frontend when you click a directory, in the right panel you see the properties of the entity. From a user perspective
     * the directories are the entities.
     */
    @Override
    public io.milton.resource.CollectionResource createCollection(String newName) throws NotAuthorizedException, ConflictException, BadRequestException {
        var subj = createResource(newName)
                .addProperty(RDF.type, FS.Directory);

        factory.linkEntityToSubject(subj);

        return (io.milton.resource.CollectionResource) factory.getResource(subj, access);
    }

    @Override
    public Resource createNew(String name, InputStream inputStream, Long length, String contentType) throws IOException, ConflictException, NotAuthorizedException, BadRequestException {
        return createNew(name, getBlob(), contentType);
    }

    private Resource createNew(String name, BlobInfo blob, String contentType) throws NotAuthorizedException, ConflictException, BadRequestException {
        var subj = createResource(name)
                .addProperty(RDF.type, FS.File)
                .addLiteral(FS.currentVersion, 1)
                .addProperty(FS.versions, subject.getModel().createList(newVersion(blob)));

        if (contentType != null) {
            subj.addProperty(FS.contentType, contentType);
        }

        return factory.getResource(subj, access);
    }

    private org.apache.jena.rdf.model.Resource createResource(String name) throws ConflictException, NotAuthorizedException, BadRequestException {
        validateResourceName(name);
        name = name.trim();
        factory.validateChildNameUniqueness(subject, name);
        var subj = factory.createDavResource(name, subject);

        updateParents(subject);
        return subj;
    }

    @Override
    public Resource child(String childName) throws NotAuthorizedException, BadRequestException {
        return factory.getResource(childSubject(subject, childName), access);
    }

    @Override
    public List<? extends Resource> getChildren() {
        return subject.getModel()
                .listSubjectsWithProperty(FS.belongsTo, subject)
                .mapWith(r -> factory.getResource(r, access))
                .filterDrop(Objects::isNull)
                .toList();
    }

    @Override
    public void delete(boolean purge) throws NotAuthorizedException, ConflictException, BadRequestException {
        for (var child : getChildren()) {
            ((BaseResource) child).delete(purge);
        }
        super.delete(purge);
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) throws IOException, NotAuthorizedException, BadRequestException, NotFoundException {
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    @Override
    public String getContentType(String accepts) {
        return "text/html";
    }

    @Override
    public Long getContentLength() {
        return null;
    }

    @Override
    public boolean isLockedOutRecursive(Request request) {
        return false;
    }

    @Override
    protected void performAction(String action, Map<String, String> parameters, Map<String, FileItem> files) throws BadRequestException, NotAuthorizedException, ConflictException {
        switch (action) {
            // curl -i -H 'Authorization: Basic b3JnYW5pc2F0aW9uLWFkbWluOmZhaXJzcGFjZTEyMw==' \
            // -F 'action=upload_files' -F '/dir/subdir/file1.ext=@/dir/subdir/file1.ext' \
            // -F '/dir/subdir/file2.ext=@/dir/subdir/file2.ext' \
            // http://localhost:8080/api/webdav/c1/
            case "upload_files" -> uploadFiles(files);
            // curl -i -H 'Authorization: Basic b3JnYW5pc2F0aW9uLWFkbWluOmZhaXJzcGFjZTEyMw==' \
            // -F 'action=upload_metadata' -F 'file=@meta.csv' http://localhost:8080/api/webdav/c1/
            case "upload_metadata" -> uploadMetadata(files.get("file"));
            default -> super.performAction(action, parameters, files);
        }
    }

    private void uploadFiles(Map<String, FileItem> files) throws NotAuthorizedException, ConflictException, BadRequestException {
        for (var entry : files.entrySet()) {
            uploadFile(entry.getKey(), entry.getValue());
        }
    }

    private void uploadFile(String path, FileItem file) throws NotAuthorizedException, BadRequestException, ConflictException {
        path = normalizePath(URLDecoder.decode(path, StandardCharsets.UTF_8));
        if (path.contains("/")) {
            var segments = splitPath(path);
            var child = child(segments[0]);
            if (child == null) {
                child = createCollection(segments[0]);
            }
            if (!(child instanceof DirectoryResource)) {
                throw new ConflictException(child);
            }
            var dir = (DirectoryResource) child;
            var relPath = Stream.of(segments)
                    .skip(1)
                    .collect(joining("/"));
            dir.uploadFile(relPath, file);
        } else {
            var blob = ((BlobFileItem) file).getBlob();
            var child = child(path);
            if (child != null) {
                if (child instanceof FileResource) {
                    ((FileResource) child).replaceContent(blob);
                } else {
                    throw new ConflictException(child);
                }
            } else {
                createNew(path, blob, file.getContentType());
            }
        }
    }

    private void uploadMetadata(FileItem file) throws BadRequestException {
        var entityColumn = "DirectoryName";

        if (file == null) {
            setErrorMessage("Missing 'file' parameter");
            throw new BadRequestException(this);
        }
        var model = createDefaultModel();

        try (var is = file.getInputStream();
             var reader = new InputStreamReader(is);
             var csvParser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withCommentMarker('#')
                     .withIgnoreEmptyLines()
                     .parse(reader)) {
            var headers = new HashSet<>(csvParser.getHeaderNames());
            if (!headers.contains(entityColumn)) {
                setErrorMessage("Line " + csvParser.getCurrentLineNumber() + ". Invalid file format. '" + entityColumn + "' column is missing.");
                throw new BadRequestException(this);
            }
            try {
                for (var record : csvParser) {
                    var directory = getDirectory(record.get(entityColumn), csvParser.getCurrentLineNumber(), entityColumn);

                    var classShape = directory.getPropertyResourceValue(FS.linkedEntityType).inModel(VOCABULARY);
                    var entity = directory.getPropertyResourceValue(FS.linkedEntity).inModel(VOCABULARY);

                    var propertyShapes = new HashMap<String, org.apache.jena.rdf.model.Resource>();

                    classShape.listProperties(SHACLM.property)
                            .mapWith(Statement::getObject)
                            .mapWith(RDFNode::asResource)
                            .filterKeep(propertyShape -> propertyShape.hasProperty(SHACLM.name)
                                    && propertyShape.hasProperty(SHACLM.path)
                                    && propertyShape.getProperty(SHACLM.path).getObject().isURIResource())
                            .forEachRemaining(propertyShape -> {
                                var name = getStringProperty(propertyShape, SHACLM.name);
                                if (name != null) {
                                    propertyShapes.put(name, propertyShape);
                                }
                            });

                    for (var header : headers) {
                        if (header.equals(entityColumn)) {
                            continue;
                        }

                        var text = record.get(header);

                        if (isBlank(text)) {
                            continue;
                        }

                        var propertyShape = propertyShapes.get(header);

                        if (propertyShape == null) {
                            setErrorMessage("Line " + csvParser.getCurrentLineNumber() + ". Unknown attribute: " + header);
                            throw new BadRequestException(this);
                        }

                        var property = model.createProperty(propertyShape.getPropertyResourceValue(SHACLM.path).getURI());
                        var datatype = propertyShape.getPropertyResourceValue(SHACLM.datatype);
                        var class_ = propertyShape.getPropertyResourceValue(SHACLM.class_);
                        assert (datatype != null) ^ (class_ != null);

                        var values = text.split("\\|");

                        for (var value : values) {
                            if (class_ != null) {
                                var object = subject.getModel().listResourcesWithProperty(RDF.type, class_)
                                        .filterKeep(r -> r.getURI().equals(value) || r.hasProperty(RDFS.label, value))
                                        .toList();
                                if (object.size() == 1) {
                                    model.add(entity, property, object.get(0));
                                } else if (object.size() > 1) {
                                    setErrorMessage("Line " + csvParser.getCurrentLineNumber()
                                            + ". Object \"" + value + "\" of class " + "\"" + class_ + "\" is not unique.");
                                    throw new BadRequestException(this);
                                } else {
                                    setErrorMessage("Line " + csvParser.getCurrentLineNumber()
                                            + ". Object \"" + value + "\" of class " + "\"" + class_ + "\" does not exist.");
                                    throw new BadRequestException(this);
                                }
                            } else {
                                var o = model.createTypedLiteral(value, datatype.getURI());
                                model.add(entity, property, o);
                            }
                        }
                    }
                }
            } catch (IllegalStateException e) {
                setErrorMessage("Line " + csvParser.getCurrentLineNumber() + ". Metadata file is not a valid comma separated values file (CSV).");
                throw new BadRequestException("Error parsing file " + file.getName(), e);
            }
        } catch (IllegalArgumentException | IOException e) {
            setErrorMessage(e.getMessage());
            throw new BadRequestException("Error parsing file " + file.getName(), e);
        }

        MetadataService metadataService = factory.context.get(METADATA_SERVICE);
        try {
            metadataService.patch(model);
        } catch (ValidationException e) {
            var message = new StringBuilder("Validation of the uploaded metadata failed.\n");
            var n = 0;
            for (var v : e.getViolations()) {
                var path = v.getSubject().replaceFirst(subject.getURI(), "");
                path = URLDecoder.decode(path, StandardCharsets.UTF_8);
                var propertyShapes = VOCABULARY.listResourcesWithProperty(SHACLM.path, createURI(v.getPredicate()));
                var propertyName = propertyShapes.hasNext()
                        ? getStringProperty(propertyShapes.next(), SHACLM.name)
                        : createURI(v.getPredicate()).getLocalName();
                message.append("- <")
                        .append(path)
                        .append("> has an invalid value for property *")
                        .append(propertyName)
                        .append("*: ")
                        .append(v.getMessage())
                        .append(".\n");
                if (++n == 5) {
                    if (e.getViolations().size() > n) {
                        message.append("+ ").append(e.getViolations().size() - n).append(" more errors.");
                    }
                    break;
                }
            }
            setErrorMessage(message.toString());
            throw new BadRequestException("Error applying metadata. " + message);
        } catch (AccessDeniedException e) {
            setErrorMessage("Access denied. " + e.getMessage());
            throw new BadRequestException("Error applying metadata. Access denied.");
        } catch (Exception e) {
            setErrorMessage(e.getMessage());
            throw new BadRequestException("Error applying metadata. " + e.getMessage());
        }
    }

    private org.apache.jena.rdf.model.Resource getDirectory(String name, long lineNumber, String columnName) throws BadRequestException {
        if (name.equals(".") || name.contains("/") || name.contains("\\")) {
            String error = "Line " + lineNumber + ". File \"" + name + "\" " + columnName + " contains invalid characters.";
            setErrorMessage(error);
            throw new BadRequestException(error);
        }

        var parent = Optional.of(subject)
                .map(s -> s.getPropertyResourceValue(FS.belongsTo))
                .orElse(null);

        var path = parent == null ? factory.rootSubject.getURI() + "/" + encodePath(name) : parent + "/" + encodePath(name);
        var dirResource = subject.getModel().getResource(path);

        if (!subject.getModel().containsResource(dirResource)
            || dirResource.getPropertyResourceValue(FS.linkedEntityType) == null
            || dirResource.getPropertyResourceValue(FS.linkedEntityType).inModel(VOCABULARY) == null) {
            String error = "Line " + lineNumber + ". File \"" + name + "\" not found";
            setErrorMessage(error);
            throw new BadRequestException(error);
        }

        if (subject.hasProperty(FS.dateDeleted)) {
            String error = "Line " + lineNumber + ". File \"" + name + "\" was deleted";
            setErrorMessage(error);
            throw new BadRequestException(error);
        }

        return dirResource;
    }

    private boolean writeAllowed() throws BadRequestException {
        MetadataPermissions metadataPermissions = factory.context.get(METADATA_PERMISSIONS);
        var type = factory.getEntityTypeFromRequest();
        return metadataPermissions.canWriteMetadataByUri(type);
    }
}
