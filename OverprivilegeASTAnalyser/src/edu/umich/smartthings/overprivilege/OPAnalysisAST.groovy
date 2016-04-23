/*
 * SmartThingsAnalysisTools Copyright 2016 Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package edu.umich.smartthings.overprivilege

//@Grab(group='org.codehaus.gpars', module='gpars', version='1.0.0')
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehause.groovyx.gpars.*

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class OPAnalysisAST extends CompilationCustomizer
{

	Map allCommands
	Map allProps
	
	Map dev2cap
	Map cap2dev
	
	List allCommandsList
	List allPropsList
	List allCapsList
	
	int numCmdOverpriv
	int numAttrOverpriv
	int numTotalOverpriv
	int numReflection
	int type2_numCaps
	int samename_flags
	int type2_cmdattr_uses
	
	int numSendSms
	int numOAuth
	int numInternet
	
	Logger log
	
	public OPAnalysisAST(Logger logger)
	{
		super(CompilePhase.SEMANTIC_ANALYSIS)
		
		allCommands = new HashMap()
		allProps = new HashMap()
		
		cap2dev = new HashMap()
		dev2cap = new HashMap()
		
		allCommandsList = new ArrayList()
		allPropsList = new ArrayList()
		allCapsList = new ArrayList()
		
		numCmdOverpriv = 0
		numAttrOverpriv = 0
		numTotalOverpriv = 0
		numReflection = 0
		
		type2_numCaps = 0
		
		samename_flags = 0
		type2_cmdattr_uses = 0
		
		numSendSms = 0
		numOAuth = 0
		numInternet = 0
		
		log = logger
	}
	
	@Override
	void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
		
		//step 1: run a MethodCodeVisitor that will accumulate 
		//DeclarationExpressions
		MethodCodeVisitor mcvDex = new MethodCodeVisitor()
		classNode.visitContents(mcvDex)
		
		//step 2: run an instruction visitor
		InsnVisitor insnVis = new InsnVisitor(mcvDex.dexpressions, mcvDex.bexpressions)
		classNode.visitContents(insnVis)
		
		def allMethodNodes = classNode.getAllDeclaredMethods()
		def allFieldNodes = classNode.getFields()
		
		ArrayList<String> declaredMethods = new ArrayList<String>()
		allMethodNodes.each { it -> declaredMethods.add(it.getName().toLowerCase()) }
				
		//to compute declared fields, you must inspect the run() method
		//injected by the groovy compiler
		
		processApp(insnVis, declaredMethods)
		
		//compute type 2 overprivilege as the number of unused
		//capabilities. These unused capabilities come from the device
		//handlers that implement multiple capabilities
		computeType2Overprivilege(insnVis, declaredMethods)
	}
	
	class MethodCodeVisitor extends ClassCodeVisitorSupport
	{
		public ArrayList<String> globals
		public ArrayList<DeclarationExpression> dexpressions
		public ArrayList<BinaryExpression> bexpressions
		
		public MethodCodeVisitor()
		{
			globals = new ArrayList<String>()
			dexpressions = new ArrayList<DeclarationExpression>()
			bexpressions = new ArrayList<BinaryExpression>()
		}
		
		@Override
		public void visitBinaryExpression(BinaryExpression bex)
		{
			bexpressions.add(bex)
		}
		
		@Override
		public void visitDeclarationExpression(DeclarationExpression dex)
		{
			println "visiting a dex: " + dex.getText()
			dexpressions.add(dex)
			
			if(!dex.isMultipleAssignmentDeclaration())
			{
				VariableExpression left = dex.getVariableExpression()
				globals.add(left.getName().toLowerCase())
			}
			else
			{
				TupleExpression tex = dex.getTupleExpression()
				List<Expression> lefts = tex.getExpressions()
				lefts.each { it -> globals.add(it.getName().toLowerCase()) }
			}
			
			super.visitDeclarationExpression(dex)
		}
		
		@Override
		protected SourceUnit getSourceUnit() {
			return null;
		}
	}
	
	class InsnVisitor extends ClassCodeVisitorSupport
	{
		Set<String> calledMethods
		Set<String> calledProps
		Set<String> requestedCaps
		List<String> declaredCapVars
		List<String> declaredGlobalVars
		List<String> usedAttrs
		Map attrSubs
		int globalBodyEntries = 0
		ArrayList<DeclarationExpression> alldexprs
		ArrayList<BinaryExpression> allbexprs
		
		boolean usesAddChildDevice
		boolean usesOAuth
		boolean usesSendSms
		boolean usesInternet
		
		public InsnVisitor(ArrayList<DeclarationExpression> alldex, ArrayList<BinaryExpression> allbex)
		{
			calledMethods = new HashSet<String>()
			calledProps = new HashSet<String>()
			requestedCaps = new HashSet<String>()
			declaredCapVars = new ArrayList<String>()
			attrSubs = new HashMap<String, HashSet<String> >()
			usedAttrs = new ArrayList<String>()
			
			usesAddChildDevice = false
			usesOAuth = false
			usesSendSms = false
			usesInternet = false
			
			alldexprs = alldex
			allbexprs = allbex
		}
		
		@Override
		void visitMethodCallExpression(MethodCallExpression mce)
		{
			//println mce.getMethodAsString()
			def methText
				
			
			if(mce.getMethodAsString() == null)
			{
				//dynamic methods
				methText = mce.getText()
			}
			else
			{
				methText = mce.getMethodAsString()
			}
			
			//determine the receiver of the method call
			def recver = mce.getReceiver()
			if(recver instanceof VariableExpression)
			{
				VariableExpression recvex = (VariableExpression) recver
				if(!recvex.getName().equals("this"))
				{
					//capability commands will never be
					//invoked on the "this" static variable.
					//they are always invoked on same variable
					//derived from the dynamic variable injected
					//by the ST compiler
					
					//here be bullshit
					//imageCapture.take and list.take
					//are the same damn name.
					//but, take(), or take(delay: value) are for the ST cmd/attr
					if(methText.equals("take"))
					{
						if(mce.getArguments().toList().size() > 0)
						{
							mce.getArguments().each { arg ->
								if(arg instanceof NamedArgumentListExpression)
								{
									NamedArgumentListExpression nnale = (NamedArgumentListExpression) arg
									nnale.mapEntryExpressions.each { mee ->
										def keyexprText = mee.getKeyExpression().getText()
										
										if(keyexprText.equals("delay"))
										{
											calledMethods.add(methText)
										}
									}
								}
							}
						}
						else
							calledMethods.add(methText)
						
					}
					else
						calledMethods.add(methText)
				}
			}
			
			if(recver instanceof PropertyExpression)
			{
				PropertyExpression pex = (PropertyExpression) recver
				if(!pex.getPropertyAsString().equals("this"))
				{
					//here be bullshit.
					//imageCapture.take and list.take
					//are the same damn name.
					//but, take(), or take(delay: value) are for the ST cmd/attr
					if(methText.equals("take"))
					{
						if(mce.getArguments().toList().size() > 0)
						{
							mce.getArguments().each { arg ->
								if(arg instanceof NamedArgumentListExpression)
								{
									NamedArgumentListExpression nnale = (NamedArgumentListExpression) arg
									nnale.mapEntryExpressions.each { mee ->
										def keyexprText = mee.getKeyExpression().getText()
										
										if(keyexprText.equals("delay"))
										{
											calledMethods.add(methText)
										}
									}
								}
							}
						}
						else
							calledMethods.add(methText)
						
					}
					else
						calledMethods.add(methText)
				}
			}
			
			//if you wondering what this awful "ifSet" is,
			//then you have some app author to thank. In his
			//infinite wisdom, he decided to redefine input -> ifSet
			if(methText.equals("input") ||
				methText.equals("ifSet"))
			{
				//we are searching for capability.* 
				def args = mce.getArguments()
				
				args.each { arg ->
					if(arg instanceof ConstantExpression)
					{
						def txt = arg.getText()?.toLowerCase()
						if(txt.contains("capability."))
						{
							requestedCaps.add(txt)
						}
					}
					else if(arg instanceof MapExpression || 
						arg instanceof VariableExpression ||
						arg instanceof PropertyExpression)
					{
						MapExpression mex = null
						
						if(arg instanceof PropertyExpression)
						{
							def property = ((PropertyExpression) arg).getText()
							//search thru our list of binary expressions
							allbexprs.each { bexpr ->
								if(bexpr.getLeftExpression() instanceof PropertyExpression)
								{
									def leftProperty = (PropertyExpression) bexpr.getLeftExpression()
									if(leftProperty.getText().equals(property))
									{
										if(bexpr.getRightExpression() instanceof ConstantExpression)
										{
											def right = (ConstantExpression) bexpr.getRightExpression()
											def rightText = right.getText()
											println "followed a Binary Property Constant expression"
											println rightText + " is assigned to " + leftProperty.getText()
											
											if(rightText.contains("capability."))
												requestedCaps.add(rightText.toLowerCase())
										}
									}
								}
							}
						}						
						else if(arg instanceof VariableExpression)
						{
							//run thru the AST once to find the DeclarationExpression
							//for this VariableExpression
							//we expect the rightExpression() of the declaration
							//to be a MapExpression. We only try to do this
							//once as a matter of policy. Ideally, could write
							//a recursive function to traverse multiple levels.
							VariableExpression argvex = (VariableExpression) arg
							def varName = argvex.getName()
							
							for(BinaryExpression bexp in allbexprs)
							{
								//input mapVariable
								if(bexp.getRightExpression() instanceof MapExpression)
								{
									if(bexp.getLeftExpression() instanceof VariableExpression)
									{
										//the left is a Variable, right is a Map
										def testVarName = ((VariableExpression) bexp.getLeftExpression()).getName()
										if(testVarName.equals(varName))
										{
											println "followed 1 binary expr: " + varName
											mex = (MapExpression) bexp.getRightExpression()
											break
										}
									}
								}
								//input "strVar", varWithSomeDeclEarlier
								else if(bexp.getRightExpression() instanceof ConstantExpression)
								{
									if(bexp.getLeftExpression() instanceof VariableExpression)
									{
										//the left is a Variable, right is a constant
										def testVarName = ((VariableExpression) bexp.getLeftExpression()).getName()
										if(testVarName.equals(varName))
										{
											def txt = bexp.getRightExpression().getText()?.toLowerCase()
											if(txt.contains("capability."))
											{
												requestedCaps.add(txt.toLowerCase())
											}
										}
									}
								}
							}
						}
						else
							mex = (MapExpression) arg
							
						mex?.mapEntryExpressions.each { inner ->
							
							def keyExpr = inner.getKeyExpression()
							def valExpr = inner.getValueExpression()
							
							if(valExpr instanceof ConstantExpression)
							{
								ConstantExpression cap_exp = (ConstantExpression) valExpr
								def txt = cap_exp.getText()?.toLowerCase()
								if(txt.contains("capability."))
								{
									requestedCaps.add(txt)
								}
							}
						}
					}
					else if(arg instanceof NamedArgumentListExpression)
					{
						NamedArgumentListExpression nnale = (NamedArgumentListExpression) arg
						nnale?.mapEntryExpressions.each { inner ->
							
							def keyExpr = inner.getKeyExpression()
							def valExpr = inner.getValueExpression()
							
							if(valExpr instanceof ConstantExpression)
							{
								ConstantExpression cap_exp = (ConstantExpression) valExpr
								def txt = cap_exp.getText()?.toLowerCase()
								if(txt.contains("capability."))
								{
									requestedCaps.add(txt)
								}
							}
						}
					}
				}
			}
			
			/*
			This technique was searching for the declared cap var
			and the cap itself. But since we manually process
			same-named commands, we dont need to track declared cap vars
			anymore.
			
			if(methText.equals("input"))
			{
				def args = mce.getArguments()
				
				List cexp = new ArrayList()
				
				args.each { arg ->
					if(arg instanceof ConstantExpression)
					{
						cexp.add((ConstantExpression) arg)
					}
					else if(arg instanceof MapExpression)
					{
						MapExpression mex = (MapExpression) arg
						mex?.mapEntryExpressions.each { inner ->
							
							def keyExpr = inner.getKeyExpression()
							def valExpr = inner.getValueExpression()
							
							if(keyExpr instanceof ConstantExpression &&
								valExpr instanceof ConstantExpression)
							{
								ConstantExpression capVar_cexp = (ConstantExpression) keyExpr
								ConstantExpression cap_exp = (ConstantExpression) valExpr
								
								
								
								if(capVar_cexp.getText()?.toLowerCase().equals("name"))
								{
									cexp[0] = cap_exp
								}
								else if(capVar_cexp.getText()?.toLowerCase().equals("type"))
								{
									cexp[1] = cap_exp
								}
							}
							
							//we've filled up a name and type
							if(cexp.size() == 2)
							{
								
								def theReqCap = cexp[1]?.getText()?.toLowerCase()
								def theDecCapVar = cexp[0]?.getText()
								
								requestedCaps.add(theReqCap)
								declaredCapVars.add(theDecCapVar)
								
								//println theDecCapVar + "," + theReqCap
								
								cexp.clear()
							}
						}					
					}
					else if(arg instanceof NamedArgumentListExpression)
					{
						NamedArgumentListExpression nnale = (NamedArgumentListExpression) arg
						nnale?.mapEntryExpressions.each { inner ->
							
							def keyExpr = inner.getKeyExpression()
							def valExpr = inner.getValueExpression()
							
							if(keyExpr instanceof ConstantExpression &&
								valExpr instanceof ConstantExpression)
							{
								ConstantExpression capVar_cexp = (ConstantExpression) keyExpr
								ConstantExpression cap_exp = (ConstantExpression) valExpr
								
								
								
								if(capVar_cexp.getText()?.toLowerCase().equals("name"))
								{
									cexp[0] = cap_exp
								}
								else if(capVar_cexp.getText()?.toLowerCase().equals("type"))
								{
									cexp[1] = cap_exp
								}
							}
							
							//we've filled up a name and type
							if(cexp.size() == 2)
							{
								
								def theReqCap = cexp[1]?.getText()?.toLowerCase()
								def theDecCapVar = cexp[0]?.getText()
								
								requestedCaps.add(theReqCap)
								declaredCapVars.add(theDecCapVar)
								
								//println theDecCapVar + "," + theReqCap
								
								cexp.clear()
							}

						}
					}
					
					//this is used when none of the MapExpression-style
					//declarations are used
					if(cexp.size() == 2)
					{
						
						def theReqCap = cexp[1].getText()?.toLowerCase()
						def theDecCapVar = cexp[0].getText()
						
						requestedCaps.add(theReqCap)
						declaredCapVars.add(theDecCapVar)
						
						//println theDecCapVar + "," + theReqCap
						
						cexp.clear()
					}
				}
			}
			*/
			
			if(methText.equals("subscribeToCommand"))
			{
				//the app is not actually calling the command but
				//only listening to whether a device was sent that command
				//therefore, this is not a calledMethod. Neither is it a 
				//call attribute. Question is, is it a attribute access thru
				//subscription? I don't know. So I'm marking this for manual
				//analysis
				log.append "subscribeToCommand"
				OPAnalysisAST.this.append_manual()
			}
			
			if(methText.equals("subscribe"))
			{
				def args = mce.getArguments()
				def skipCheck = false
				//if(args.size() == 3) //device state subscriptions must have 3 parameters
				if(args.size() > 0)
				{
					
					//handle location and app subscriptions
					if(args[0] instanceof VariableExpression)
					{
						VariableExpression argvex0 = (VariableExpression) args[0]
						if(argvex0.getName().equals("location") ||
							argvex0.getName().equals("app"))
						{
							//no warnings here. 
							//we don't do anything with it.
							skipCheck = true
						}
					}
					
					if(!skipCheck)
					{
						//we have a variable, constant, variable generally
						//if we don't, flag this by spitting out text on the console
						//so we can analyse and see how to deal with it
						if(args[1] instanceof ConstantExpression)
						{
							ConstantExpression cexp = (ConstantExpression) args[1]
							def cexptext = cexp.getText()
							def attrUse = cexptext
							if(cexptext.contains("."))
							{
								//strip out the dot and stuff after it
								int index = cexptext.indexOf('.')
								attrUse = cexptext.substring(0, index)
							}
							
							usedAttrs.add(attrUse)
						}
						else
						{
							log.append "subscribe: not a ConstantExpression!"
							OPAnalysisAST.this.append_manual()
							
							if(! (args[0] instanceof VariableExpression))
							{
								log.append "subscribe: first arg not a VariableExpression"
								OPAnalysisAST.this.append_manual()
							}
						}
					}
					
					/*if(args[0] instanceof VariableExpression && 
						args[1] instanceof ConstantExpression) //we don't care about the "handler"
					{
						VariableExpression devexp = (VariableExpression) args[0]
						ConstantExpression cexp = (ConstantExpression) args[1]
						
						//devexp represents an item from the input method call and is an IoT device
						//cexp is the use of the attribute. Generally, a subscribe call listens
						//to state changes in attribute values of capabilities. Therefore, we count
						//a subscribe as a use of an attribute
						def cexptext = cexp.getText()
						def attrUse = cexptext
						if(cexptext.contains("."))
						{
							//strip out the dot and stuff after it
							int index = cexptext.indexOf('.')
							attrUse = cexptext.substring(0, index)
						}
						
						def capVar = devexp.getText()
						if(attrSubs.containsKey(capVar))
						{
							attrSubs[capVar].add(attrUse)
						}
						else
						{
							Set uses = new HashSet<String>()
							uses.add(attrUse)
							attrSubs.put(capVar, uses)
						}
					}*/		
				}												
			}
			
			if(methText.equals("currentState") || methText.equals("currentValue"))
			{
				def args = mce.getArguments()
				
				if(args.size() == 1) //these methods have only 1 arg
				{
					//currently, we are assuming that a constant is passed in.
					//might have to handle variable expressions if we observe that in the dataset
					if(args[0] instanceof ConstantExpression)
					{
						ConstantExpression cexp = (ConstantExpression) args[0]
						usedAttrs.add(cexp.getText()?.toLowerCase())
					}
					else
					{
						log.append mce.getMethodAsString() + ", arg not ConstantExpression"
						OPAnalysisAST.this.append_manual()
					}
				}
			}
			
			if(methText.equals("latestState") || methText.equals("latestValue"))
			{
				def args = mce.getArguments()
				
				if(args.size() == 1) //these methods have only 1 arg
				{
					//currently, we are assuming that a constant is passed in.
					//might have to handle variable expressions if we observe that in the dataset
					if(args[0] instanceof ConstantExpression)
					{
						ConstantExpression cexp = (ConstantExpression) args[0]
						usedAttrs.add(cexp.getText()?.toLowerCase())
					}
					else
					{
						log.append mce.getMethodAsString() + ", arg not ConstantExpression"
						OPAnalysisAST.this.append_manual()
					}
				}
			}
			
			if(methText.equals("statesSince") || methText.equals("statesBetween"))
			{
				def args = mce.getArguments()
				
				//currently, we are assuming that a constant is passed in.
				//might have to handle variable expressions if we observe that in the dataset
				//for states* methods, we only care about the first argument -- the attr name
				if(args[0] instanceof ConstantExpression)
				{
					ConstantExpression cexp = (ConstantExpression) args[0]
					usedAttrs.add(cexp.getText()?.toLowerCase())
				}
				else
				{
					log.append mce.getMethodAsString() + ", arg not ConstantExpression"
					OPAnalysisAST.this.append_manual()
				}
			}
			
			if(methText.contains("\$"))
			{
				//possible reflection call
				def args = mce.getArguments()
				if(args.toList().size() > 0)
					log.append "reflective call with args: " + methText + ", count:" + args.toList().size()
			}
			
			if(methText.contains("addChildDevice"))
			{
				//adding child devices gives the SmartApp
				//access to a devicehandler without using
				//an input statement. So our type2 overpriv use
				//counter might increase  even though it should 
				//not because child devices do not point to
				//existing devices. The smartapp manages the child
				//device in the first place. therefore, we do not
				//count it as overprivilege.
				//log.append("addChildDevice usage")
				//OPAnalysisAST.this.append_manual()
				usesAddChildDevice = true
			}
			
			if(methText.contains("sendSms") || methText.contains("sendSmsMessage"))
				usesSendSms = true
				
			if(methText.contains("mappings"))
				usesOAuth = true
			
			if(methText.contains("httpDelete") ||
				methText.contains("httpGet") ||
				methText.contains("httpHead") ||
				methText.contains("httpPost") ||
				methText.contains("httpPostJson") ||
				methText.contains("httpPut") ||
				methText.contains("httpPutJson"))
			{
				usesInternet = true
			}
			
			super.visitMethodCallExpression(mce)
		}
		
		/*public List getSubscriptionAttrs()
		{
			List usedAttrs = new ArrayList<String>()
			
			attrSubs.each { k, v ->
				if(declaredCapVars.find { it.equals(k) } != null)
				{
					//the key of attrSubs is in fact a real capability variable
					//that represents an IoT device. So add each attribute
					//to the used list
					v.each { usedAttrs.add it }					
				}
			}
			
			return usedAttrs
		}*/
		
		public List getSubscriptionAttrs()
		{
			return usedAttrs
		}
		
		@Override
		void visitPropertyExpression(PropertyExpression pe)
		{
			//location.*/settings.*/state.* is not a property gained thru capabilities
			//so we skip it
			Expression expr = pe.getObjectExpression()
			if(expr instanceof VariableExpression)
			{
				VariableExpression pvex = (VariableExpression) expr
				if(pvex.getName().equals("location") ||
					pvex.getName().equals("settings") ||
					pvex.getName().equals("state"))
				{
					//do nothing
				}
				else
				{
					calledProps.add(pe.getPropertyAsString()?.toLowerCase())		
				}
			}
			
			super.visitPropertyExpression(pe)
		}
		
		@Override
		void visitMethod(MethodNode meth)
		{
			MethodCodeVisitor mcv = new MethodCodeVisitor()
			
			String returnType = meth.getReturnType().getName()
			String methName = meth.getName()
			
			if(returnType.equals("java.lang.Object") && methName.equals("run"))
			{
				//this condition may only execute once for an app
				globalBodyEntries += 1
				
				assert globalBodyEntries == 1
								
				meth.getCode()?.visit(mcv)
				
				declaredGlobalVars = mcv.globals
			}
						
			super.visitMethod(meth)
		}
						
		@Override
		protected SourceUnit getSourceUnit() {
			return null;
		}
	}
					
	def loadCapRef(def file)
	{
		file.splitEachLine(",") { fields ->
			allCommands[fields[1]?.toLowerCase()] = fields[3]?.toLowerCase()
			allProps[fields[1]?.toLowerCase()] = fields[2]?.toLowerCase()
		}
		
		allCommands.each { k, v ->
			def values = v?.split(" ")
			values?.each { allCommandsList.add(it.toLowerCase()) }
									
			allCapsList.add(k.toLowerCase())
		}
		
		allProps.each { k, v ->
			def values = v?.split(" ")
			values?.each { allPropsList.add(it.toLowerCase()) }
		}
		
		println "all commands:" + allCommandsList.size()
		println "all attrs:" + allPropsList.size()
	}
	
	def loadCapRefAll(def file)
	{
		file.splitEachLine(",") { fields ->
			allCommands[fields[0]?.toLowerCase()] = fields[2]?.toLowerCase()
			allProps[fields[0]?.toLowerCase()] = fields[1]?.toLowerCase()
		}
		
		allCommands.each { k, v ->
			def values = v?.split(" ")
			values?.each { allCommandsList.add(it.toLowerCase()) }
									
			allCapsList.add(k.toLowerCase())
		}
		
		allProps.each { k, v ->
			def values = v?.split(" ")
			values?.each { allPropsList.add(it.toLowerCase()) }
		}
		
		println "all commands full:" + allCommandsList.size()
		println "all attrs full:" + allPropsList.size()
	}
	
	def loadCap2Dev(def file)
	{
		file.splitEachLine(",") { fields ->
			def capname = fields[0].toLowerCase()
			def copyFields = fields.toList()
			copyFields.remove(0)
			
			def listOfDeviceIds = new ArrayList()
			copyFields.each { devId -> listOfDeviceIds.add(devId) }
			
			cap2dev[capname] = listOfDeviceIds
		}
	}
	
	def loadDev2Cap(def file)
	{
		file.splitEachLine(",") { fields ->
			def devname = fields[0].toLowerCase()
			def copyFields = fields.toList()
			copyFields.remove(0)
			
			def listOfCaps = new ArrayList()
			copyFields.each { cap -> listOfCaps.add(cap.toLowerCase()) }
			
			dev2cap[devname] = listOfCaps
		}
	}
	
	def dump_Dev2Cap()
	{
		dev2cap.each { k, v ->
			println k + "," + v.join(",")
		}
	}
	
	def dump_Cap2Dev()
	{
		cap2dev.each { k, v ->
			println k + "," + v.join(",")
		}
	}
	
	def comb_test(s)
	{
		/*def input = new LinkedHashMap<String, Vector<String>>()
		input.put("c1", new Vector<String>(Arrays.asList("d1", "d2", "d3")))
		input.put("c2", new Vector<String>(Arrays.asList("d4", "d5", "d6")))
		//input.put("c3", new Vector<String>(Arrays.asList("d7", "d8")))
		
		Utils.allUniqueCombinations(input).each { comb ->
			println Arrays.toString(comb)
		}*/
		
		
	}
			
	def summarize()
	{				
		log.append "summary"
		log.append "cmd overpriv:" + numCmdOverpriv
		log.append "attr overpriv:" + numAttrOverpriv
		log.append "num reflection:" + numReflection
		log.append "total:" + numTotalOverpriv
		
		log.append "type2 overprivilege total:" + type2_numCaps
		log.append "samename_flags:" + samename_flags
		log.append "type2 cmd/attr uses:" + type2_cmdattr_uses 
		
		log.append "numSendSms: " + numSendSms
		log.append "numOAuth: " + numOAuth
		log.append "numInternet: " + numInternet
	}
	
	def computeType2Overprivilege(InsnVisitor insnVis, ArrayList<String> declaredMethods)
	{
		def reqCaps = insnVis.requestedCaps.intersect(allCapsList)
		Map supportedCapDevs = new HashMap()
		
		reqCaps.each { cap ->
			//find out which devices support this cap
			def devicesForCap = cap2dev[cap]
			supportedCapDevs[cap] = devicesForCap
		}
		
		//now for each cap, we have a set of devices that expose that cap
		//now we must test all combinations
		//e.g. suppose my app asks for c1 and c2
		//say c1 is exposed by d1, d2
		//say c2 is exposed by d3, d4
		//we must compute the number of unused caps for the combinations
		//cartesian product:
		// (c1, c2) = { (d1, d3), (d1, d4), (d2, d3), (d3, d4) }
		//if the app uses atleast 1 command or attribute from a cap
		//we assume that, that cap is "fully used". We do this since
		//in type 2 we are only interested in the number of extraneous
		//capabilities
		def rawCaps = new LinkedHashMap<String, Vector<String>>()
		
		//basically indicates how many different types of device handlers (and hence devices) a setup might have
		def cutoff = 5
		def cutoff_caps = 8
		
		//second stage cutoff. if we have too many caps in an app, we need to reduce cutoff value
		if(supportedCapDevs.keySet()?.size() > cutoff_caps)
			cutoff = 2
		
		supportedCapDevs.each { cap, listOfDevs ->
			
			if(listOfDevs != null)
			{
				def size = listOfDevs.size()
				
				if(size > cutoff)
					size = cutoff
				
				//we try to always compute minimal bound on overprivilege
				//so when selecting which devices when pruning the list of devices
				//we select devices that implement the minimum number of capabilities
				rawCaps.put(cap, selectMinimalDevicesOfSize(listOfDevs, size - 1))
			}
		}
		
		def allUniqueCombs = Utils.allUniqueCombinations(rawCaps).toList()
		def hdrCaps = allUniqueCombs[0]
		allUniqueCombs.remove(0)
		
		println "done computing combinations"
		
		//figure out what the app uses
		def calledCmdAttr = getCalledMethodsProps(insnVis)
		def calledMethods = calledCmdAttr[0]
		def calledProps = calledCmdAttr[1]
		def subAttrs = calledCmdAttr[2]
		def reqCmdAttrs = getCmdAttr(reqCaps)
		def reqCmds = reqCmdAttrs[0]
		def reqAttrs = reqCmdAttrs[1]
		
		def comb2overpriv = new ArrayList()
		
		//for each combination possibility, we compute resultant set of capabilities
		//and finally compute the set of unused caps
		allUniqueCombs.each { auc ->
			def univOfCapsAtThisPoint = new ArrayList()
			auc.each { aDevice ->
				univOfCapsAtThisPoint.addAll(dev2cap[aDevice])
			}
			
			//println "For combination, " + Arrays.toString(auc)
			def allUniqueCapsAtPoint = univOfCapsAtThisPoint.toSet()
			
			//for each capability from allUniqueCapsAtPoint
			//we need to test whether the app calls any method from it
			//if the app does call a method from a particular cap
			//we filter that cap out, assuming that the _entire_ cap
			//is used. We do this as we are only interested in completely
			//unused caps for type 2 overprivilege
			def type2OverprivCaps = new ArrayList()
			
			allUniqueCapsAtPoint.each { ucap ->
				def cmdsAttrs = getCmdAttr([ucap])
				
				def allCmds = cmdsAttrs[0].toSet()
				def allAttrs = cmdsAttrs[1].toSet()
				
				if(allCmds.intersect(calledMethods.toSet()).toList().size() == 0 &&
					allAttrs.intersect(calledProps.toSet()).toList().size()== 0 &&
					(allCmds.size() > 0 || allAttrs.size() > 0)) //there should be atleast some cmds/attrs
				{
					
					type2OverprivCaps.add(ucap)
				}
				else
				{
					//the app is using some cmd or attr
				}
			}
			
			//at this point, type2OverprivCaps contains unused caps
			//for this particular combination
			SimpleContainer sc = new SimpleContainer(auc, type2OverprivCaps)
			comb2overpriv.add(sc)
			
		}
		
		//now select the minimum amount of extraneous caps
		//and report that as the type 2 overprivilege for this app
		
		comb2overpriv.sort { it -> it.two?.size() }
		
		/*comb2overpriv.each { item ->
			println Arrays.toString(item.one)
			println Arrays.toString(item.two)
		}*/
		
		def minOverprivCaps = comb2overpriv[0]
		
		if(minOverprivCaps.two?.size() > 0)
		{
		
			log.append "type2 overprivilege unused caps:"
			log.append "type2 driver combination: " + minOverprivCaps.one
			log.append Arrays.toString(minOverprivCaps.two)
			
			type2_numCaps += 1
		}
		
		//part 2: compute whether the app is using any type 2 cmd/attributes
		//we want to compute the set of ALL cmds/attrs possible for this
		//particular app using the set of devicehandlers that we have
		/*def universeCaps = new ArrayList()
		supportedCapDevs.each { cap, listOfDevs ->
			//for each cap the app asked, we get the list
			//of devices that support that cap. this is listOfDevs.
			//then for all the caps in each of these devs, we
			//get all the cmds and attributes.
			//this is repeated for all caps the app asked for.
			//if the app is using any of these computed
			//cmds and attrs, then it measn app is using type2
			//overpriv for legitimate reasons
			
			listOfDevs?.each { dev ->
				def capsForDev = dev2cap[dev]
				universeCaps.addAll(capsForDev)
			}
		}
		
		//IMPORTANT: we must filter out the requested Caps
		def uniqueUniverseCaps = universeCaps.toSet()
		reqCaps.each { rCap ->
			uniqueUniverseCaps.remove(rCap)
		}
		
		//this represents all the capability cmds/attrs
		//the app can call beyond what it requested
		def universeUniqueCmdAttr = getCmdAttr(uniqueUniverseCaps)
		def univUniqueCmds = universeUniqueCmdAttr[0]
		def univUniqueAttrs = universeUniqueCmdAttr[1]
		
		//does the app call anything beyond its requested items?
		def calledCmds = calledMethods.toSet().intersect(allCommandsList)
		def calledAttrs = calledProps.toSet().intersect(allPropsList)
		def type2Cmds = univUniqueCmds.toSet().intersect(calledCmds)
		def type2Attrs = univUniqueAttrs.toSet().intersect(calledAttrs)
		
		if(type2Cmds.size() > 0)
		{
			log.append "type 2 cmds used:"
			type2Cmds.each { c -> log.append c }
		}
		if(type2Attrs.size() > 0)
		{
			log.append "type 2 attrs used:"
			type2Attrs.each { a -> log.append a }
		}
		
		if(type2Cmds.size() > 0 || type2Attrs.size() > 0)
			type2_cmdattr_uses += 1
		*/
	}
	
	//this sorts listOfDevices by number of caps implemented
	//and returns the smallest "count" number of items
	def selectMinimalDevicesOfSize(def listOfDevices, def count)
	{
		def devWithCount = new ArrayList()
		listOfDevices.each { dev ->
			int size = (dev2cap[dev] != null) ? dev2cap[dev].size() : 0
			SimpleContainer sc = new SimpleContainer(dev, size)
			devWithCount.add(sc)
		}
		
		devWithCount.sort { it -> it.two }
		
		//notice spread operator. weird thing but useful
		return devWithCount*.one[0..count]
	}
	
	def processApp(InsnVisitor insnVis, ArrayList<String> declaredMethods)
	{
		
		def calledCmdAttr = getCalledMethodsProps(insnVis)
		def calledMethods = calledCmdAttr[0].toSet()
		def calledProps = calledCmdAttr[1].toSet()
		def subAttrs = calledCmdAttr[2].toSet()
		
		def reqCaps = insnVis.requestedCaps.intersect(allCapsList)
		def declaredGlobals = insnVis.declaredGlobalVars
								
		log.append "req caps: " + reqCaps.toSet()
		log.append "req cap size: " + reqCaps.size()
		
		def reqCmdAttrs = getCmdAttr(reqCaps)
		def reqCmds = reqCmdAttrs[0].toSet()
		def reqAttrs = reqCmdAttrs[1].toSet()
		
		log.append "requested commands:" + reqCmds
		log.append "requested attrs:" + reqAttrs
		
		//this means that the app asks for no capabilities at all
		if(reqCmds.toList().size() == 0 && reqAttrs.toList().size() == 0)
			return
			
		
		def reflIndex = calledMethods?.toList().any { v -> v?.contains("\$") }
		if(reflIndex)
		{
			log.append "Dynamic Method Invocation"
			OPAnalysisAST.this.append_manual()
			numReflection += 1
		}
				
		//we need to refine calledMethods and calledProps further
		//to ensure that the method or property is only invoked
		//on a variable representing an actual device. We do this by computing
		//a set of all "defined" methods in the program itself and excluding
		//those from our list of calledMethods. We follow the same logic
		//for attributes. Any globally defined variables are excluded from calledProps

		//Note that this computation appears after we've checked that the app requests
		//no capabilities of its own. That is, we only care about the following situation
		//if the app has asked for capabilities and it defines methods that have the same
		//names as known commands/attributes
		//1. first compute whether the declared methods in the app have names
		//same as known IoT device commands
		def sameNameCommands = allCommandsList.intersect(declaredMethods)
		
		if(sameNameCommands.size() > 0 && (reqCmds.toList().size() > 0 || reqAttrs.toList().size() > 0))
		{
			log.append "Some app-defined methods have the same name as known IoT commands:"
			OPAnalysisAST.this.append_manual()
			sameNameCommands.each { it -> log.append it }
		}
			
		//2. compute the same thing for properties (attributes)
		def sameNameAttrs = allPropsList.intersect(declaredGlobals)
		if(sameNameAttrs.size() > 0 && (reqCmds.toList().size() > 0 || reqAttrs.toList().size() > 0))
		{
			log.append "Some app-defined globally-scoped properites have the same name as known IoT attributes:"
			OPAnalysisAST.this.append_manual()
			sameNameAttrs.each { it -> log.append it }
		}
		
		if(sameNameCommands.size() > 0 || sameNameAttrs.size() > 0)
			samename_flags += 1
						
		//now filter the called* arrays to only include
		//items that are actual commands or attributes
		//Note that this is computed against the _requested_
		//cmds (and attrs)
		
		def filteredCalledMethods = calledMethods.intersect(reqCmds)
		def filteredCalledProps = calledProps.intersect(reqAttrs)
		
		//these represent a set of called method and attrs as computed
		//using _ALL_ known commands and attributes
		def filteredExtraneousCalledMethods = calledMethods.intersect(allCommandsList)
		def filteredExtraneousCalledProps = calledProps.intersect(allPropsList)
		//There are there cmd and attribute uses _other_ than those
		//available thru reqCmds and reqAttrs. If it is, then it means
		//that the app is actually using cmds/attrs other than the primary
		//requested capability (using a type 2 overprivilege). We compute this now
		def type2Uses_Cmds = filteredExtraneousCalledMethods.toSet() - filteredCalledMethods.toSet()
		def type2Uses_Attrs = filteredExtraneousCalledProps.toSet() - filteredCalledProps.toSet()
		
		//this only matters if the app asked for any caps at all
		//in the first place!
		if(insnVis.usesAddChildDevice)
		{
			log.append "addChildDevice usage"
			OPAnalysisAST.this.append_manual()
		}
		
		if (type2Uses_Cmds.toList().size() > 0)
		{
			log.append "type 2 command uses"
			type2Uses_Cmds.each { it -> log.append it }
		}
		
		if(type2Uses_Attrs.toList().size() > 0)
		{
			log.append "type 2 attr uses"
			type2Uses_Attrs.each { it -> log.append it }
		}
		
		if(type2Uses_Cmds.size() > 0 || type2Uses_Attrs.size() > 0)
			type2_cmdattr_uses += 1

		log.append "called cap-methods by app"
		filteredCalledMethods.each { it -> log.append it }
		
		log.append "called cap-props by app"
		filteredCalledProps.each { it -> log.append it }
		
		log.append "attribute uses through subscriptions"
		subAttrs.toSet().each{ log.append it}
		
		//compute overprivilege
		def cmdOverpriv = reqCmds.toSet() - filteredCalledMethods.toSet()
		def attrOverpriv = (reqAttrs.toSet() - filteredCalledProps.toSet())
		
		log.append "cmd overpriv:" + cmdOverpriv
		log.append "attr overpriv:" + attrOverpriv
		
		if(cmdOverpriv.toList().size() > 0)
			numCmdOverpriv += 1
			
		if(attrOverpriv.toList().size() > 0)
			numAttrOverpriv += 1
			
		if(cmdOverpriv.toList().size() > 0 || attrOverpriv.toList().size() > 0)
		{
			numTotalOverpriv += 1
			
			log.append("^^^^^^^^-OVERPRIVILEGED-^^^^^^^")		
		}
		
		//basic statistics
		if(insnVis.usesSendSms)
		{
			numSendSms += 1
		}
			
		if(insnVis.usesOAuth)
		{
			numOAuth += 1
		}
		
		if(insnVis.usesInternet)
		{
			numInternet += 1
		}
	}
	
	def getCalledMethodsProps(InsnVisitor insnVis)
	{
		def calledMethods = insnVis.calledMethods.toList()
		def reqCaps = insnVis.requestedCaps.toList()
		
		def subAttrs = insnVis.getSubscriptionAttrs().toList() //attribute uses via subscriptions (events)
		
		/*
		 * Attribute uses are computed via
		 * 1. direct accesses (deviceVariable.attribute)
		 * 2. accesses thru subscriptions (event handlers, subscribe(deviceVar, attribute, handler))
		 * 3. device.* methods -- current<Attribute>, <attribute>State, currentState(attribute),
		 * currentValue(attribute)
		 *
		 * events(), eventsBetween(...), eventsSince(...):
		 * These methods access past event data of a device. Since there are no rules
		 * about what exactly goes into an event, we asusme the worst and all
		 * attributes are accessed. So we add all possible attributes (derived from
		 * the set of caps this app asks for) to the list of used attributes
		 * 
		 * statesBetween(attr, ...),
		 * statesSince(attr, ...)
		 * 
		 * These access past device state values (attr) and take the first
		 * param as attr name. Our instruction visitor computes these
		 * and has automatically added them to calledProps
		 * 
		 */
		
		if("events" in calledMethods ||
			"eventsBetween" in calledMethods ||
			"eventsSince" in calledMethods)
		{
			def cA = getCmdAttr(reqCaps)
			
			subAttrs.addAll(cA[1]) //add all known attributes for the caps that this app asked for
			
		}
		
		
		def calledProps_initial = insnVis.calledProps.toList() + subAttrs //all attribute uses thru subscriptions
		
		//handle attribute accesses of the form current<Attribute> or <attribute>State
		//do this by looking for substring "current" or "attribute" and then removing that
		//substring so that later during intersect, the attribute is considered.
		def replacementList = new ArrayList()
		def deletionList = new ArrayList()
		calledProps_initial.each  { attr ->
			if(attr?.contains("current"))
			{
				deletionList.add(attr)
				replacementList.add(attr.replace("current", "").toLowerCase())
			}
			
			if(attr?.contains("State"))
			{
				deletionList.add(attr)
				replacementList.add(attr.replace("State", "").toLowerCase())
			}
		}
		
		def calledProps = (calledProps_initial - deletionList) + replacementList
		
		def processed_calledMethods = calledMethods?.collect { item -> def x = item?.toLowerCase()
			x
		}
		
		def processed_calledProps = calledProps?.collect { item -> def x = item?.toLowerCase()
			x
		}
		
		def processed_subAttrs = subAttrs?.collect { item -> def x = item?.toLowerCase()
			x
		}
		
		return [
			processed_calledMethods.toSet(),
			processed_calledProps.toSet(),
			processed_subAttrs.toSet()
		]
		
	}
	
	//given a list of cap name, get all its commands and attributes as lists
	def getCmdAttr(def caps)
	{
		def cmds = new ArrayList()
		def attrs = new ArrayList()
		
		caps.each { capname ->
			
			def listOfCmds = allCommands[capname]
									
			def values = listOfCmds?.split(" ")
			values.each { cmds.add it }
			
			def listOfAttrs = allProps[capname]
			
			def values2 = listOfAttrs?.split(" ")
			values2.each { attrs.add it }
		}
		
		def combined = [cmds, attrs]
		return combined
	}
			
	def append_manual()
	{
		log.append "--manual investigation required--"
	}
	
	class SimpleContainer
	{
		public def one
		public def two
		
		public SimpleContainer(def o, def t)
		{
			one = o
			two = t
		}
	}
}
