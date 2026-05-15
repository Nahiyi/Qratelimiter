package cn.clazs.qratelimiter.stress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class StressTestSupport {

    private StressTestSupport() {
    }

    static int runConcurrentAttempts(Callable<Boolean> attempt, int attempts) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();

        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executorService.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return attempt.call();
                    }
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            return allowed;
        } finally {
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
