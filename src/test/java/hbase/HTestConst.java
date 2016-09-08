package hbase;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.util.Bytes;


public class HTestConst {

  private HTestConst() {
  }

  public static final String DEFAULT_TABLE_STR = "MyTestTable";
  public static final byte[] DEFAULT_TABLE_BYTES = Bytes.toBytes(DEFAULT_TABLE_STR);
  public static final TableName DEFAULT_TABLE =
      TableName.valueOf(DEFAULT_TABLE_BYTES);

  public static final String DEFAULT_CF_STR = "MyDefaultCF";
  public static final byte[] DEFAULT_CF_BYTES = Bytes.toBytes(DEFAULT_CF_STR);

  public static final String DEFAULT_ROW_STR = "MyTestRow";
  public static final byte[] DEFAULT_ROW_BYTES = Bytes.toBytes(DEFAULT_ROW_STR);

  public static final String DEFAULT_QUALIFIER_STR = "MyColumnQualifier";
  public static final byte[] DEFAULT_QUALIFIER_BYTES = Bytes.toBytes(DEFAULT_QUALIFIER_STR);

  public static String DEFAULT_VALUE_STR = "MyTestValue";
  public static byte[] DEFAULT_VALUE_BYTES = Bytes.toBytes(DEFAULT_VALUE_STR);

  /**
   * Generate the given number of unique byte sequences by appending numeric
   * suffixes (ASCII representations of decimal numbers).
   */
  public static byte[][] makeNAscii(byte[] base, int n) {
    byte [][] ret = new byte[n][];
    for (int i = 0; i < n; i++) {
      byte[] tail = Bytes.toBytes(Integer.toString(i));
      ret[i] = Bytes.add(base, tail);
    }
    return ret;
  }

}
