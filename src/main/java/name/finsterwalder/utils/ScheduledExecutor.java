package name.finsterwalder.utils;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public interface ScheduledExecutor {
	ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

	ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);
}
