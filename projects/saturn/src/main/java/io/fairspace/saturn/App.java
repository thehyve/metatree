package io.fairspace.saturn;

import io.fairspace.saturn.rdf.Vocabulary;
import io.fairspace.saturn.rdf.inversion.InvertingDatasetGraph;
import io.fairspace.saturn.services.metadata.MetadataAPIServlet;
import io.fairspace.saturn.services.vocabulary.VocabularyAPIServlet;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionLocal;
import org.apache.jena.sparql.core.DatasetGraph;

import java.io.File;

import static org.apache.jena.tdb2.DatabaseMgr.connectDatasetGraph;

public class App {
    private static final String DEFAULT_DATASET_PATH = "/data/saturn/db";
    private static final String LOCAL_DATASET_PATH = "db";

    public static void main(String[] args) {
        System.out.println("Saturn is starting");

        String datasetPath = new File(DEFAULT_DATASET_PATH).exists() ? DEFAULT_DATASET_PATH : LOCAL_DATASET_PATH;

        DatasetGraph dsg = new InvertingDatasetGraph(connectDatasetGraph(datasetPath));
        Vocabulary.init(dsg);
        Dataset ds = DatasetFactory.wrap(dsg);



        RDFConnection connection = new RDFConnectionLocal(ds);

        FusekiServer.create()
                .add("/rdf", ds)
                .addServlet("/statements", new MetadataAPIServlet(connection))
                .addServlet("/vocabulary", new VocabularyAPIServlet(connection))
                .port(8080)
                .build()
                .start();

        System.out.println("Fuseki is running on :8080/rdf");
        System.out.println("Metadata API is running on :8080/statements");
        System.out.println("Vocabulary API is running on :8080/vocabulary");
    }
}