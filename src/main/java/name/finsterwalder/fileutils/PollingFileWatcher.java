/*
 * fileutils - A simple FileWatcher utility
 * Copyright (C) 2013 Malte Finsterwalder
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

import name.finsterwalder.utils.Ensure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Watch a single file for changes. Uses a polling approach, that checks the modified timestamp of the file in a regular interval.
 * Whenever a change in the modified timestamp is detected, a delayed notification is triggered. This ensures a grace period
 * which allows the file to be written completely, before the notification about the change is issued.
 *
 * File modifications often first truncate a file and then write the file new. Without a grace period, a notified
 * FileChangeListener with unfortunate timing might see the empty file. When the write is fast and the timestamp granularity
 * is rather low, the writing of the file does not even change the modified timestamp of the file again and the completely
 * written file is never notified. The grace period should be at least as large as the timestamp granularity of the underlying
 * file system.
 *
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class PollingFileWatcher implements FileWatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(PollingFileWatcher.class);
	public static final int DEFAULT_RELOAD_INTERVAL_IN_MS = 500;
	public static final int DEFAULT_GRACE_PERIOD_IN_MS = 1000;

	private final ScheduledExecutorService executorService;
	private final Path path;
	private final FileChangeListener fileChangeListener;
	private final long gracePeriodInMs;
	private volatile FileTime lastSeen;
	private volatile boolean changed = false;

	private final Thread shutdownHook = new Thread() {
		@Override
		public void run() {
			executorService.shutdownNow();
		}
	};

	/**
	 * Create a PollingFileWatcher with a default reload interval of 500ms and a default grace period of 1000ms.
	 * @param filenameOfFileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public PollingFileWatcher(final String filenameOfFileToWatch, final FileChangeListener fileChangeListener) {
		this(Paths.get(filenameOfFileToWatch), fileChangeListener, DEFAULT_RELOAD_INTERVAL_IN_MS, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	/**
	 * Create a PollingFileWatcher with a default reload interval of 500ms and a default grace period of 1000ms.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public PollingFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener) {
		this(fileToWatch.toPath(), fileChangeListener, DEFAULT_RELOAD_INTERVAL_IN_MS, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	/**
	 * Create a PollingFileWatcher with a default reload interval of 500ms and a default grace period of 1000ms.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public PollingFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener) {
		this(fileToWatch, fileChangeListener, DEFAULT_RELOAD_INTERVAL_IN_MS, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	/**
	 * Create a PollingFileWatcher with the given reload interval and a default grace period of 1000ms.
	 * @param filenameOfFileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 * @param reloadIntervalInMs Reload interval in ms
	 */
	public PollingFileWatcher(final String filenameOfFileToWatch, final FileChangeListener fileChangeListener, final long reloadIntervalInMs) {
		this(Paths.get(filenameOfFileToWatch), fileChangeListener, reloadIntervalInMs, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	/**
	 * Create a PollingFileWatcher with the given reload interval and a default grace period of 1000ms.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 * @param reloadIntervalInMs Reload interval in ms
	 */
	public PollingFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener, long reloadIntervalInMs) {
		this(fileToWatch.toPath(), fileChangeListener, reloadIntervalInMs, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	/**
	 * Create a PollingFileWatcher with the given reload interval and a default grace period of 1000ms.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 * @param reloadIntervalInMs Reload interval in ms
	 */
	public PollingFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener, long reloadIntervalInMs) {
		this(fileToWatch, fileChangeListener, reloadIntervalInMs, DEFAULT_GRACE_PERIOD_IN_MS, Executors.newSingleThreadScheduledExecutor());
	}

	public PollingFileWatcher(final String filename, final FileChangeListener fileChangeListener, final long reloadIntervalInMs, final long gracePeriodInMs) {
		this(Paths.get(filename), fileChangeListener, reloadIntervalInMs, gracePeriodInMs, Executors.newSingleThreadScheduledExecutor());
	}

	/**
	 * Create a PollingFileWatcher with the given reload interval and the given grace period.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 * @param reloadIntervalInMs Reload interval in ms
	 * @param gracePeriodInMs Grace period in ms to wait after a change in the file before sending an update to the FileChangeListener
	 */
	public PollingFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener, long reloadIntervalInMs, final long gracePeriodInMs) {
		this(fileToWatch.toPath(), fileChangeListener, reloadIntervalInMs, gracePeriodInMs, Executors.newSingleThreadScheduledExecutor());
	}

	/**
	 * Create a PollingFileWatcher with the given reload interval and the given grace period.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 * @param reloadIntervalInMs Reload interval in ms
	 * @param gracePeriodInMs Grace period in ms to wait after a change in the file before sending an update to the FileChangeListener
	 */
	public PollingFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener, long reloadIntervalInMs, final long gracePeriodInMs) {
		this(fileToWatch, fileChangeListener, reloadIntervalInMs, gracePeriodInMs, Executors.newSingleThreadScheduledExecutor());
	}

	/*package*/ PollingFileWatcher(final Path path, final FileChangeListener fileChangeListener, final long reloadIntervalInMs, final long gracePeriodInMs,
								   ScheduledExecutorService executorService) {
		Ensure.notNull(path, "path");
		Ensure.notNull(fileChangeListener, "fileChangeListener");
		Ensure.notNull(executorService, "executorService");
		Ensure.that(reloadIntervalInMs > 0, "reload interval > 0");
		Ensure.that(gracePeriodInMs >= 0, "grace period >= 0");
		this.executorService = executorService;
		this.path = path;
		this.gracePeriodInMs = gracePeriodInMs;
		this.fileChangeListener = fileChangeListener;
		changed(); //initiate lastSeen timestamp
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		this.executorService.scheduleAtFixedRate(new ChangeWatcher(this), reloadIntervalInMs, reloadIntervalInMs, TimeUnit.MILLISECONDS);
	}

	synchronized private boolean changed() {
		try {
			FileTime lastModified = Files.getLastModifiedTime(path);
			if (lastSeen == null || lastModified.compareTo(lastSeen) > 0) {
				lastSeen = lastModified;
				return true;
			}
			return false;
		} catch (IOException e) {
			return (lastSeen != null);
		}
	}

	/**
	 * Stop watching the file. Once stoped, watching can not be started again. A new PollingFileWatcher needs to be created when the file
	 * needs to be watched again.
	 */
	@Override
	public void unwatch() {
		Runtime.getRuntime().removeShutdownHook(shutdownHook);
		executorService.shutdownNow();
	}

	@Override
	protected void finalize() throws Throwable {
		unwatch();
		super.finalize();
	}

	/*package*/ static class ChangeWatcher implements Runnable {

		private final PollingFileWatcher watcher;

		public ChangeWatcher(final PollingFileWatcher watcher) {
			this.watcher = watcher;
		}

		@Override
		public void run() {
			try {
				synchronized (watcher) {
					if (!watcher.changed && (watcher.changed = watcher.changed())) {
						if (watcher.gracePeriodInMs > 0) {
							// Schedule a delayed notify after the grace period
							watcher.executorService.schedule(new DelayedNotifier(watcher), watcher.gracePeriodInMs, TimeUnit.MILLISECONDS);
						} else {
							watcher.fileChangeListener.fileChanged();
						}
					}
				}
			} catch (Exception e) {
				LOGGER.warn("PollingFileWatcher could not check file {}.", watcher.path, e);
			}
		}
	}

	/*package*/ static class DelayedNotifier implements Runnable {

		private final PollingFileWatcher watcher;

		public DelayedNotifier(final PollingFileWatcher watcher) {
			this.watcher = watcher;
		}

		@Override
		public void run() {
			try {
				synchronized (watcher) {
					if (watcher.changed()) {
						// File changed again. Schedule another grace period
						watcher.executorService.schedule(this, watcher.gracePeriodInMs, TimeUnit.MILLISECONDS);
					} else {
						// File didn't change again. Notify!
						watcher.changed = false;
						watcher.fileChangeListener.fileChanged();
					}
				}
			} catch (Exception e) {
				LOGGER.warn("PollingFileWatcher could not check file {}.", watcher.path, e);
			}
		}
	}
}
