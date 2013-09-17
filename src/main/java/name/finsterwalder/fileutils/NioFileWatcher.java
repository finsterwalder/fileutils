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
import name.finsterwalder.utils.SimpleDateTimeProvider;
import name.finsterwalder.utils.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;


/**
 * Watch a single file for changes. Uses the Java NIO WatchService. The WatchService is called by the underlying operating system
 * when a file is changed.
 * This mechanismus needs operating system support and might not work in all environments.
 * Also I made the observation, that it does not work with NFS mounted file systems.
 * Whenever a change in a file is detected, a delayed notification is triggered. This ensures a grace period
 * which allows the file to be modified completely, before the notification about the change is issued.
 *
 * File modifications often first truncate a file and then write the file new. Without a grace period, several notifications
 * will be issued for a single change. The grace period reduces the amount of notifications.
 *
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class NioFileWatcher implements FileWatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(NioFileWatcher.class);
	private static final int DEFAULT_GRACE_PERIOD = 1000;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private PollingFileWatcher pollingFileWatcher;
	private WatchService watchService;
	private final Path absoluteFileToWatch;
	private volatile long lastChanged;
	private volatile long lastProcessed;
	private final TimeProvider timeProvider;

	/**
	 * Create a NioFileWatcher with a default grace period of 1000ms.
	 * @param filenameOfFileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public NioFileWatcher(final String filenameOfFileToWatch, final FileChangeListener fileChangeListener) {
		this(Paths.get(filenameOfFileToWatch), fileChangeListener, DEFAULT_GRACE_PERIOD, new SimpleDateTimeProvider());
	}

	/**
	 * Create a NioFileWatcher with a default grace period of 1000ms.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public NioFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener) {
		this(fileToWatch.toPath(), fileChangeListener, DEFAULT_GRACE_PERIOD, new SimpleDateTimeProvider());
	}

	/**
	 * Create a NioFileWatcher with a default grace period of 1000ms.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public NioFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener) {
		this(fileToWatch, fileChangeListener, DEFAULT_GRACE_PERIOD, new SimpleDateTimeProvider());
	}

	/**
	 * Create a NioFileWatcher with the given grace period.
	 * @param filenameOfFileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public NioFileWatcher(final String filenameOfFileToWatch, final FileChangeListener fileChangeListener, long gracePeriodInMs) {
		this(FileSystems.getDefault().getPath(filenameOfFileToWatch), fileChangeListener, gracePeriodInMs, new SimpleDateTimeProvider());
	}

	/**
	 * Create a NioFileWatcher with the given grace period.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public NioFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener, long gracePeriodInMs) {
		this(fileToWatch.toPath(), fileChangeListener, gracePeriodInMs, new SimpleDateTimeProvider());
	}

	/**
	 * Create a NioFileWatcher with the given grace period.
	 * @param fileToWatch File to watch
	 * @param fileChangeListener Listener to notify about changes
	 */
	public NioFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener, long gracePeriodInMs) {
		this(fileToWatch, fileChangeListener, gracePeriodInMs, new SimpleDateTimeProvider());
	}

	/*package*/ NioFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener, final long gracePeriodInMs, TimeProvider timeProvider) {
		Ensure.notNull(fileToWatch, "fileToWatch");
		Ensure.notNull(fileChangeListener, "fileChangeListener");
		Ensure.notNull(timeProvider, "timeProvider");
		this.timeProvider = timeProvider;
		absoluteFileToWatch = fileToWatch.toAbsolutePath();
		final Path directoryPath = absoluteFileToWatch.getParent();
		if (directoryPath == null) {
			throw new IllegalArgumentException("File does not have a parent directory: " + absoluteFileToWatch);
		}
		if (Files.exists(directoryPath)) {
			initWatcher(directoryPath, fileChangeListener, gracePeriodInMs);
		} else {
			pollingFileWatcher = new PollingFileWatcher(absoluteFileToWatch, new FileChangeListener() {
				@Override
				public void fileChanged() {
					pollingFileWatcher.unwatch();
					pollingFileWatcher = null;
					initWatcher(directoryPath, fileChangeListener, gracePeriodInMs);
					fileChangeListener.fileChanged();
				}
			}, PollingFileWatcher.DEFAULT_RELOAD_INTERVAL_IN_MS, gracePeriodInMs);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		unwatch();
		super.finalize();
	}

	private void initWatcher(final Path directoryPath, final FileChangeListener fileChangeListener, final long gracePeriod) {
		final Path filenamePath = absoluteFileToWatch.getFileName();
		try {
			watchService = directoryPath.getFileSystem().newWatchService();
			directoryPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
			new Thread() {
				@Override
				public void run() {
					boolean valid = true;
					try {
						while (valid && !isInterrupted()) {
							WatchKey takenWatchKey = watchService.take();
							for (WatchEvent<?> event : takenWatchKey.pollEvents()) {
								if (OVERFLOW != event.kind()) {
									Path context = (Path)event.context();
									if (filenamePath.equals(context)) {
										notifyChangeListener(fileChangeListener, gracePeriod);
									}
								}
							}
							valid = takenWatchKey.reset();
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (ClosedWatchServiceException e) {
						// nothing to do, since polling loop is already cancelled
					} finally {
						close(watchService, absoluteFileToWatch);
					}
				}

				private void notifyChangeListener(final FileChangeListener fileChangeListener, final long gracePeriod) {
					if (gracePeriod > 0) {
						final long changeTimestamp = timeProvider.getTime();
						synchronized (NioFileWatcher.this) {
							lastChanged = changeTimestamp;
						}
						scheduler.schedule(new Runnable() {

							@Override
							public void run() {
								synchronized (NioFileWatcher.this) {
									if (changeTimestamp == lastChanged && lastProcessed < lastChanged) {
										lastProcessed = lastChanged;
										fileChangeListener.fileChanged();
									}
								}
							}
						}, gracePeriod, TimeUnit.MILLISECONDS);
					} else {
						fileChangeListener.fileChanged();
					}
				}
			}.start();
		} catch (Exception e) {
			throw new RuntimeException("Could not initialize file watcher for " + absoluteFileToWatch.toAbsolutePath(), e);
		}
	}

	@Override
	public void unwatch() {
		close(watchService, absoluteFileToWatch);
		if (pollingFileWatcher != null) {
			pollingFileWatcher.unwatch();
			pollingFileWatcher = null;
		}
	}

	private static void close(final WatchService watchService, final Path fileToWatch) {
		if (watchService != null) {
			try {
				watchService.close();
			} catch (IOException e) {
				LOGGER.info("Could not close file watcher for {}", fileToWatch.toAbsolutePath(), e);
			}
		}
	}
}
