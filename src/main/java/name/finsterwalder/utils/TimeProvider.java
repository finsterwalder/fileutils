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

package name.finsterwalder.utils;

import java.io.Serializable;
import java.util.Date;


/**
 * A TimeProvider can serve the current Time as a Date or long value.
 * Allows for faking of time during tests.
 *
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public interface TimeProvider extends Serializable {

	/**
	 * @return The current date (usually "new Date()")
	 */
	Date getDate();

	/**
	 * @return The current time in milliseconds (usually System.currentTimeMillis())
	 */
	long getTime();
}
