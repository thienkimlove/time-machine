package com.booking.replication.schema.table;

import com.booking.replication.schema.column.ColumnSchema;
import com.booking.replication.util.CaseInsensitiveMap;
import scala.xml.dtd.ANY;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class TableSchemaVersion {

    private final Map<String,ColumnSchema> columns = new CaseInsensitiveMap<>();

    private final Map<Integer,String> columnIndexToColumnNameMap = new HashMap<>();
    private final Map<Integer,String> columnIndexToNameMap = new HashMap<>();
    private final Map<String,Map<String,String>> columnsSchema = new HashMap<>();

    private final String tableSchemaVersionUUID;

    // TODO: load table CHARACTER_SET_NAME
    // private String CHARACTER_SET_NAME;

    public TableSchemaVersion() {
        tableSchemaVersionUUID = UUID.randomUUID().toString();;
    }

    public void addColumn(ColumnSchema columnSchema) {
        String columnName = columnSchema.getColumnName();
        this.columns.put(columnName, columnSchema);

        // update the indexToNameMap
        Integer index = columnSchema.getOrdinalPosition();
        this.columnIndexToColumnNameMap.put(index, columnName);
        this.columnIndexToNameMap.put(index, columnName);

        Map<String, String> feature = new HashMap<>();

        feature.put("columnType", columnSchema.getColumnType());
        feature.put("columnKey", columnSchema.getColumnKey());
        feature.put("dataType", columnSchema.getDataType());
        feature.put("nullAble", columnSchema.getIsNullAble());
        feature.put("characterSetName", columnSchema.getCharacterSetName());
        feature.put("characterMaximumLength", columnSchema.getCharacterMaximumLength());
        this.columnsSchema.put(columnName, feature);

    }

    public ColumnSchema getColumnSchemaByColumnName(String columnName) {
        return this.columns.get(columnName);
    }

    public ColumnSchema getColumnSchemaByColumnIndex(Integer columnIndex) {
        String columnName = columnIndexToColumnNameMap.get(columnIndex);
        return columns.get(columnName);
    }

    public Set<String> getColumnNames() {
        return Collections.unmodifiableSet(columns.keySet());
    }

    public Map<Integer,String> getColumnIndexToColumnNameMap() {
        return columnIndexToColumnNameMap;
    }

    public Map<Integer,String> getColumnIndexToNameMap() {
        return columnIndexToNameMap;
    }

    public Map<String,Map<String, String>> getcolumnsSchema() {
        return columnsSchema;
    }


}
