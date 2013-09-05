package name.finsterwalder.fileutils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class PollingFileWatcher implements FileWatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(PollingFileWatcher.class);
	private static final int DEFAULT_RELOAD_INTERVAL_IN_MS = 500;
	private static final int DEFAULT_GRACE_PERIOD_IN_MS = 1000;

	private final ScheduledExecutorService executorService;
	private final File file;
	private final FileChangeListener fileChangeListener;
	private final long gracePeriod;
	private volatile long lastModifiedSeen;
	private volatile boolean changed = false;

	private final Thread shutdownHook = new Thread() {
		@Override
		public void run() {
			executorService.shutdownNow();
		}
	};

	public static FileWatcher watch(final String fileToWatch, final FileChangeListener fileChangeListener, long reloadIntervalInMs, final long gracePeriod) {
		return watch(new File(fileToWatch), fileChangeListener, reloadIntervalInMs, gracePeriod);
	}

	public static FileWatcher watch(final File fileToWatch, final FileChangeListener fileChangeListener, long reloadIntervalInMs, final long gracePeriod) {
		return new PollingFileWatcher(fileToWatch, fileChangeListener, reloadIntervalInMs, gracePeriod, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final String filename, final FileChangeListener fileChangeListener) {
		this(new File(filename), fileChangeListener, DEFAULT_RELOAD_INTERVAL_IN_MS, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final File file, final FileChangeListener fileChangeListener) {
		this(file, fileChangeListener, DEFAULT_RELOAD_INTERVAL_IN_MS, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final String filename, final FileChangeListener fileChangeListener, final long reloadIntervalInMs, final long gracePeriod) {
		this(new File(filename), fileChangeListener, reloadIntervalInMs, gracePeriod, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final File file, final FileChangeListener fileChangeListener, final long reloadIntervalInMs, final long gracePeriod, ScheduledExecutorService executorService) {
		this.executorService = executorService;
		this.file = file;
		this.gracePeriod = gracePeriod;
		this.fileChangeListener = fileChangeListener;
		lastModifiedSeen = file.lastModified();
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		this.executorService.scheduleAtFixedRate(new ChangeWatcher(), reloadIntervalInMs, reloadIntervalInMs, TimeUnit.MILLISECONDS);
	}

	synchronized private boolean changed() {
		long lastModified = file.lastModified();
		if (lastModified > lastModifiedSeen) {
			lastModifiedSeen = lastModified;
			return true;
		}
		return false;
	}

	@Override
	public void unwatch() {
		Runtime.getRuntime().removeShutdownHook(shutdownHook);
		executorService.shutdownNow();
	}

	@Override
	protected void finalize() throws Throwable {
		unwatch();
	}

	public class ChangeWatcher implements Runnable {

		@Override
		public void run() {
			try {
				synchronized (PollingFileWatcher.this) {
					if (!changed && (changed = changed())) {
						// Schedule a delayed notify after the grace period
						executorService.schedule(new DelayedNotifier(), gracePeriod, TimeUnit.MILLISECONDS);
					}
				}
			} catch (Exception e) {
				LOGGER.warn("PollingFileWatcher could not check file {}.", file.getAbsolutePath(), e);
			}
		}
	}

	private class DelayedNotifier implements Runnable {
		@Override
		public void run() {
			synchronized (PollingFileWatcher.this) {
				if (changed()) {
					// File changed again. Schedule another grace period
					executorService.schedule(this, gracePeriod, TimeUnit.MILLISECONDS);
				} else {
					// File didn't change again. Notify!
					changed = false;
					fileChangeListener.fileChanged();
				}
			}
		}
	}
}
