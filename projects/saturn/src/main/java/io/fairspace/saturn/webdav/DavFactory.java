package io.fairspace.saturn.webdav;

import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.Resource;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.net.URI;

import static io.fairspace.saturn.auth.RequestContext.getUserURI;
import static io.fairspace.saturn.rdf.SparqlUtils.generateMetadataIri;
import static io.fairspace.saturn.util.EnumUtils.max;
import static io.fairspace.saturn.util.EnumUtils.min;
import static io.fairspace.saturn.webdav.AccessMode.DataPublished;
import static io.fairspace.saturn.webdav.AccessMode.MetadataPublished;
import static io.fairspace.saturn.webdav.PathUtils.encodePath;
import static io.fairspace.saturn.webdav.WebDAVServlet.*;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

public class DavFactory implements ResourceFactory {
    // Represents the root URI, not stored in the database
    final org.apache.jena.rdf.model.Resource rootSubject;
    final BlobStore store;
    final UserService userService;
    final Context context;
    private final String baseUri;
    public final RootResource root = new RootResource(this);

    public DavFactory(org.apache.jena.rdf.model.Resource rootSubject, BlobStore store, UserService userService, Context context) {
        this.rootSubject = rootSubject;
        this.store = store;
        this.userService = userService;
        this.context = context;
        var uri = URI.create(rootSubject.getURI());
        this.baseUri = URI.create(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "")).toString();
    }

    @Override
    public Resource getResource(String host, String path) throws NotAuthorizedException {
        return getResource(rootSubject.getModel().createResource(baseUri + "/" + encodePath(path)));
    }

    Resource getResource(org.apache.jena.rdf.model.Resource subject) {
        if (subject.equals(rootSubject)) {
            return root;
        }

        if (!subject.getModel().containsResource(subject)
                || subject.hasProperty(FS.movedTo)) {
            return null;
        }

        return getResource(subject, getAccess(subject));
    }

    public Access getAccess(org.apache.jena.rdf.model.Resource subject) {
        var uri = subject.getURI();
        var nextSeparatorPos = uri.indexOf('/', rootSubject.getURI().length() + 1);
        var coll = rootSubject.getModel().createResource(nextSeparatorPos < 0 ? uri : uri.substring(0, nextSeparatorPos));

        var user = currentUserResource();
        var ownerWs = coll.getPropertyResourceValue(FS.ownedBy);
        var deleted = coll.hasProperty(FS.dateDeleted) || (ownerWs != null && ownerWs.hasProperty(FS.dateDeleted));

        var access = getGrantedPermission(coll, user);

        if(user.hasProperty(FS.isManagerOf, ownerWs)) {
            access = Access.Manage;
        }

        if (coll.hasLiteral(FS.accessMode, DataPublished.name()) && (userService.currentUser().isCanViewPublicData() || access.canRead())) {
            return Access.Read;
        }
        if (!access.canList() && userService.currentUser().isCanViewPublicMetadata()
                && (coll.hasLiteral(FS.accessMode, MetadataPublished.name()) || coll.hasLiteral(FS.accessMode, DataPublished.name()))) {
            access = Access.List;
        }

        var userWorkspacesIterator = rootSubject.getModel()
                .listSubjectsWithProperty(RDF.type, FS.Workspace)
                .filterKeep(ws -> user.hasProperty(FS.isManagerOf, ws) || user.hasProperty(FS.isMemberOf, ws))
                .filterDrop(ws -> ws.hasProperty(FS.dateDeleted));
        while (userWorkspacesIterator.hasNext() && access != Access.Manage) {
            access = max(access, getGrantedPermission(coll, userWorkspacesIterator.next()));
        }

        if (deleted) {
            if (!showDeleted() && !isMetadataRequest()) {
                return Access.None;
            } else {
                access = min(access, Access.List);
            }
        } else if (coll.hasProperty(FS.status, Status.ReadOnly.name())) {
            access = min(access, Access.Read);
        } else if (coll.hasProperty(FS.status, Status.Archived.name())) {
            access = min(access, Access.List);
        }

        if(access == Access.None && userService.currentUser().isAdmin()) {
            return Access.List;
        }

        return access;
    }

    protected static Access getGrantedPermission(org.apache.jena.rdf.model.Resource resource, org.apache.jena.rdf.model.Resource principal) {
        if (principal.hasProperty(FS.canManage, resource)) {
            return Access.Manage;
        }
        if (principal.hasProperty(FS.canWrite, resource)) {
            return Access.Write;
        }
        if (principal.hasProperty(FS.canRead, resource)) {
            return Access.Read;
        }
        if (principal.hasProperty(FS.canList, resource)) {
            return Access.List;
        }
        return Access.None;
    }

    Resource getResource(org.apache.jena.rdf.model.Resource subject, Access access) {
        if (subject.hasProperty(FS.dateDeleted) && !showDeleted()) {
            return null;
        }
        if (subject.hasProperty(FS.movedTo)) {
            return null;
        }
        return getResourceByType(subject, access);
    }

    Resource getResourceByType(org.apache.jena.rdf.model.Resource subject, Access access) {
        if (subject.hasProperty(RDF.type, FS.File)) {
            return new FileResource(this, subject, access);
        }
        if (subject.hasProperty(RDF.type, FS.Directory)) {
            return new DirectoryResource(this, subject, access);
        }

        return null;
    }

    static org.apache.jena.rdf.model.Resource childSubject(org.apache.jena.rdf.model.Resource subject, String name) {
        return subject.getModel().createResource(subject.getURI() + "/" + encodePath(name));
    }

    org.apache.jena.rdf.model.Resource currentUserResource() {
        return rootSubject.getModel().createResource(getUserURI().getURI());
    }

    public boolean isFileSystemResource(org.apache.jena.rdf.model.Resource resource) {
        return resource.getURI().startsWith(rootSubject.getURI());
    }

    org.apache.jena.rdf.model.Resource getLinkedEntityType() throws BadRequestException {
        var type = entityType();
        if (type != null) {
            type = type.trim();
        }
        if (type == null || type.isEmpty()) {
            var message = "The linked entity type is empty.";
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
        return createResource(type);
    }

    public void addLinkedEntity(String name, org.apache.jena.rdf.model.Resource linkedDirectory, org.apache.jena.rdf.model.Resource type) {
        linkedDirectory.addProperty(FS.linkedEntityType, type);

        var entityIri = linkedEntityIri();

        if(entityIri != null && !entityIri.isEmpty()) {
            addExistingEntity(linkedDirectory, entityIri);
        }
        else {
            addNewLinkedEntity(name, linkedDirectory, type);
        }
    }

    private void addExistingEntity(org.apache.jena.rdf.model.Resource linkedDirectory, String entityIri) {
        linkedDirectory.addProperty(FS.linkedEntity, rootSubject.getModel().getResource(entityIri));
    }

    private void addNewLinkedEntity(String name, org.apache.jena.rdf.model.Resource linkedDirectory, org.apache.jena.rdf.model.Resource type) {
        var newEntity = linkedDirectory.getModel()
                .createResource(generateMetadataIri().getURI())
                .addProperty(RDF.type, type)
                .addProperty(RDFS.label, name)
                .addProperty(FS.createdBy, this.currentUserResource())
                .addProperty(FS.dateCreated, WebDAVServlet.timestampLiteral());

        linkedDirectory.addProperty(FS.linkedEntity, newEntity);
    }
}
