package org.softevo.mutation.results.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.softevo.mutation.coverageResults.db.TestCoverageLineResult;
import org.softevo.mutation.coverageResults.db.TestCoverageTestCaseName;
import org.softevo.mutation.properties.MutationProperties;
import org.softevo.mutation.results.Mutation;
import org.softevo.mutation.results.MutationCoverage;
import org.softevo.mutation.results.SingleTestResult;
import org.softevo.mutation.results.TestName;
import org.softevo.mutation.results.Mutation.MutationType;
import org.softevo.mutation.util.Util;

/**
 * Class that provides static method that execute queries.
 *
 * @author David Schuler
 *
 */
@SuppressWarnings("unchecked")
public class QueryManager {

	private static final String TEST_CASE_NO_INFO = "NO INFO";
	private static Logger logger = Logger.getLogger(QueryManager.class);

	/**
	 * Prevent initialization.
	 */
	private QueryManager() {

	}

	/**
	 * Get Mutation that corresponds to given mutation from the database.
	 *
	 * @throws RuntimeException
	 *             if no Mutation was found in the db.
	 * @param mutation
	 *            Mutation that is used to query.
	 * @return The Mutation from the database.
	 */
	public static Mutation getMutation(Mutation mutation) {
		Mutation m = getMutationOrNull(mutation);
		if (m == null) {
			throw new RuntimeException("Mutation not found in DB " + mutation);
		}
		return m;
	}

	/**
	 * Get Mutation that corresponds to given mutation from the database.
	 *
	 *
	 * @param mutation
	 *            Mutation that is used to query.
	 * @return The Mutation from the database or null if it is not contained.
	 */
	public static Mutation getMutationOrNull(Mutation mutation) {
		Session session = HibernateUtil.openSession();
		Transaction tx = session.beginTransaction();
		Mutation m = null;
		try {
			if (mutation.getId() != null) {
				m = (Mutation) session.get(Mutation.class, mutation.getId());
			}
			if (m == null) {
				Query query = session
						.createQuery("from Mutation as m where m.className=:name and m.lineNumber=:number and m.mutationForLine=:mforl and m.mutationType=:type");
				query.setParameter("name", mutation.getClassName());
				query.setParameter("number", mutation.getLineNumber());
				query.setParameter("type", mutation.getMutationType());
				query.setParameter("mforl", mutation.getMutationForLine());
				m = (Mutation) query.uniqueResult();
			}
			tx.commit();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
			session.close();
		}
		return m;
	}

	/**
	 * Set the result for the mutation and update the mutation in the database.
	 *
	 * @param mutation
	 *            The Mutation to update.
	 * @param mutationTestResult
	 *            The result used to update.
	 */
	public static void updateMutation(Mutation mutation,
			SingleTestResult mutationTestResult) {
		mutation = getMutation(mutation);
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Mutation m2 = (Mutation) session.get(Mutation.class, mutation.getId());
		session.save(mutationTestResult);
		m2.setMutationResult(mutationTestResult);
		tx.commit();
		session.close();
	}

	public static void updateMutations(Map<Mutation, SingleTestResult> results) {
		logger.info("Storing results for " + results.size() + " mutations");
		Set<Entry<Mutation, SingleTestResult>> entrySet = results.entrySet();
		Session session = HibernateUtil.openSession();
		Transaction tx = session.beginTransaction();
		for (Entry<Mutation, SingleTestResult> entry : entrySet) {
			SingleTestResult mutationTestResult = entry.getValue();
			Mutation mutation = entry.getKey();
			Mutation mutationFromDB = (Mutation) session.get(Mutation.class,
					mutation.getId());
			if (mutationFromDB.getMutationResult() != null) {
				logger
						.warn("Mutation already has a test result - not storing the given result");
				logger.warn("Mutation:" + mutationFromDB);
				logger.warn("Result (that is not stored): "
						+ mutationTestResult);
				session.setReadOnly(mutationFromDB, true);
				session.close();
				break;
			} else {
				session.save(mutationTestResult);
				logger.debug("Setting result for mutation "
						+ mutationFromDB.getId());
				mutationFromDB.setMutationResult(mutationTestResult);
			}
		}
		if (session.isOpen()) {
			tx.commit();
			session.close();
			logger.info("Succesfully stored results for " + results.size()
					+ " mutations");
		}
	}

