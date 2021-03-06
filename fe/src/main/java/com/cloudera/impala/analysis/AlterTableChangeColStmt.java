// Copyright 2012 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cloudera.impala.analysis;

import org.apache.hadoop.hive.metastore.api.FieldSchema;

import com.cloudera.impala.catalog.AuthorizationException;
import com.cloudera.impala.catalog.HBaseTable;
import com.cloudera.impala.catalog.Table;
import com.cloudera.impala.common.AnalysisException;
import com.cloudera.impala.thrift.TAlterTableChangeColParams;
import com.cloudera.impala.thrift.TAlterTableParams;
import com.cloudera.impala.thrift.TAlterTableType;
import com.google.common.base.Preconditions;

/**
 * Represents an ALTER TABLE CHANGE COLUMN colName newColDef statement.
 * Note: It would be fairly simple to reuse this class to support ALTER TABLE MODIFY
 * newColDef statements in the future my making colName optional.
 */
public class AlterTableChangeColStmt extends AlterTableStmt {
  private final String colName;
  private final ColumnDesc newColDef;

  public AlterTableChangeColStmt(TableName tableName, String colName,
      ColumnDesc newColDef) {
    super(tableName);
    Preconditions.checkNotNull(newColDef);
    Preconditions.checkState(colName != null && !colName.isEmpty());
    this.colName = colName;
    this.newColDef = newColDef;
  }

  public String getColName() {
    return colName;
  }

  public ColumnDesc getNewColDef() {
    return newColDef;
  }

  @Override
  public TAlterTableParams toThrift() {
    TAlterTableParams params = super.toThrift();
    params.setAlter_type(TAlterTableType.CHANGE_COLUMN);
    TAlterTableChangeColParams colParams = new TAlterTableChangeColParams();
    colParams.setCol_name(colName);
    colParams.setNew_col_def(newColDef.toThrift());
    params.setChange_col_params(colParams);
    return params;
  }

  @Override
  public void analyze(Analyzer analyzer) throws AnalysisException,
      AuthorizationException {
    super.analyze(analyzer);
    Table t = getTargetTable();
    // TODO: Support column-level DDL on HBase tables. Requires updating the column
    // mappings along with the table columns.
    if (t instanceof HBaseTable) {
      throw new AnalysisException("ALTER TABLE CHANGE COLUMN not currently supported " +
          "on HBase tables.");
    }
    String tableName = getDb() + "." + getTbl();

    // Verify there are no conflicts with partition columns.
    for (FieldSchema fs: t.getMetaStoreTable().getPartitionKeys()) {
      if (fs.getName().toLowerCase().equals(colName.toLowerCase())) {
        throw new AnalysisException("Cannot modify partition column: " + colName);
      }
      if (fs.getName().toLowerCase().equals(newColDef.getColName().toLowerCase())) {
        throw new AnalysisException(
            "Column name conflicts with existing partition column: " +
            newColDef.getColName());
      }
    }

    // Verify the column being modified exists in the table
    if (t.getColumn(colName) == null) {
      throw new AnalysisException(String.format(
          "Column '%s' does not exist in table: %s", colName, tableName));
    }

    // Check that the new column def's name is valid.
    newColDef.analyze();
    // Verify that if the column name is being changed, the new name doesn't conflict
    // with an existing column.
    if (!colName.toLowerCase().equals(newColDef.getColName().toLowerCase()) &&
        t.getColumn(newColDef.getColName()) != null) {
      throw new AnalysisException("Column already exists: " + newColDef.getColName());
    }
  }
}
