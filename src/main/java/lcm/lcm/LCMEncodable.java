// code by lcm
package lcm.lcm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** A message which can be easily sent using LCM. */
public interface LCMEncodable {
  /** LCMEncodables will always have an empty constructor and a constructor
   * that takes a DataInput. **/
  /** Invoked by LCM.
   * 
   * @param outs
   * Any data to be sent should be written to this output stream. */
  void encode(DataOutput outs) throws IOException;

  /** Encode the data without the magic header. Most users will never use this
   * function. **/
  void _encodeRecursive(DataOutput outs) throws IOException;

  /** Decode the data without the magic header. Most users will never use this
   * function. **/
  void _decodeRecursive(DataInput ins) throws IOException;
}
