package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.*;
import io.fairspace.saturn.rdf.dao.*;
import io.fairspace.saturn.rdf.transactions.*;
import io.fairspace.saturn.services.maintenance.*;
import io.fairspace.saturn.services.metadata.*;
import io.fairspace.saturn.services.metadata.validation.*;
import io.fairspace.saturn.services.search.FileSearchRequest;
import io.fairspace.saturn.services.users.*;
import io.fairspace.saturn.services.workspaces.*;
import io.fairspace.saturn.webdav.*;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.*;
import io.milton.resource.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.core.*;
import org.apache.jena.sparql.util.*;
import org.eclipse.jetty.server.*;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.junit.*;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.*;

import static io.fairspace.saturn.TestUtils.*;
import static io.fairspace.saturn.auth.RequestContext.*;
import static io.fairspace.saturn.config.Services.FS_ROOT;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static org.apache.jena.query.DatasetFactory.*;
import static org.mockito.Mockito.*;

@Ignore("Ignored until the basic data model is not defined")
@RunWith(MockitoJUnitRunner.class)
public class JdbcQueryServiceTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = ConfigLoader.CONFIG.publicUrl + BASE_PATH;
    static final String SAMPLE_NATURE_BLOOD = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C12434";
    static final String ANALYSIS_TYPE_RNA_SEQ = "https://institut-curie.org/osiris#O6-12";
    static final String ANALYSIS_TYPE_IMAGING = "https://institut-curie.org/osiris#C37-2";

    @Mock
    BlobStore store;
    @Mock
    UserService userService;
    @Mock
    private MetadataPermissions permissions;
    MetadataService api;
    QueryService queryService;
    MaintenanceService maintenanceService;

    User user;
    Authentication.User userAuthentication;
    User workspaceManager;
    Authentication.User workspaceManagerAuthentication;
    User admin;
    Authentication.User adminAuthentication;
    private org.eclipse.jetty.server.Request request;

    private void selectRegularUser() {
        lenient().when(request.getAuthentication()).thenReturn(userAuthentication);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void selectAdmin() {
        lenient().when(request.getAuthentication()).thenReturn(adminAuthentication);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    @Before
    public void before() throws SQLException, NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var viewDatabase = new Config.ViewDatabase();
        viewDatabase.url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        viewDatabase.username = "sa";
        viewDatabase.password = "";
        ViewsConfig config = ConfigLoader.VIEWS_CONFIG;
        ViewStoreClientFactory.H2_DATABASE = true;
        var viewStoreClientFactory = new ViewStoreClientFactory(config, viewDatabase);

        var dsg = new TxnIndexDatasetGraph(DatasetGraphFactory.createTxnMem(), viewStoreClientFactory);
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        Model model = ds.getDefaultModel();

        maintenanceService = new MaintenanceService(userService, ds, viewStoreClientFactory);

        var context = new Context();

        var davFactory = new DavFactory(model.createResource(baseUri), store, userService, context);
        ds.getContext().set(FS_ROOT, davFactory.root);

        queryService = new JdbcQueryService(ConfigLoader.CONFIG.search, viewStoreClientFactory, tx, davFactory.root);

        when(permissions.canWriteMetadata(any())).thenReturn(true);
        api = new MetadataService(tx, VOCABULARY, new ComposedValidator(new DeletionValidator()), permissions, davFactory);

        userAuthentication = mockAuthentication("user");
        user = createTestUser("user", false);
        new DAO(model).write(user);
        workspaceManager = createTestUser("workspace-admin", false);
        new DAO(model).write(workspaceManager);
        workspaceManagerAuthentication = mockAuthentication("workspace-admin");
        adminAuthentication = mockAuthentication("admin");
        admin = createTestUser("admin", true);
        new DAO(model).write(admin);

        setupRequestContext();
        request = getCurrentRequest();

        selectAdmin();

        var taxonomies = model.read("taxonomies.ttl");
        api.put(taxonomies);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", 0, "md5"));

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        var coll1 = (PutableResource) root.createCollection("coll1");
        coll1.createNew("coffee.jpg", null, 0L, "image/jpeg");

        selectRegularUser();

        var coll2 = (PutableResource) root.createCollection("coll2");
        coll2.createNew("sample-s2-b-rna.fastq", null, 0L, "chemical/seq-na-fastq");

        var coll3 = (PutableResource) root.createCollection("coll3");

        coll3.createNew("sample-s2-b-rna_copy.fastq", null, 0L, "chemical/seq-na-fastq");

        var testdata = model.read("testdata.ttl");
        api.put(testdata);
    }

    @Test
    public void testRetrieveSamplePage() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        var page = queryService.retrieveViewPage(request);
        Assert.assertEquals(2, page.getRows().size());
        var row = page.getRows().get(0);
        Assert.assertEquals(Set.of("Sample", "Sample_nature", "Sample_parentIsOfNature", "Sample_origin", "Sample_topography", "Sample_tumorCellularity"), row.keySet());
        Assert.assertEquals("Sample A for subject 1", row.get("Sample").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals("Blood", row.get("Sample_nature").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals("Liver", row.get("Sample_topography").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals(45.2f, ((Number)row.get("Sample_tumorCellularity").stream().findFirst().orElseThrow().getValue()).floatValue(), 0.01);
    }

    @Test
    public void testRetrieveSamplePageAfterReindexing() {
        maintenanceService.recreateIndex();
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        var page = queryService.retrieveViewPage(request);
        Assert.assertEquals(2, page.getRows().size());
        var row = page.getRows().get(0);
        Assert.assertEquals(Set.of("Sample", "Sample_nature", "Sample_parentIsOfNature", "Sample_origin", "Sample_topography", "Sample_tumorCellularity"), row.keySet());
        Assert.assertEquals("Sample A for subject 1", row.get("Sample").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals("Blood", row.get("Sample_nature").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals("Liver", row.get("Sample_topography").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals(45.2f, ((Number)row.get("Sample_tumorCellularity").stream().findFirst().orElseThrow().getValue()).floatValue(), 0.01);
    }

    @Test
    public void testRetrieveSamplePageUsingSampleFilter() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("Sample_nature")
                        .values(Collections.singletonList(SAMPLE_NATURE_BLOOD))
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        Assert.assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplePageForAccessibleCollection() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("Resource_analysisType")
                        .values(Collections.singletonList(ANALYSIS_TYPE_RNA_SEQ))
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        Assert.assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplePageForUnaccessibleCollection() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("Resource_analysisType")
                        .values(Collections.singletonList(ANALYSIS_TYPE_IMAGING))
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        Assert.assertEquals(0, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplePageIncludeJoin() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setIncludeJoinedViews(true);
        var page = queryService.retrieveViewPage(request);
        Assert.assertEquals(2, page.getRows().size());
        var row1 = page.getRows().get(0);
        Assert.assertEquals("Sample A for subject 1", row1.get("Sample").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals(1, row1.get("Subject").size());
        var row2 = page.getRows().get(1);
        Assert.assertEquals("Sample B for subject 2", row2.get("Sample").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals(
                Set.of("RNA-seq", "Whole genome sequencing"),
                row2.get("Resource_analysisType").stream().map(ValueDTO::getLabel).collect(Collectors.toSet()));
    }

    @Test
    public void testRetrieveSamplePageIncludeJoinAfterReindexing() {
        maintenanceService.recreateIndex();
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setIncludeJoinedViews(true);
        var page = queryService.retrieveViewPage(request);
        Assert.assertEquals(2, page.getRows().size());
        var row1 = page.getRows().get(0);
        Assert.assertEquals("Sample A for subject 1", row1.get("Sample").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals(1, row1.get("Subject").size());
        var row2 = page.getRows().get(1);
        Assert.assertEquals("Sample B for subject 2", row2.get("Sample").stream().findFirst().orElseThrow().getLabel());
        Assert.assertEquals(
                Set.of("RNA-seq", "Whole genome sequencing"),
                row2.get("Resource_analysisType").stream().map(ValueDTO::getLabel).collect(Collectors.toSet()));
    }

    @Test
    public void testCountSamples() {
        var request = new CountRequest();
        request.setView("Sample");
        var result = queryService.count(request);
        Assert.assertEquals(2, result.getCount());
    }

    @Test
    public void testSearchFiles() {
        var request = new FileSearchRequest();
        // There are two files with 'rna' in the file name in coll2.
        request.setQuery("rna");
        var results = queryService.searchFiles(request);
        Assert.assertEquals(2, results.size());
        // Expect the results to be sorted by id
        Assert.assertEquals("sample-s2-b-rna.fastq", results.get(0).getLabel());
        Assert.assertEquals("sample-s2-b-rna_copy.fastq", results.get(1).getLabel());
    }

    @Test
    public void testSearchFilesRestrictsToAccessibleCollections() {
        var request = new FileSearchRequest();
        // There is one file named coffee.jpg in coll1, not accessible by the regular user.
        request.setQuery("coffee");
        var results = queryService.searchFiles(request);
        Assert.assertEquals(0, results.size());

        selectAdmin();
        results = queryService.searchFiles(request);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("coffee.jpg", results.get(0).getLabel());
    }

    @Test
    public void testSearchFilesRestrictsToAccessibleCollectionsAfterReindexing() {
        maintenanceService.recreateIndex();
        var request = new FileSearchRequest();
        // There is one file named coffee.jpg in coll1, not accessible by the regular user.
        request.setQuery("coffee");
        var results = queryService.searchFiles(request);
        Assert.assertEquals(0, results.size());

        selectAdmin();
        results = queryService.searchFiles(request);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("coffee.jpg", results.get(0).getLabel());
    }

    @Test
    public void testSearchFilesRestrictsToParentDirectory() {
        selectAdmin();
        var request = new FileSearchRequest();
        // There is one file named coffee.jpg in coll1.
        request.setQuery("coffee");

        request.setParentIRI(ConfigLoader.CONFIG.publicUrl + "/api/webdav/coll1");
        var results = queryService.searchFiles(request);
        Assert.assertEquals(1, results.size());

        request.setParentIRI(ConfigLoader.CONFIG.publicUrl + "/api/webdav/coll2");
        results = queryService.searchFiles(request);
        Assert.assertEquals(0, results.size());
    }

    @Test
    public void testSearchFileDescription() {
        selectAdmin();
        var request = new FileSearchRequest();
        // There is one file named sample-s2-b-rna.fastq with a description
        request.setQuery("corona");

        //request.setParentIRI(ConfigLoader.CONFIG.publicUrl + "/api/webdav/coll1");
        var results = queryService.searchFiles(request);
        Assert.assertEquals(1, results.size());
    }
}
