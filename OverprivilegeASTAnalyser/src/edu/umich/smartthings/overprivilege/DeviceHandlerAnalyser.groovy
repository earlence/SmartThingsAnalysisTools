/*
 * SmartThingsAnalysisTools Copyright 2016 Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.umich.smartthings.overprivilege

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class DeviceHandlerAnalyser extends CompilationCustomizer
{

	List allCommandsList
	
	Logger log
	
	public DeviceHandlerAnalyser(Logger logger)
	{
		super(CompilePhase.SEMANTIC_ANALYSIS)
				
		allCommandsList = new ArrayList()
		
		log = logger
	}
	
	@Override
	void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
		
		InsnVisitor insnVis = new InsnVisitor()
		classNode.visitContents(insnVis)
		
		processApp(insnVis)
	}
	
	class InsnVisitor extends ClassCodeVisitorSupport
	{
		Set<String> nonstdCmds
				
		public InsnVisitor()
		{
			nonstdCmds = new HashSet<String>()
			
		}
		
		@Override
		void visitMethodCallExpression(MethodCallExpression mce)
		{
			//println mce.getMethodAsString()
			
			
			
			super.visitMethodCallExpression(mce)
		}
		
		@Override
		protected SourceUnit getSourceUnit() {
			return null;
		}
				
	}
		
	def processApp(InsnVisitor insnVis)
	{
		
	}
}
