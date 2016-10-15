package org.codehaus.openxma.mojo.multirelease.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.shared.invoker.PrintStreamLogger;
import org.codehaus.openxma.mojo.multirelease.exception.ProcessException;


/**
 * Executes a process depending on the underlying OS. If an error occurs while executing the process
 * {@link ProcessException} is thrown
 * @author Rakshit Jain
 * 
 */
public class ProcessLauncher {

	private static String OS = System.getProperty("os.name").toLowerCase();
	/** Instance logger */
	private final static PrintStreamLogger log = new PrintStreamLogger();

	/**
	 * Execute process depending on the underlying OS.
	 * @param command Command to be executed.
	 * @throws IOException
	 * @throws ProcessException Exception thrown if a Build error is encounterd.
	 */
	public void executeProcess(String command) throws IOException, ProcessException {
		List<String> commandList = new ArrayList<String>();
		if (isUnix()) {
			commandList.add("/bin/bash");
			commandList.add("-c");
			commandList.add(command);
		}
		else if (isWindows()) {
			commandList.add("cmd");
			commandList.add("/c");
			commandList.add(command);
		}
		executeCommand(commandList);
	}

	private void executeCommand(List<String> command) throws IOException, ProcessException {
		InputStreamReader inputStreamReader = null;
		BufferedReader inputReader = null;
		try {
			log.info("Executing process with command " + command);
			ProcessBuilder builder = new ProcessBuilder(command);
			builder.redirectErrorStream(true);
			Process process = builder.start();
			inputStreamReader = new InputStreamReader(process.getInputStream());
			inputReader = new BufferedReader(inputStreamReader);
			String line = "";
			boolean buildFailed = false;
			while ((line = inputReader.readLine()) != null) {
				if (line.contains("BUILD FAILURE")) {
					buildFailed = true;
				}
				System.out.println(line);
			}
			if (buildFailed) {
				throw new ProcessException();
			}
		} finally {
			try {
				if (inputStreamReader != null) {
					inputStreamReader.close();
				}
				if (inputReader != null) {
					inputReader.close();
				}
			} catch (IOException e) {
				// Do nothing.
			}
		}
	}

	private boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
	}

	private boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}
}
