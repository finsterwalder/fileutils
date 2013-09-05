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

/**
 * A FileWatcher watches a single file for changes. Watching the file can be stopped.
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public interface FileWatcher {
	/**
	 * Stop watching the file. Once stoped, watching can not be started again. A new PollingFileWatcher needs to be created when the file
	 * needs to be watched again.
	 */
	void unwatch();
}
