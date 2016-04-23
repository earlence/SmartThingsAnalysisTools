/*
 * SmartThingsAnalysisTools Copyright 2016 Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.umich.smartthings.overprivilege

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration

class AnalysisDriver 
{
	
	static main(def args)
	{
		def project_root = "/" //REPLACE with your project root
		
		def outputfilename = project_root + "/" + "overprivout.txt"
		def capsAsPerSamsungFile = project_root + "/" + "Capabilities.csv"
		def allCapsFile = project_root + "/" + "capfull.csv"
		
		def sourceCodeDir = project_root + "/" + "dump_ast" //REPLACE with your dataset
		
		def manualAnalysesReflection = project_root + "/" + "skip_apps_reflection_falsepos.txt" 
		
		CompilerConfiguration cc = new CompilerConfiguration(CompilerConfiguration.DEFAULT)
		Logger log = new Logger(outputfilename)
		
		OPAnalysisAST opal = new OPAnalysisAST(log)
		def allCaps = new File(capsAsPerSamsungFile)
		def allCapsAll = new File(allCapsFile)
		opal.loadCapRefAll(allCapsAll)
		//opal.loadCapRef(allCaps)
		
		opal.loadCap2Dev(new File(project_root + "cap2dev.txt"))
		opal.loadDev2Cap(new File(project_root + "devhandlers2cap.txt"))
		//opal.dump_Dev2Cap()
		
		cc.addCompilationCustomizers(opal)
		
		//support libraries
		cc.classpath.add(project_root + "/pcompile/spring-beans-4.1.7.RELEASE.jar")
		cc.classpath.add(project_root + "/pcompile/grails-bootstrap-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/grails-core-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/json-20140107.jar")
		cc.classpath.add(project_root + "/pcompile/grails-encoder-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/grails-web-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/grails-web-boot-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/grails-web-common-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/http-builder-0.6.jar")
		cc.classpath.add(project_root + "/pcompile/httpclient-4.3.3.jar")
		cc.classpath.add(project_root + "/pcompile/grails-compat-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/grails-plugin-converters-3.0.4.jar")
		cc.classpath.add(project_root + "/pcompile/httpcore-4.3.2.jar")
		cc.classpath.add(project_root + "/pcompile/smartthings-stub-classes.jar")
		cc.classpath.add(project_root + "/pcompile/joda-time-2.4.jar")
		//cc.classpath.add("C:\\apps\\pcompile\\rt.jar")
		
		//load up files that we must skip due to reflection (they were manually analysed)
		def reflFile = new File(manualAnalysesReflection)
		def reflectionSkip = new ArrayList();
		reflFile.eachLine { line -> reflectionSkip.add(line + ".txt") }
		
		GroovyShell gshell = new GroovyShell(cc)
										
		new File(sourceCodeDir).eachFile { file ->
			try {
				
				println "processing ${file.getName()}"
				
				//----NOTE----
				//to compute basic statistics, disable skipping of the reflection skiplist files
				//------------
				
				if(!(file.getName() in reflectionSkip))
				{				
					log.append "--app-start--"
					log.append "processing ${file.getName()}"
					gshell.evaluate(file)
					log.append "--app-end--"
				}
				else
					println "skipping ${file.getName()} due to reflection manual analyses"
				
												
			} catch(MissingMethodException mme)
			{
				//missing method on *.definition is fine since it contains
				//nothing related to computing overprivilege
				
				def missingMethod = mme.toString()
				
				if(!missingMethod.contains("definition()"))
					log.append("missing method: " + missingMethod)
			}
		}
		
		/*def file = new File(project_root + "/dump_ast/BigTalker.txt")
		try {
			gshell.evaluate(file)
		} catch(MissingMethodException mme)
		{
			println("missing method: " + mme.toString())
		}*/
		
		opal.summarize()
						
		//def file = new File("D:\\SamsungSmartApps\\dump_ast\\ALARMController.txt")
		//SimpleOverprivilege sop = new SimpleOverprivilege()
		
		//def allCaps = new File("D:\\SamsungSmartApps\\Capabilities.csv")
		//sop.loadCapRef(allCaps)
		//sop.processApp(file)
		//sop.processAllApps("D:\\SamsungSmartApps\\dump_ast")
	}
}
