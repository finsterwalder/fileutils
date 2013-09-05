/*
 * Created on 13.10.2010 by mfinsterwalder
 */
package name.finsterwalder.utils;

import java.util.Collection;


/**
 * A simple Design-by-contract helper class to make parameter checks explicit.
 *
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class Ensure {

	/**
	 * Check that the provided object is not null.
	 *
	 * @param obj Object to check
	 * @param parameterName name of the parameter to display in the error message
	 */
	public static void notNull(final Object obj, final String parameterName) {
		if (obj == null) {
			throw new IllegalArgumentException("\"" + parameterName + "\" must not be null");
		}
	}

	/**
	 * Check that the provided object is null.
	 *
	 * @param obj Object to check
	 * @param parameterName name of the parameter to display in the error message
	 */
	public static void isNull(final Object obj, final String parameterName) {
		if (obj != null) {
			throw new IllegalArgumentException("\"" + parameterName + "\" must be null");
		}
	}

	/**
	 * Check that the provided string is not empty (not null, not "").
	 *
	 * @param str string to check
	 * @param parameterName name of the parameter to display in the error message
	 */
	public static void notEmpty(final String str, final String parameterName) {
		if (str == null || str.length() == 0) {
			throw new IllegalArgumentException("\"" + parameterName + "\" must not be null or empty, but was: " + str);
		}
	}

	/**
	 * Check that the provided Collection is not empty).
	 *
	 * @param collection collection to check
	 * @param parameterName name of the parameter to display in the error message
	 */
	public static void notEmpty(final Collection<?> collection, final String parameterName) {
		if (collection.isEmpty()) {
			throw new IllegalArgumentException("\"" + parameterName + "\" must not be null or empty, but was: " + collection);
		}
	}

	/**
	 * Check, that the given condition is true
	 *
	 * @param cond condition to evaluate
	 * @param conditionName name to display in the error message
	 */
	public static void that(final boolean cond, final String conditionName) {
		if (!cond) {
			throw new IllegalArgumentException("\"" + conditionName + "\" must be true");
		}
	}
}
