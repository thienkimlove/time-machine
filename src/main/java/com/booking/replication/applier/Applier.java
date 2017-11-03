package com.booking.replication.applier;

import com.booking.replication.augmenter.AugmentedRowsEvent;
import com.booking.replication.augmenter.AugmentedSchemaChangeEvent;
import com.booking.replication.pipeline.CurrentTransaction;
import com.booking.replication.pipeline.PipelineOrchestrator;
import com.google.code.or.binlog.BinlogEventV4;
import com.google.code.or.binlog.impl.event.*;

import java.io.IOException;

/**
 * Created by bosko on 11/14/15.
 */
public interface Applier {

    void applyAugmentedRowsEvent(AugmentedRowsEvent augmentedSingleRowEvent, CurrentTransaction currentTransaction)
            throws ApplierException, IOException;

    void applyBeginQueryEvent(QueryEvent event, CurrentTransaction currentTransaction);

    void applyCommitQueryEvent(QueryEvent event, CurrentTransaction currentTransaction);

    void applyXidEvent(XidEvent event, CurrentTransaction currentTransaction);

    void applyRotateEvent(RotateEvent event) throws ApplierException, IOException;

    void applyAugmentedSchemaChangeEvent(
            AugmentedSchemaChangeEvent augmentedSchemaChangeEvent,
            PipelineOrchestrator caller);

    void forceFlush() throws ApplierException, IOException;

    void applyFormatDescriptionEvent(FormatDescriptionEvent event);

    void applyTableMapEvent(TableMapEvent event);

    void waitUntilAllRowsAreCommitted(BinlogEventV4 event) throws IOException, ApplierException;

}