	/**
	 * Fetches the given number of Mutations from the database, if there are
	 * that much.
	 *
	 * @param maxResults
	 *            The number of Mutations to fetch.
	 * @return The List of fetched Mutations.
	 */
	@SuppressWarnings("unchecked")
	public static List<Mutation> getMutations(int maxResults) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session.createQuery("from Mutation order by linenumber");
		query.setMaxResults(maxResults);
		List l = query.list();
		List<Mutation> mutations = new ArrayList<Mutation>();
		for (Object o : l) {
			mutations.add((Mutation) o);
		}
		tx.commit();
		session.close();
		return mutations;
	}

	/**
	 * Fetch all mutations for a given class from the database.
	 *
	 * @param className
	 *            The name of the class in this form "java.lang.Object"
	 * @return A list of Mutations for this class.
	 */
	@SuppressWarnings("unchecked")
	public static List<Mutation> getAllMutationsForClass(String className) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createQuery("from Mutation where classname=:clName");
		query.setString("clName", className);
		List l = query.list();
		List<Mutation> mutations = (List<Mutation>) l;
		tx.commit();
		session.close();
		return mutations;
	}

	/**
	 * Saves given mutation in database if it is not already contained.
	 *
	 * @param mutation
	 *            The mutation to save.
	 */
	public static synchronized void saveMutation(Mutation mutation) {
		Mutation mutationInDb = getMutationOrNull(mutation);
		if (mutationInDb != null) {
			logger.info("Mutation already contained - not saving to db: "
					+ mutation);
			assert mutationInDb.equalsWithoutId(mutation) : "Expected mutations to be equal: "
					+ mutation + "   " + mutationInDb;
		} else {
			logger.info("Saving Mutation" + mutation);
			save(mutation);
		}
	}

	/**
	 * Save an object to the database, no checking is performed.
	 *
	 * @param objectToSave
	 *            The object to save.
	 */
	public static void save(Object objectToSave) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		session.save(objectToSave);
		tx.commit();
		session.close();
	}

	public static String[] getTestCasesExternalData(Mutation mutation) {
		return getTestCasesExternalData(mutation.getClassName(), mutation
				.getLineNumber());
	}

	/**
	 * Gets all test cases from the database that cover the given line of the
	 * class.
	 *
	 * @param className
	 *            The name of the class to get the test cases for.
	 * @param lineNumber
	 *            The linenumber to get the test cases for.
	 * @return An array that contains the names of the testcases that cover this
	 *         line.
	 */
	public static String[] getTestCasesExternalData(String className,
			int lineNumber) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createQuery("from TestCoverageClassResult as clazz join clazz.lineResults as lineres where clazz.className=:clname and lineres.lineNumber=:lnumber");
		query.setString("clname", className);
		query.setInteger("lnumber", lineNumber);
		query.setFetchSize(10);
		List l = query.list();
		assert l.size() <= 1;
		List<String> retList = null;
		if (l.size() >= 1) {
			Object[] array = (Object[]) l.get(0);
			if (array.length >= 2) {
				TestCoverageLineResult lineResult = (TestCoverageLineResult) array[1];
				List<TestCoverageTestCaseName> testCaseNames = lineResult
						.getTestCases();
				retList = new ArrayList<String>();
				for (TestCoverageTestCaseName name : testCaseNames) {
					retList.add(name.getTestCaseName());
				}
			}
		}
		tx.commit();
		session.close();
		if (retList == null) {
			logger.info("no testcases found for line " + lineNumber
					+ " of class " + className);
			return null;
		}
		logger.info("Found " + retList.size() + " testcases for line "
				+ lineNumber + " of class " + className);
		return retList.toArray(new String[0]);
	}

	/**
	 * Return all mutations that are covers by a given set of test cases.
	 *
	 * @param tests
	 *            A collection of test case names.
	 * @return A collection of mutations that are covered by the given test
	 *         cases.
	 */
	public static Collection<Mutation> getAllMutationsForTestCases(
			Collection<String> tests) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		String sqlQueryString = "SELECT mutation.* FROM TESTCOVERAGETESTCASENAME AS tname,"
				+ " TESTCOVERAGELINERESULT_TESTCOVERAGETESTCASENAME AS line_name,"
				+ " TESTCOVERAGECLASSRESULT_TESTCOVERAGELINERESULT AS class_line,"
				+ " TESTCOVERAGECLASSRESULT AS classResult, MUTATION AS mutation WHERE"
				+ " tname.testcasename = :tcName AND line_name.testcases_id = tname.id"
				+ " AND line_name.testcoveragelineresult_id = class_line.lineresults_id"
				+ " AND class_line.testcoverageclassresult_id = classResult.id AND"
				+ " classResult.classname = mutation.classname";
		Query query = session.createSQLQuery(sqlQueryString).addEntity(
				Mutation.class);
		// String hibernateQuery = "SELECT DISTINCT m FROM Mutation AS m,
		// TestCoverageClassResult AS tccr JOIN tccr.lineResults AS tclr WHERE
		// tclr.testCases.testCaseName = :tcName AND tccr.className =
		// m.className";
		// Query query = session.createQuery(hibernateQuery);

		Set<Mutation> results = new HashSet<Mutation>();
		for (String testCaseName : tests) {
			query.setString("tcName", testCaseName);
			logger.info("TestCaseName: " + testCaseName);
			List queryResults = query.list();
			addAndCastResults(results, queryResults);
		}
		for (Object s : results) {
			System.out.println(s);
		}
		tx.commit();
		session.close();
		return results;
	}

	private static void addAndCastResults(Set<Mutation> results,
			List queryResults) {
		for (Object o : queryResults) {
			logger.log(Level.DEBUG, "Type" + o.getClass());
			results.add((Mutation) o);
		}
	}

	/**
	 * Checks if there are mutations for given class in the database.
	 *
	 * @param className
	 *            Name of the class.
	 * @return True, if there is on or more Mutation in the database.
	 */
	public static boolean hasMutationsforClass(String className) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createQuery("SELECT count(*) from Mutation where classname=:clname");
		query.setString("clname", className);
		List results = query.list();
		long l = getResultFromCountQuery(results);
		tx.commit();
		session.close();
		return l > 0;
	}

	public static long getResultFromCountQuery(List results) {
		Long l = null;
		if (results.size() > 0 && results.get(0) instanceof Long) {
			l = (Long) results.get(0);
		} else {
			throw new RuntimeException("Expected a Long result");
		}
		return l.longValue();
	}

	/**
	 * Checks if the line of a given mutation is covered by a test case.
	 *
	 * @param mutation
	 *            The mutation to check.
	 * @return True, if the line of the mutation is at least covered by one test
	 *         case.
	 */
	public static boolean isCoveredMutation(Mutation mutation) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		String hibernateQuery = "SELECT COUNT(*) FROM TestCoverageClassResult as tccr join tccr.lineResults as lineres where tccr.className=:clname and lineres.lineNumber=:lnumber";
		Query query = session.createQuery(hibernateQuery);
		query.setString("clname", mutation.getClassName());
		query.setInteger("lnumber", mutation.getLineNumber());
		List result = query.list();
		long l = getResultFromCountQuery(result);
		tx.commit();
		session.close();
		return l > 0;
	}

	/**
	 * Returns the number of mutations that have a result associated.
	 *
	 * @return The number of mutations that have a result associated.
	 */
	public static long getNumberOfMutationsWithResult() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		String hibernateQuery = "SELECT count(*) FROM Mutation WHERE mutationResult IS NOT NULL";
		Query query = session.createQuery(hibernateQuery);
		List result = query.list();
		long l = getResultFromCountQuery(result);
		tx.commit();
		session.close();
		return l;
	}

	/**
	 * Query the database for mutations with the given ids.
	 *
	 * @param ids
	 *            The ids that are used to query.
	 * @return Return a list of mutations with the given ids.
	 */
	public static List<Mutation> getMutationsFromDbByID(Long[] ids) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		// Query query = session
		// .createQuery("FROM Mutation m inner join fetch m.mutationResult inner
		// join fetch m.mutationResult.failures inner join fetch
		// m.mutationResult.errors inner join fetch m.mutationResult.passing
		// WHERE m.id IN (:ids)");

		Query query = session
				.createQuery("FROM Mutation m  WHERE m.id IN (:ids)");
		query.setParameterList("ids", ids);

		List results = query.list();

		List<Mutation> mutationList = new ArrayList<Mutation>();

		for (Object m : results) {
			mutationList.add((Mutation) m);
		}

		tx.commit();
		session.close();

		return mutationList;
	}

	/**
	 * Generates a mutation of type not mutated in the database, if there is
	 * none in the db. The generated mutation or the one from the db is
	 * returned.
	 *
	 * @param mutation
	 *            Mutation to generate a mutation of type not mutated for.
	 * @return The generated mutation or the mutation from the db.
	 */
	public static Mutation generateUnmutated(Mutation mutation) {
		Mutation unmutated;
		if (!hasUnmutated(mutation)) {
			unmutated = new Mutation(mutation.getClassName(), mutation
					.getLineNumber(), mutation.getMutationForLine(),
					MutationType.NO_MUTATION);
			saveMutation(unmutated);
		} else {
			unmutated = getUnmutated(mutation);
		}
		return unmutated;
	}

	private static Mutation getUnmutated(Mutation mutation) {
		return getUnmutated(mutation.getClassName(), mutation.getLineNumber());
	}

	public static boolean hasUnmutated(Mutation mutation) {
		return hasUnmutated(mutation.getClassName(), mutation.getLineNumber());
	}

	public static boolean hasUnmutated(String className, int lineNumber) {
		return getUnmutated(className, lineNumber) != null ? true : false;
	}

	public static Mutation getUnmutated(String className, int lineNumber) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createQuery("from Mutation where classname=:clname and linenumber=:lnumber and mutationtype=:mtype");
		query.setString("clname", className);
		query.setInteger("lnumber", lineNumber);
		query.setInteger("mtype", MutationType.NO_MUTATION.ordinal());
		Mutation mmutation = (Mutation) query.uniqueResult();
		tx.commit();
		session.close();
		return mmutation;
	}

	public static void saveMutations(List<Mutation> mutationsToSave) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		int counter = 0;
		for (Mutation mutation : mutationsToSave) {
			if (mutation.getId() != null) {
				logger.debug("Not saving mutation. Mutation already in db "
						+ mutation);
			} else {
				counter++;
				try{
				logger.info(counter + ": Trying to save mutation :" + mutation);
				session.save(mutation);
				}catch(Exception e){
					logger.warn("Exception thrown: " + e.getMessage());
					logger.info(Util.getStackTraceString());
					logger.info("Mutations to save: " + mutationsToSave);
					throw new RuntimeException(e);
				}
			}
		}
		tx.commit();
		session.close();

	}

	public static int getNumberOfMutationsForClass(String className) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createSQLQuery("SELECT count(*) FROM Mutation WHERE className = :clName AND mutationType!= 0;");
		query.setString("clName", className);
		Integer numberOfMutations = Integer.valueOf(query.uniqueResult()
				.toString());
		tx.commit();
		session.close();
		return numberOfMutations.intValue();
	}

	public static List<Mutation> getMutationListFromDb(int numberOfMutations) {
		String prefix = MutationProperties.PROJECT_PREFIX;
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		String queryString = "SELECT m.* FROM Mutation m JOIN TestCoverageClassResult tccr ON m.classname = tccr.classname JOIN TestCoverageClassResult_TestCoverageLineResult AS class_line ON class_line.testcoverageclassresult_id = tccr.id JOIN TestCoverageLineResult AS tclr ON tclr.id = class_line.lineresults_id 	WHERE m.mutationresult_id IS NULL AND m.linenumber = tclr.linenumber AND m.mutationType != 0 AND m.className LIKE '"
				+ prefix + "%' ";
		if (!MutationProperties.COVERAGE_INFFORMATION) {
			queryString = "SELECT m.* FROM Mutation m WHERE m.mutationresult_id IS NULL  AND m.mutationType != 0 AND m.className LIKE '"
					+ prefix + "%' ";
		}
		Query query = session.createSQLQuery(queryString).addEntity(
				Mutation.class);
		query.setMaxResults(numberOfMutations);
		List results = query.list();
		List<Mutation> idList = new ArrayList<Mutation>();
		for (Object mutation : results) {
			idList.add((Mutation) mutation);
		}
		tx.commit();
		session.close();
		return idList;
	}

	/**
	 * Return a list of mutation ids that have coverage data associated but do
	 * not have a result yet.
	 *
	 * @return a list of mutation ids.
	 */
	public static List<Long> getMutationsIdListFromDb(int numberOfMutations,
			String prefix) {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();

		String queryString =

		"SELECT distinct(m.id) FROM Mutation m"
				+ " JOIN MutationCoverage mc ON m.id = mc.mutationId"
				+ " JOIN MutationCoverage_TestName mctn ON mc.id = mctn.MutationCoverage_id"
				+ " JOIN TestName tn ON mctn.testNames_id = tn.id"
				+ " WHERE tn.name !='NO INFO'"
				+ " AND m.mutationResult_id IS NULL " + " AND m.mutationType != 0"
				+ " AND m.className LIKE '" + prefix + "%' ORDER BY  m.id ";
		// String queryString = "SELECT m.id FROM Mutation m JOIN
		// TestCoverageClassResult tccr ON m.classname = tccr.classname JOIN
		// TestCoverageClassResult_TestCoverageLineResult AS class_line ON
		// class_line.testcoverageclassresult_id = tccr.id JOIN
		// TestCoverageLineResult
		// AS tclr ON tclr.id = class_line.lineresults_id WHERE
		// m.mutationresult_id IS
		// NULL AND m.linenumber = tclr.linenumber AND m.mutationType != 0 AND
		// m.className LIKE '"
		// + prefix + "%' ";
		if (!MutationProperties.COVERAGE_INFFORMATION) {
			queryString = "SELECT m.id FROM Mutation m WHERE "
					+ " m.mutationresult_id IS NULL  AND m.mutationType != 0 AND m.className LIKE '"
					+ prefix + "%' ";
		}
		Query query = session.createSQLQuery(queryString);
		query.setMaxResults(numberOfMutations);
		List results = query.list();
		List<Long> idList = new ArrayList<Long>();
		for (Object id : results) {
			idList.add(Long.valueOf(id.toString()));
		}
		tx.commit();
		session.close();
		return idList;
	}

	/**
	 * Save the given coverage data to the database.
	 *
	 * @param coverageData
	 *            a map that contains the collected coverage data
	 */
	public static void saveCoverageResults(Map<Long, Set<String>> coverageData) {
		Session session = HibernateUtil.openSession();
		Transaction tx = session.beginTransaction();
		// Query nullQuery = session
		// .createQuery("from TestName as m where m.name IS NULL");
		// TestName nullValue = (TestName) nullQuery.uniqueResult();
		Query query = session.createQuery("from TestName");// as m where m.name
		// IN (:names)");
		// query.setParameterList("names", entry.getValue());
		List<TestName> testNameQueryList = query.list();
		Map<String, TestName> testNameMap = new HashMap<String, TestName>();
		for (TestName tn : testNameQueryList) {
			testNameMap.put(tn.getName(), tn);
		}

		for (Map.Entry<Long, Set<String>> entry : coverageData.entrySet()) {
			List<TestName> testNames = new ArrayList<TestName>();
			for (String testCase : entry.getValue()) {
				TestName testName = null;
				if (testCase == null) {
					testCase = TEST_CASE_NO_INFO;
				}
				if (testNameMap != null && testNameMap.containsKey(testCase)) {
					testName = testNameMap.get(testCase);
				} else {
					testName = new TestName(testCase);
					session.save(testName);
					testNameMap.put(testCase, testName);
				}
				testNames.add(testName);
			}
			MutationCoverage mutationCoverage = new MutationCoverage(entry
					.getKey(), testNames);
			session.save(mutationCoverage);
		}

		tx.commit();
		session.close();
	}

	/**
	 * Returns the coverage data for given mutation id, or null if it has none.
	 *
	 * @param id
	 *            the id of the mutation.
	 * @return the coverage data or null
	 */
	public static MutationCoverage getMutationCoverageData(long id) {
		logger.debug("Getting coverage data for mutation " + id);
		Session session = HibernateUtil.openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createQuery("from MutationCoverage as m where m.mutationID = :mutation_id");
		query.setLong("mutation_id", id);
		MutationCoverage mc = (MutationCoverage) query.uniqueResult();
		if (mc != null) {
			Hibernate.initialize(mc.getTestsNames());
		}
		tx.commit();
		session.close();
		if (mc == null) {
			logger.warn("found no coverage data for mutation with id " + id);
		}

		return mc;
	}

	/**
	 * Delete Coverage data for given set of mutationids. rage data
	 *
	 * @param ids
	 *            mutation ids that should be deleted.
	 */
	@SuppressWarnings("unchecked")
	public static void deleteCoverageResult(List<Long> ids) {
		Session session = HibernateUtil.openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createQuery("from MutationCoverage as m where m.mutationID IN (:mutation_ids)");
		query.setParameterList("mutation_ids", ids);
		List<MutationCoverage> mcs = query.list();
		for (MutationCoverage mc : mcs) {
			session.delete(mc);
		}
		tx.commit();
		session.close();
	}

	public static void deleteCoverageResultByMutaiton(List<Mutation> mutations) {
		List<Long> ids = new ArrayList<Long>();
		for (Mutation mutation : mutations) {
			if (mutation.getId() != null) {
				ids.add(mutation.getId());
			} else {
				throw new RuntimeException("Mutation with id null" + mutation);
			}
		}
		deleteCoverageResult(ids);
	}

	/**
	 *
	 * Add testnames to the database although they did not cover any mutation.
	 *
	 * @param testsRun
	 */
	@SuppressWarnings("unchecked")
	public static void saveTestsWithNoCoverage(Set<String> testsRun) {
		Session session = HibernateUtil.openSession();
		Transaction tx = session.beginTransaction();
		Query query = session.createQuery("from TestName");
		List<TestName> testNameQueryList = query.list();
		List<String> testsToAdd = new ArrayList<String>(testsRun);
		for (TestName tn : testNameQueryList) {
			testsToAdd.remove(tn.getName());
		}
		for (String addTest : testsToAdd) {
			session.save(new TestName(addTest));
		}
		tx.commit();
		session.close();
	}

	public static Set<String> getTestsCollectedData(Mutation mutation) {
		MutationCoverage mutationCoverage = getMutationCoverageData(mutation
				.getId());
		if (mutationCoverage == null) {
			return null;
		}
		List<TestName> testsNames = mutationCoverage.getTestsNames();
		Set<String> tests = new HashSet<String>();
		for (TestName testName : testsNames) {
			String testNameString = testName.getName();
			if (!testNameString.equals(TEST_CASE_NO_INFO)) {
				tests.add(testNameString);
			}
		}
		return tests;
	}

}
