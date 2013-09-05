package name.finsterwalder.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * @author mfinsterwalder
 * @since 2013-09-04 18:24
 */
public class ScheduledExecutorDefaultImpl implements ScheduledExecutor {
	private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	@Override
	public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
		return executorService.schedule(command, delay, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
		return executorService.scheduleAtFixedRate(command, initialDelay, period, unit);
	}
}
