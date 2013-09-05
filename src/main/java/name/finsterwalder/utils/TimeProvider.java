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
