package dtu.significance;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.contexts.cli.CLIPluginContext;
import org.processmining.framework.boot.Boot;
import org.processmining.framework.plugin.annotations.Bootable;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.util.CommandLineArgumentList;
import org.processmining.plugins.etm.CentralRegistry;
import org.processmining.plugins.etm.fitness.TreeFitnessAbstract;
import org.processmining.plugins.etm.fitness.metrics.FitnessReplay;
import org.processmining.plugins.etm.model.narytree.NAryTree;
import org.processmining.plugins.etm.model.narytree.conversion.ProcessTreeToNAryTree;
import org.processmining.processtree.ProcessTree;
import org.processmining.processtree.ptml.importing.PtmlImportTree;

import dtu.util.LogSerializer;
import nl.tue.astar.AStarThread.Canceller;

public class SignificanceTester {
	
	private static XFactory factory = new XFactoryNaiveImpl();
	
	@Plugin(name = "CLI", parameterLabels = {}, returnLabels = {}, returnTypes = {}, userAccessible = false)
	@Bootable
	public void main(CommandLineArgumentList commandlineArguments) throws Exception {
		
		if (commandlineArguments.size() != 2) {
			System.err.println("Error: provide 2 parameters to the command line:");
			System.err.println(" - path to the .ptml (process tree) file");
			System.err.println(" - path to the .xes (process tree) file");
			System.exit(1);
		}
		
		String modelPath = commandlineArguments.get(0);
		String logPath = commandlineArguments.get(1);
		
		CLIContext globalContext = new CLIContext();
		CLIPluginContext context = new CLIPluginContext(globalContext, "c");
		
		LogSerializer.serialize("Importing model...");
		ProcessTree processTree = context.tryToFindOrConstructFirstNamedObject(
				ProcessTree.class,
				PtmlImportTree.class.getAnnotation(Plugin.class).name(),
				null,
				null,
				modelPath);
		LogSerializer.serialize("Done!");
		
		LogSerializer.serialize("Importing log...");
		XLog log = context.tryToFindOrConstructFirstNamedObject(
				XLog.class,
				null,
				null,
				null,
				logPath);
		LogSerializer.serialize("Done!");
		
		for(XTrace trace : log) {
			LogSerializer.serialize("Checking trace `" + traceToString(trace) + "'");
			if (!isConformant(processTree, trace)) {
				LogSerializer.serialize("Trace is not conformant already");
				continue;
			}
			int redundancy = 0;
			XTrace witness = null;
			double time = System.currentTimeMillis();
			for (XTrace partialTrace : getAllSubTraces(trace)) {
				int hole = trace.size() - partialTrace.size();
				if (isConformant(processTree, partialTrace)) {
					if (redundancy < hole) {
						redundancy = hole;
						witness = partialTrace;
					}
				}
			}
			time = System.currentTimeMillis() - time;
			if (redundancy == 0) {
				LogSerializer.serialize("Trace has no significance (" + time + " ms)");
			} else {
				LogSerializer.serialize("Trace has a significance of " + redundancy + ", witness is `" + traceToString(witness) + "' (" + time + " ms)");
			}
		}
		
		LogSerializer.print();
		System.exit(0);
	}
	
	private Set<XTrace> getAllSubTraces(XTrace trace) {
		Set<XTrace> set = new HashSet<XTrace>();
		getAllSubTraces(trace, set);
		return set;
	}
	
	private Set<XTrace> getAllSubTraces(XTrace trace, Set<XTrace> partialTraces) {
		for(int i = 0; i < trace.size(); i++){
			XTrace partialTrace = factory.createTrace();
			for (int f = 0; f < i; f++) partialTrace.add(trace.get(f));
			for (int f = i + 1; f < trace.size(); f++) partialTrace.add(trace.get(f));
			
			if (partialTrace.size() != 0) {
				if (partialTraces.add(partialTrace)) {
					getAllSubTraces(partialTrace, partialTraces);
				}
			}
		}
		return partialTraces;
	}
	
	private String traceToString(XTrace trace) {
		String s = "";
		for (XEvent e : trace) {
			s += XConceptExtension.instance().extractName(e) + ", ";
		}
		return s.substring(0, s.length() - 2);
	}
	
	private boolean isConformant(ProcessTree processTree, XTrace trace) {
		return getFitness(processTree, trace) == 1d;
	}
	
	private double getFitness(ProcessTree processTree, XTrace trace) {
		PrintStream oldOut = System.out;
		PrintStream oldErr = System.err;
		System.setOut(new Interceptor(oldOut));
		System.setErr(new Interceptor(oldErr));
		
		XLog log = factory.createLog();
		log.add(trace);
		CentralRegistry registry = new CentralRegistry(log, new XEventNameClassifier(), new Random(1));
		ProcessTreeToNAryTree converter = new ProcessTreeToNAryTree(registry.getEventClasses());
		NAryTree tree = converter.convert(processTree);
		
		TreeFitnessAbstract tfa = new FitnessReplay(registry,  new DummyCanceler());
		double fitness = tfa.getFitness(tree, Arrays.asList(tree));
		
		System.setOut(oldOut);
		System.setErr(oldErr);
		
		return fitness;
	}
	
	public static void main(String[] args) throws Exception {
		Boot.boot(SignificanceTester.class, CLIPluginContext.class, args);
	}
}

class Interceptor extends PrintStream {
	public Interceptor(OutputStream out) {
		super(out, true);
	}

	@Override
	public void print(String s) { }
}


class DummyCanceler implements Canceller {
	public boolean isCancelled() {
		return false;
	}
}