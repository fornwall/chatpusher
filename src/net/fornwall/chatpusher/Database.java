package net.fornwall.chatpusher;

import java.util.List;

import javax.jdo.JDOHelper;
import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;

public class Database {

	private static final PersistenceManagerFactory pmfInstance = JDOHelper
			.getPersistenceManagerFactory("transactions-optional");

	public static <T> T objectById(Class<T> clazz, Object id) {
		PersistenceManager manager = openManager();
		try {
			try {
				return manager.getObjectById(clazz, id);
			} catch (JDOObjectNotFoundException e) {
				return null;
			}
		} finally {
			manager.close();
		}
	}

	private static PersistenceManager openManager() {
		PersistenceManager manager = pmfInstance.getPersistenceManager();
		manager.setDetachAllOnCommit(true);
		return manager;
	}

	public static <T> T save(T o) {
		PersistenceManager manager = openManager();
		try {
			return manager.makePersistent(o);
		} finally {
			manager.close();
		}
	}

	public static void delete(Object o) {
		PersistenceManager manager = openManager();
		try {
			manager.makePersistent(o);
			manager.deletePersistent(o);
		} finally {
			manager.close();
		}
	}

	@SuppressWarnings("unchecked") public static <T> List<T> queryForList(Class<T> clazz, String queryString,
			Object... parameters) {
		PersistenceManager manager = openManager();
		try {
			Query query = manager.newQuery(queryString);
			List<T> results;
			switch (parameters.length) {
			case 0:
				results = (List<T>) query.execute();
				break;
			case 1:
				results = (List<T>) query.execute(parameters[0]);
				break;
			case 2:
				results = (List<T>) query.execute(parameters[0], parameters[1]);
				break;
			case 3:
				results = (List<T>) query.execute(parameters[0], parameters[1], parameters[2]);
				break;
			default:
				throw new IllegalArgumentException("Parameter size: " + parameters.length);
			}

			// FIXME: hack to initialize:
			results.size();

			return results;
		} finally {
			manager.close();
		}
	}

	@SuppressWarnings("unchecked") public static <T> T queryForObject(Class<T> clazz, String queryString,
			Object... parameters) {
		PersistenceManager manager = openManager();
		try {
			Query query = manager.newQuery(queryString);
			query.setUnique(true);
			T result = (T) query.executeWithArray(parameters);
			return result;
		} finally {
			manager.close();
		}
	}

}
