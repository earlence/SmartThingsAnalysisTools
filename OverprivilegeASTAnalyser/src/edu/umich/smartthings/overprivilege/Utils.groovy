/*
 * SmartThingsAnalysisTools Copyright 2016 Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.umich.smartthings.overprivilege;

public class Utils
{
	public static String[][] allUniqueCombinations(LinkedHashMap<String, Vector<String>> dataStructure)
	{
		int n = dataStructure.keySet().size().intValue();
		int solutions = 1;

		for(Vector<String> vector : dataStructure.values()) {
			solutions = solutions * vector.size().intValue();
		}

		String[][] allCombinations = new String[solutions + 1][];
		allCombinations[0] = dataStructure.keySet().toArray(new String[n]);

		for(int i = 0; i < solutions; i++) {
			Vector<String> combination = new Vector<String>(n);
			int j = 1;
			for(Vector<String> vec : dataStructure.values()) {
				int vecSize = vec.size().intValue()
				combination.add(vec.get(((int) i/j) % vec.size().intValue()));
				j = j * vec.size().intValue();
			}
			allCombinations[i + 1] = combination.toArray(new String[n]);
		}

		return allCombinations;
	}
}