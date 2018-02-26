// code by jph
package lcm.logging;

import java.io.File;
import java.io.IOException;
import java.net.URL;

enum LogPlayerDemo {
  ;
  public static void main(String[] args) throws IOException, Exception {
    URL url = LogPlayerDemo.class.getResource("/log/lcmlog-2017-08-24.04");
    File file = new File(url.getFile());
    System.out.println(file.exists());
    // ---
    LogPlayerConfig cfg = new LogPlayerConfig();
    cfg.logFile = url.getFile();
    cfg.speed_numerator = 1;
    cfg.speed_denominator = 64;
    LogPlayer lp = LogPlayer.create(cfg);
    Thread.sleep(3000);
    lp.close();
  }
}
