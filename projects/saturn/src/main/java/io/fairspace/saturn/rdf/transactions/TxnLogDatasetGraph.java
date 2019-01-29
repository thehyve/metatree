package io.fairspace.saturn.rdf.transactions;

import io.fairspace.saturn.auth.UserInfo;
import io.fairspace.saturn.rdf.AbstractChangesAwareDatasetGraph;
import org.apache.jena.dboe.transaction.txn.TransactionException;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.QuadAction;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static java.lang.System.currentTimeMillis;

public class TxnLogDatasetGraph extends AbstractChangesAwareDatasetGraph {
    private final TransactionLog transactionLog;
    private final Supplier<UserInfo> userInfoProvider;
    private Set<Quad> added;   // Quads added in current transaction
    private Set<Quad> deleted; // Quads deleted in current transaction

    public TxnLogDatasetGraph(DatasetGraph dsg, TransactionLog transactionLog, Supplier<UserInfo> userInfoProvider) {
        super(dsg);
        this.transactionLog = transactionLog;
        this.userInfoProvider = userInfoProvider;
    }

    /**
     * Collects changes
     */
    @Override
    protected void onChange(QuadAction action, Node graph, Node subject, Node predicate, Node object) {
        var q = new Quad(graph, subject, predicate, object);
        switch (action) {
            case ADD:
                if (!deleted.remove(q)) {  // Check if it was previously deleted in same transaction
                    added.add(q);
                }
                break;
            case DELETE:
                if (!added.remove(q)) {    // Check if it was previously added in same transaction
                    deleted.add(q);
                }
                break;
        }
    }

    @Override
    public void begin(TxnType type) {
        begin(TxnType.convert(type));
    }


    /**
     * Start either a READ or WRITE transaction.
     **/
    @Override
    public void begin(ReadWrite readWrite) {
        super.begin(readWrite);
        if (readWrite == ReadWrite.WRITE) { // a write transaction => be ready to collect changes
            added = new HashSet<>();
            deleted = new HashSet<>();
        }
    }

    @Override
    public void commit() {
        if (isInWriteTransaction() && !(added.isEmpty() && deleted.isEmpty())) {
            logTransaction();
        }
        super.commit();
    }

    private void logTransaction() {
        var t = new TransactionRecord();
        t.setAdded(added);
        t.setDeleted(deleted);
        t.setTimestamp(currentTimeMillis());
        if (userInfoProvider != null) {
            var userInfo = userInfoProvider.get();
            if (userInfo != null) {
                t.setUserId(userInfo.getUserId());
                t.setUserName(userInfo.getFullName());
            }
        }
        added = null;
        deleted = null;
        try {
            transactionLog.log(t);
        } catch (IOException e) {
            throw new TransactionException("Error writing to the transaction log", e);
        }
    }

    @Override
    public void abort() {
        if (isInWriteTransaction()) {
            added = null;
            deleted = null;
        }
        super.abort();
    }

    @Override
    public void end() {
        if (isInWriteTransaction()) {
            abort();
        } else {
            super.end();
        }
    }

    private boolean isInWriteTransaction() {
        return isInTransaction() && transactionMode() == ReadWrite.WRITE;
    }
}
