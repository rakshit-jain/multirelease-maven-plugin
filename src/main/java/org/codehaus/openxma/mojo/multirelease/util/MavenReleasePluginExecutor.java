package org.codehaus.openxma.mojo.multirelease.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.openxma.mojo.multirelease.exception.ProcessException;
import org.codehaus.openxma.mojo.multirelease.launcher.ProcessLauncher;
import org.codehaus.openxma.mojo.multirelease.pojo.Version;

/**
 * Class to execute maven release plugin.
 * @author Rakshit Jain
 * 
 */
public class MavenReleasePluginExecutor {

	private final ProcessLauncher processLauncher = new ProcessLauncher();
	private final PropertyResolver propertyResolver = PropertyResolver.getInstance();

	/**
	 * Runs the clean,prepare and release goal of maven release plugin.
	 * @param mavenProject {@link MavenProject}
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ProcessException
	 * @throws MojoExecutionException
	 */
	public void prepareRelease(MavenProject mavenProject, Map<String, String> projectProperties)
			throws FileNotFoundException, IOException, ProcessException, MojoExecutionException {
		String command = getPreparePerformReleaseCommand(mavenProject, projectProperties);
		processLauncher.executeProcess(command);
	}

	/**
	 * Runs the clean install goal on the given {@link MavenProject}
	 * 
	 * @param mavenProject the maven project
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ProcessException the process exception
	 */
	public void dryRun(MavenProject mavenProject) throws IOException, ProcessException {
		String pomPath = mavenProject.getOriginalModel().getPomFile()
				.getAbsolutePath();
		String command = null;
		command = getDryRunCommand(pomPath);
		processLauncher.executeProcess(command);
	}

	public void rollback(MavenProject mavenProject) throws IOException,
			ProcessException {
		String command = null;
		command = getRollbackCommand(mavenProject);
		processLauncher.executeProcess(command);
	}

	public void clean(List<MavenProject> projects) throws IOException, ProcessException {
		for (MavenProject mavenProject : projects) {
			String pomPath = mavenProject.getOriginalModel().getPomFile().getAbsolutePath();
			processLauncher.executeProcess("mvn release:clean -f" + pomPath);
		}
	}

	/**
	 * Gets the prepare perform release goal command. Parameters defined in multirelease.properties are added as system
	 * configuration in the command.
	 * @param releaseConfiguration {@link ReleaseConfiguration}
	 * @param mavenProject {@link MavenProject}
	 * @return the prepare perform release command
	 * @throws MojoExecutionException
	 */
	private String getPreparePerformReleaseCommand(MavenProject mavenProject, Map<String, String> projectProperties)
			throws MojoExecutionException {
		String pomPath = mavenProject.getOriginalModel().getPomFile()
				.getAbsolutePath();
		String releaseCommand = "mvn";
		String releaseGoal = " release:prepare release:perform -B -f" + pomPath;
		for (Entry<String, String> entry : projectProperties.entrySet()) {
			if (entry.getKey().equals("developmentVersion")) {
				checkDevelopmentVersion(mavenProject, entry);
			}
			releaseCommand = releaseCommand.concat(" -D" + entry.getKey() + "=\"" + entry.getValue() + "\"");
		}
		return releaseCommand.concat(releaseGoal);
	}

	/**
	 * Check development version is greater then current version of the project.
	 * @param mavenProject {@link MavenProject}
	 * @param entry the entry
	 * @throws MojoExecutionException the mojo execution exception
	 */
	private void checkDevelopmentVersion(MavenProject mavenProject, Entry<String, String> entry)
			throws MojoExecutionException {
		int result = Version.valueOf(entry.getValue().trim()).compareTo(Version.valueOf(mavenProject.getVersion().replace("-SNAPSHOT", "").trim()));
		if (result <= 0) {
			throw new MojoExecutionException("Development version of " + mavenProject.getName() + " should be greater then current version. The new development version is "
					+ entry.getValue().trim() + " and old version is " + mavenProject.getVersion().trim());
		}
	}

	/**
	 * Gets the dry run command.
	 * @param pomPath the path of the POM file.
	 * @return the dry run command
	 */
	private String getDryRunCommand(String pomPath) {
		String command = "mvn clean install -f" + pomPath;
		return command;
	}

	/**
	 * Gets the rollback command for the given POM path.
	 * @param pomPath absolute path of the POM file.
	 * @return the rollback command to be executed.
	 */
	private String getRollbackCommand(MavenProject mavenProject) {
		String pomPath = mavenProject.getOriginalModel().getPomFile()
				.getAbsolutePath();
		String releaseCommand = "mvn";
		String releaseGoal = " release:rollback -B -f" + pomPath;
		Map<String, String> properties = propertyResolver.getProjectProperties(mavenProject.getGroupId(),
				mavenProject.getArtifactId());
		for (Entry<String, String> entry : properties.entrySet()) {
			releaseCommand = releaseCommand.concat(" -D" + entry.getKey() + "=\"" + entry.getValue() + "\"");
		}
		return releaseCommand.concat(releaseGoal);
	}
}
