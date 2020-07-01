package io.fairspace.saturn.rdf.search;

import org.apache.jena.graph.Node;
import org.apache.jena.query.text.DatasetGraphText;
import org.apache.jena.query.text.TextIndex;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.query.text.TextQuery;
import org.apache.jena.query.text.es.ESSettings;
import org.apache.jena.sparql.core.DatasetGraph;
import org.elasticsearch.client.Client;

import java.util.Map;
import java.util.function.Function;

public class IndexedDatasetGraph extends DatasetGraphText {
    public IndexedDatasetGraph(DatasetGraph dsg, ESSettings settings, Map<String, String> advancedSettings, Function<String, String> idToIndexMapper, boolean recreateIndex) {
        this(dsg, ElasticSearchClientFactory.build(settings, advancedSettings), idToIndexMapper, recreateIndex);
    }

    public IndexedDatasetGraph(DatasetGraph dsg, Client client, Function<String, String> idToIndexMapper, boolean recreateIndex) {
        this(dsg, new TextIndexESBulk(new TextIndexConfig(new AutoEntityDefinition()), client, idToIndexMapper), client, recreateIndex);
    }

    public IndexedDatasetGraph(DatasetGraph dsg, TextIndex index, Client client, boolean recreateIndex) {
        super(dsg, index, new SingleTripleTextDocProducer(index), true);

        getContext().set(TextQuery.textIndex, index);

        if (recreateIndex) {
            client.admin().indices().prepareDelete("_all").get();
        }
    }
}
