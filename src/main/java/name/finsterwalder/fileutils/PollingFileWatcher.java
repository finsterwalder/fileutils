/*
 * fileutils - A simple FileWatcher utility
 * Copyright (C) 2013 PARSHIP GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

	public PollingFileWatcher(final String filename, final FileChangeListener fileChangeListener) {
		this(new File(filename), fileChangeListener, DEFAULT_RELOAD_INTERVAL_IN_MS, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final File file, final FileChangeListener fileChangeListener) {
		this(file, fileChangeListener, DEFAULT_RELOAD_INTERVAL_IN_MS, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final String filename, final FileChangeListener fileChangeListener, final long reloadIntervalInMs, final long gracePeriod) {
		this(new File(filename), fileChangeListener, reloadIntervalInMs, gracePeriod, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener, long reloadIntervalInMs, final long gracePeriod) {
		this(fileToWatch, fileChangeListener, reloadIntervalInMs, gracePeriod, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final File file, final FileChangeListener fileChangeListener, final long reloadIntervalInMs, final long gracePeriod, ScheduledExecutorService executorService) {
		this.executorService = executorService;
		this.file = file;
		this.gracePeriod = gracePeriod;
		this.fileChangeListener = fileChangeListener;
		lastModifiedSeen = file.lastModified();
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		this.executorService.scheduleAtFixedRate(new ChangeWatcher(this), reloadIntervalInMs, reloadIntervalInMs, TimeUnit.MILLISECONDS);
	}

	synchronized private boolean changed() {
		long lastModified = file.lastModified();
		if (lastModified > lastModifiedSeen || lastModified == 0 && lastModifiedSeen != 0) {
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

	/*package*/ static class ChangeWatcher implements Runnable {

		private PollingFileWatcher watcher;

		public ChangeWatcher(final PollingFileWatcher watcher) {
			this.watcher = watcher;
		}

		@Override
		public void run() {
			try {
				synchronized (watcher) {
					if (!watcher.changed && (watcher.changed = watcher.changed())) {
						// Schedule a delayed notify after the grace period
						watcher.executorService.schedule(new DelayedNotifier(watcher), watcher.gracePeriod, TimeUnit.MILLISECONDS);
					}
				}
			} catch (Exception e) {
				LOGGER.warn("PollingFileWatcher could not check file {}.", watcher.file.getAbsolutePath(), e);
			}
		}
	}

	/*package*/ static class DelayedNotifier implements Runnable {

		private PollingFileWatcher watcher;

		public DelayedNotifier(final PollingFileWatcher watcher) {
			this.watcher = watcher;
		}

		@Override
		public void run() {
			synchronized (watcher) {
				if (watcher.changed()) {
					// File changed again. Schedule another grace period
					watcher.executorService.schedule(this, watcher.gracePeriod, TimeUnit.MILLISECONDS);
				} else {
					// File didn't change again. Notify!
					watcher.changed = false;
					watcher.fileChangeListener.fileChanged();
				}
			}
		}
	}
}
