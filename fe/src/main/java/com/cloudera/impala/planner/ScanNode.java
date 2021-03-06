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

package com.cloudera.impala.planner;

import java.util.List;

import com.cloudera.impala.analysis.SlotDescriptor;
import com.cloudera.impala.analysis.TupleDescriptor;
import com.cloudera.impala.thrift.TExplainLevel;
import com.cloudera.impala.thrift.TNetworkAddress;
import com.cloudera.impala.thrift.TScanRangeLocations;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

/**
 * Representation of the common elements of all scan nodes.
 */
abstract public class ScanNode extends PlanNode {
  protected final TupleDescriptor desc;

  public ScanNode(PlanNodeId id, TupleDescriptor desc, String displayName) {
    super(id, desc.getId().asList(), displayName);
    this.desc = desc;
  }

  /**
   * Returns all scan ranges plus their locations. Needs to be preceded by a call to
   * finalize().
   * @param maxScanRangeLength The maximum number of bytes each scan range should scan;
   *     only applicable to HDFS; less than or equal to zero means no maximum.
   */
  abstract public List<TScanRangeLocations> getScanRangeLocations(long maxScanRangeLength);

  @Override
  protected String debugString() {
    return Objects.toStringHelper(this)
        .add("tid", desc.getId().asInt())
        .add("tblName", desc.getTable().getFullName())
        .add("keyRanges", "")
        .addValue(super.debugString())
        .toString();
  }

  /**
   * Returns the explain string for table and columns stats to be included into the
   * a ScanNode's explain string. The given prefix is prepended to each of the lines.
   * The prefix is used for proper formatting when the string returned by this method
   * is embedded in a query's explain plan.
   */
  protected String getStatsExplainString(String prefix, TExplainLevel detailLevel) {
    StringBuilder output = new StringBuilder();
    // Table stats.
    if (desc.getTable().getNumRows() == -1) {
      output.append(prefix + "table stats: unavailable");
    } else {
      output.append(prefix + "table stats: " + desc.getTable().getNumRows() +
          " rows total");
    }
    output.append("\n");

    // Column stats.
    List<String> columnsMissingStats = Lists.newArrayList();
    for (SlotDescriptor slot: desc.getSlots()) {
      if (!slot.getStats().hasStats()) {
        columnsMissingStats.add(slot.getColumn().getName());
      }
    }
    if (columnsMissingStats.isEmpty()) {
      output.append(prefix + "column stats: all");
    } else if (columnsMissingStats.size() == desc.getSlots().size()) {
      output.append(prefix + "column stats: unavailable");
    } else {
      output.append(String.format("%scolumns missing stats: %s", prefix,
          Joiner.on(", ").join(columnsMissingStats)));
    }
    return output.toString();
  }

  /**
   * Helper function to parse a "host:port" address string into TNetworkAddress
   * This is called with ipaddress:port when doing scan range assigment.
   */
  protected static TNetworkAddress addressToTNetworkAddress(String address) {
    TNetworkAddress result = new TNetworkAddress();
    String[] hostPort = address.split(":");
    result.hostname = hostPort[0];
    result.port = Integer.parseInt(hostPort[1]);
    return result;
  }

}
