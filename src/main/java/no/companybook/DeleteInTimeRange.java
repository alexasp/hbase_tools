package no.companybook;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class DeleteInTimeRange {
    private static void configure(Configuration config, String quorum, String port, String parent) {
        config.set("hbase.zookeeper.property.clientPort", port);
        config.set("hbase.zookeeper.quorum", quorum);
        config.set("zookeeper.znode.parent", parent);
    }

    public static void main (String[] args) throws IOException {
        String quorum = args[0];
        String port = args[1];
        String parent = args[2];
        byte[] familyName = Bytes.toBytes(args[3]);
        if (familyName.length < 1) {
            System.out.println("Empty column family is not allowed. Please specify column family");
            System.exit(1);
        }
        String rawColumns = args[4];
        if (rawColumns.length() < 1) {
            System.out.println("Deleting whole column families is not allowed. You need to specify columns");
            System.exit(2);
        }
        List<String> columnNames = Arrays.stream(rawColumns.split(","))
                .map(cn -> cn.replaceAll("\\s+", ""))
                .collect(Collectors.toList());
        String tableName = args[5];
        long minRequiredTimestamp = Long.parseLong(args[6]);
        String startRow = args[7];
        String stopRow = args[8];

        Configuration config = new Configuration();
        configure(config, quorum, port, parent);
        Connection connection = ConnectionFactory.createConnection(config);
        Table table = connection.getTable(TableName.valueOf(tableName));
        Scan scan = new Scan();
        scan.setMaxVersions(1);
        scan.setStartRow(Bytes.toBytes(startRow));
        scan.setStopRow(Bytes.toBytes(stopRow));
        for (String columnName: columnNames) {
            scan.addColumn(familyName, Bytes.toBytes(columnName));
        }

        ResultScanner scanner = table.getScanner(scan);
        try {
            for (Result result = scanner.next(); result != null; result = scanner.next()) {
                Delete delete = new Delete(result.getRow());
                System.out.println("Row: " + Bytes.toString(result.getRow()));

                boolean performDelete = false;

                for (String columnName: columnNames) {
                    Cell value = result.getColumnLatestCell(familyName, Bytes.toBytes(columnName));
                    long timestamp = value.getTimestamp();
                    System.out.println("Column: " + columnName + ", Timestamp: " + timestamp);
                    if (timestamp < minRequiredTimestamp) {
                        performDelete = true;
                        delete.addColumns(familyName, Bytes.toBytes(columnName));
                        System.out.println("Deleting.");
                    }
                }

                if (performDelete) {
                    table.delete(delete);
                }
            }

        } finally {
            scanner.close();
            table.close();
        }
    }
}
