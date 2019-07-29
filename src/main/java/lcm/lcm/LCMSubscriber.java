// code by lcm
package lcm.lcm;

/** A class which listens for messages on a particular channel. */
@FunctionalInterface
public interface LCMSubscriber {
  /** Invoked by LCM when a message is received.
   *
   * This method is invoked from the LCM thread.
   *
   * @param lcm
   * the LCM instance that received the message.
   * @param channel
   * the channel on which the message was received.
   * @param ins
   * the message contents. */
  void messageReceived(LCM lcm, String channel, LCMDataInputStream ins);
}
