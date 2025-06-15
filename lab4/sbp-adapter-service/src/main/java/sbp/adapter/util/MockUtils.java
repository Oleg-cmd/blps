package sbp.adapter.util;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MockUtils {

  private MockUtils() {
    // Утилитарный класс не должен инстанцироваться
  }

  private static final Random random = ThreadLocalRandom.current();

  public static void simulateNetworkDelay(long minMillis, long maxMillis) {
    try {
      if (minMillis <= 0 && maxMillis <= 0) return;
      long actualMin = Math.max(0, minMillis);
      long actualMax = Math.max(actualMin, maxMillis);

      if (actualMin >= actualMax) {
        if (actualMin > 0) Thread.sleep(actualMin);
        return;
      }
      long delay = actualMin + random.nextLong(actualMax - actualMin + 1);
      if (delay > 0) {
        Thread.sleep(delay);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("SBP Adapter: Network delay simulation interrupted");
    }
  }

  public static int getRandomInt(int bound) {
    return random.nextInt(bound);
  }
}
