package de.unisb.cs.st.javalanche.mutation.analyze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.unisb.cs.st.javalanche.mutation.results.Mutation;
import de.unisb.cs.st.javalanche.mutation.results.MutationTestResult;

public class InvariantAnalyzer implements MutationAnalyzer {

	protected static String formatPercent(double d) {
		return null;
	}

	public String analyze(Iterable<Mutation> mutations) {
		int total = 0;
		int withResult = 0;
		int violated = 0;
		int violatedNotCaught = 0;
		int killed = 0;
		List<Mutation> violatedNotCaughtList = new ArrayList<Mutation>();
		for (Mutation mutation : mutations) {
			if (mutation.isKilled()) {
				killed++;
			}
			MutationTestResult mutationResult = mutation.getMutationResult();
			if (mutationResult != null
					&& mutationResult.getDifferentViolatedInvariants() > 0) {
				violated++;
				if (!mutation.isKilled()) {
					violatedNotCaught++;
					violatedNotCaughtList.add(mutation);
				}
			}
			if (mutationResult != null) {
				withResult++;

			}
			total++;
		}
		StringBuilder sb = new StringBuilder();
		// sb.append("Total Mutations: " + total);
		// sb.append('\n');
		sb.append("Killed mutations: " + killed + "\n");
		sb.append(String.format(
				"Mutations that violated invariants: %d (%s / %s)", violated,
				AnalyzeUtil.formatPercent(violated, total), AnalyzeUtil
						.formatPercent(violated, withResult)));
		sb.append('\n');
		sb
				.append(String
						.format(
								"Mutations that violated invariants and were not caught: %d (%s / %s)",
								violatedNotCaught, AnalyzeUtil.formatPercent(
										violatedNotCaught, total), AnalyzeUtil
										.formatPercent(violatedNotCaught,
												violated)));
		sb.append('\n');
		if (false) {
			sb
					.append("List of mutations that violated invariants and were not caught:\n");

			Collections.sort(violatedNotCaughtList, new Comparator<Mutation>() {

				public int compare(Mutation o1, Mutation o2) {
					int i1 = o1.getMutationResult()
							.getDifferentViolatedInvariants();
					int i2 = o2.getMutationResult()
							.getDifferentViolatedInvariants();
					return i1 - i2;
				}

			});
			for (Mutation mutation2 : violatedNotCaughtList) {
				sb.append(mutation2.toShortString());
				sb.append('\n');
				sb.append("Violated invariants ids: "
						+ Arrays.toString(mutation2.getMutationResult()
								.getViolatedInvariants()));
				sb.append('\n');
			}
		}
		return sb.toString();
	}

}
