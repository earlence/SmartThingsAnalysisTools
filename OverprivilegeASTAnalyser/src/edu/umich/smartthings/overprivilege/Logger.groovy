/*
 * SmartThingsAnalysisTools Copyright 2016 Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.umich.smartthings.overprivilege

class Logger 
{
	File file
	
	public Logger(def filename)
	{
		file = new File(filename)
	}
	
	public void append(String s)
	{
		file.append(System.getProperty("line.separator") + s)
	}
}
