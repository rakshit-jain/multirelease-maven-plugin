package org.codehaus.openxma.mojo.multirelease.mojo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.codehaus.openxma.mojo.multirelease.exception.ProcessException;
import org.codehaus.openxma.mojo.multirelease.util.DependencyMapper;
import org.codehaus.openxma.mojo.multirelease.util.MavenReleasePluginExecutor;
import org.codehaus.openxma.mojo.multirelease.util.PropertyResolver;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * 1. Prepare for a release in SCM. Steps through several phases to ensure the POM is ready to be released and then
 * prepares SCM to eventually contain a tagged version of the release and a record in the local copy of the parameters.
 * 2. Perform a release from SCM by the tag representing the previous release in the working copy created by
 * <tt>prepare</tt>
 */
@Mojo(name = "release", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, aggregator = true)
public class PreparePerfomReleaseMojo extends AbstractReleaseMojo {

	/**
	 * Resume a previous release attempt from the point where it was stopped.
	 */
	@Parameter(defaultValue = "true", property = "resume")
	private boolean resume;

	/**
	 * Map having Group ID and Artifact ID as key and project version before release as value.
	 */
	private final Map<String, String> preReleaseVersion = new HashMap<String, String>();

	/**
	 * Map having Group ID and Artifact ID as key and project version after release as value.
	 */
	private final Map<String, String> postReleaseVersion = new HashMap<String, String>();

	// Constants
	private final static String PRE_PHASE = "preReleasePhase";
	private final static String POST_PHASE = "postReleasePhase";
	private final static String DRY_RUN_PHASE = "dryRunPhase";
	private final static String LAST_BUILT = "lastBuilt";

	private final Properties properties = new Properties();

	private final MavenReleasePluginExecutor executor = new MavenReleasePluginExecutor();

	private File releasePropertyFile = null;

	/**
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			mergeProperties();
			getLog().info("Building dependency tree.");
			List<DependencyMapper> projects = getBuildOrder();
			releasePropertyFile = getReleasePropertyfile();
			if (resume) {
				projects = getResumableProjects(projects);
			}
			executeReleasePlugin(projects);
			cleanUpAfterRelease(releasePropertyFile);
			printReleaseSummary(projects);
		} catch (IOException e) {
			getLog().error("Plugin execution failed beacuse of I/O error \n", e);
			throw new MojoExecutionException("Plugin execution failed beacuse of I/O error\n", e);
		} catch (XmlPullParserException e) {
			getLog().error("Plugin execution failed because of error while writing POM file \n", e);
			throw new MojoExecutionException("Plugin execution failed because of error while writing POM file\n", e);
		} catch (ProcessException e) {
			getLog().error("Plugin execution failed because of process executions failure\n", e);
			throw new MojoExecutionException("Plugin execution failed because of process executions failure\n", e);
		} catch (ScmException e) {
			getLog().error("Plugin execution failed because of SCM error\n", e);
			throw new MojoExecutionException("Plugin execution failed because of SCM error\n", e);
		}
	}

	/**
	 * Executes multi-release release plugin.
	 * 
	 * @param projects list of {@link DependencyMapper}
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws XmlPullParserException the xml pull parser exception
	 * @throws MojoExecutionException the mojo execution exception
	 * @throws ProcessException the process exception
	 * @throws ScmException the scm exception
	 */
	private void executeReleasePlugin(List<DependencyMapper> projects)
			throws IOException, XmlPullParserException,
			MojoExecutionException, ProcessException, ScmException {
		// Dry run successfully executed
		PropertyResolver propertyResolver = PropertyResolver.getInstance();
		for (DependencyMapper dependencyMapper : projects) {

			MavenProject mavenProject = dependencyMapper.getMavenProject();
			Map<String, String> projectProperties = propertyResolver.getProjectProperties(
					mavenProject.getGroupId(), mavenProject.getArtifactId());
			String username = projectProperties.get("username");
			String password = projectProperties.get("password");
			String scmcommentPrefix = projectProperties.get("scmCommentPrefix");
			updateReleaseVersionMap(dependencyMapper, preReleaseVersion);
			updateDependencyVersion(dependencyMapper, PRE_PHASE, username, password, scmcommentPrefix);
			getLog().info("Preparing Release.");
			executor.prepareRelease(mavenProject, projectProperties);

			// Write pre-release version in property file.
			addProperty(dependencyMapper);

			// Read the updated version.
			Model model = readPomFile(mavenProject.getOriginalModel().getPomFile());
			if (model.getVersion() == null) {
				if (mavenProject.getParent() != null) {
					model = readPomFile(mavenProject.getParent().getOriginalModel().getPomFile());
				}
			}
			dependencyMapper.getMavenProject().getOriginalModel().setVersion(model.getVersion());
			dependencyMapper.getMavenProject().setVersion(model.getVersion());
			updateReleaseVersionMap(dependencyMapper, postReleaseVersion);
			updateDependencyVersion(dependencyMapper, POST_PHASE, username, password, scmcommentPrefix);

		}
	}

	/**
	 * Updates release version map with the given project and its child project.
	 * @param dependencyMapper the dependency mapper
	 * @param mavenProject the maven project
	 */
	private void updateReleaseVersionMap(DependencyMapper dependencyMapper, Map<String, String> releaseVersionMap) {
		MavenProject mavenProject = dependencyMapper.getMavenProject();
		releaseVersionMap.put(
				mavenProject.getGroupId() + "." + mavenProject.getArtifactId(),
				mavenProject.getVersion());
		for (MavenProject project : dependencyMapper.getChildProject()) {
			releaseVersionMap.put(
					project.getGroupId() + "." + project.getArtifactId(),
					mavenProject.getVersion());
		}
	}

	/**
	 * Update dependency version in POM file. IF phase is PRE release then the dependency versions are updated to use
	 * release versions. If phase is POST release then dependencies are updated to use SNAPSHOT version.
	 * 
	 * @param dependencyMapper {@link DependencyMapper}
	 * @param phase execution phase of the project.
	 * @param username the username
	 * @param password the password
	 * @return true, if successful
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ScmException the scm exception
	 */
	private boolean updateDependencyVersion(DependencyMapper dependencyMapper,
			String phase, String username, String password, String scmCommentPrefix) throws FileNotFoundException,
			IOException, ScmException {
		boolean dependencyUpdated = false;

		List<Dependency> dependencies = null;
		// If project is multi module then use dependency management section.
		if (dependencyMapper.getMavenProject().getOriginalModel().getDependencyManagement() == null) {
			dependencies = dependencyMapper.getMavenProject()
					.getOriginalModel().getDependencies();
		} else {
			dependencies = dependencyMapper.getMavenProject()
					.getOriginalModel().getDependencyManagement()
					.getDependencies();
		}
		for (Dependency dependency : dependencies) {
			String version = null;
			if (PRE_PHASE.equals(phase)) {
				version = preReleaseVersion.get(
						dependency.getGroupId()
								+ "." + dependency.getArtifactId());
				// Update dependency to release version for prepare phase.
				if (version != null) {
					version = version.replace("-SNAPSHOT", "");
				}
			} else if (POST_PHASE.equals(phase)) {
				version = postReleaseVersion.get(dependency
						.getGroupId() + "." + dependency.getArtifactId());
			} else if (DRY_RUN_PHASE.equals(phase)) {
				version = preReleaseVersion.get(
						dependency.getGroupId()
								+ "." + dependency.getArtifactId());
			}
			if (version != null) {
				dependencyUpdated = true;
				// dependency version is defined in property tag of POM file.
				Matcher matcher = propertyTagPattern.matcher(dependency.getVersion());
				if (matcher.find()) {
					String key = matcher.group(1);
					dependencyMapper.getMavenProject()
							.getOriginalModel().getProperties().setProperty(key, version);
				} else {
					dependency.setVersion(version);
				}
			}
		}
		// Update POM if dependency is updated.
		if (dependencyUpdated) {
			writePOM(dependencyMapper.getMavenProject());
			commitModifiedModel(dependencyMapper, username, password, scmCommentPrefix);
		}
		return dependencyUpdated;
	}

	/**
	 * Gets the list of projects which can be resumed from previous release attempt.
	 * @param availableProjects the available projects
	 * @return the resumable projects
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws MojoExecutionException
	 */
	private List<DependencyMapper> getResumableProjects(List<DependencyMapper> availableProjects)
			throws FileNotFoundException, IOException, MojoExecutionException {
		List<DependencyMapper> resumableProjects = new ArrayList<DependencyMapper>();
		boolean lastBuiltProjectFound = false;
		if (releasePropertyFile.exists()) {
			loadPreReleaseProperties();
			String lastBuiltProject = properties.getProperty(LAST_BUILT);
			for (DependencyMapper dependencyMapper : availableProjects) {
				if (lastBuiltProjectFound) {
					resumableProjects.add(dependencyMapper);
				} else {
					String key = dependencyMapper.getMavenProject().getGroupId()
							+ "." + dependencyMapper.getMavenProject().getArtifactId();
					updateReleaseVersionMap(dependencyMapper, postReleaseVersion);
					if (key.equals(lastBuiltProject)) {
						lastBuiltProjectFound = true;
					}
				}
			}
		}
		if (resumableProjects.isEmpty()) {
			postReleaseVersion.clear();
			return availableProjects;
		} else {
			getLog().info("Resuming build from the previous point.");
			return resumableProjects;
		}
	}

	/**
	 * Adds the property with group ID and artifact ID as key and version as value..
	 * 
	 * @param dependencyMapper {@link DependencyMapper}
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void addProperty(DependencyMapper dependencyMapper) throws FileNotFoundException, IOException {
		FileOutputStream fileOutputStream = null;
		try {
			String key = dependencyMapper.getMavenProject().getGroupId()
					+ "." + dependencyMapper.getMavenProject().getArtifactId();
			properties.setProperty(key, dependencyMapper.getMavenProject().getVersion());
			properties.setProperty(LAST_BUILT, key);
			for (MavenProject mavenProject : dependencyMapper.getChildProject()) {
				key = mavenProject.getGroupId() + "." + mavenProject.getArtifactId();
				properties.setProperty(key, mavenProject.getVersion());
			}
			fileOutputStream = new FileOutputStream(releasePropertyFile);
			properties.store(fileOutputStream, key);
		} finally {
			try {
				if (fileOutputStream != null) {
					fileOutputStream.close();
				}
			} catch (IOException e) {
				// Do nothing
			}
		}
	}

	/**
	 * Load release properties into preReleaseVersion map.
	 * 
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private void loadPreReleaseProperties() throws FileNotFoundException, IOException {
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(releasePropertyFile);
			properties.load(fileInputStream);
			for (Entry<Object, Object> entry : properties.entrySet()) {
				preReleaseVersion.put(entry.getKey().toString(), entry.getValue().toString());
			}
		} finally {
			try {
				if (fileInputStream != null) {
					fileInputStream.close();
				}
			} catch (IOException e) {
				// Do nothing
			}
		}
	}

	/**
	 * Read POM file from the file system.
	 * 
	 * @param pomFile the POM file to read.
	 * @return the model defined in POM.
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws XmlPullParserException the xml pull parser exception
	 */
	private Model readPomFile(File pomFile) throws FileNotFoundException, IOException, XmlPullParserException {
		FileInputStream fileInputStream = null;
		try {
			MavenXpp3Reader reader = new MavenXpp3Reader();
			fileInputStream = new FileInputStream(pomFile);
			Model model = reader.read(fileInputStream);
			return model;
		} finally {
			try {
				if (fileInputStream != null) {
					fileInputStream.close();
				}
			} catch (IOException e) {
				// Do nothing.
			}
		}
	}

	/**
	 * Prints the release summary at the end of successful release.
	 */
	private void printReleaseSummary(List<DependencyMapper> projects) {
		String summary = "\nRelease Summary:\n";
		for (DependencyMapper dependencyMapper : projects) {
			summary = summary.concat("-------------------------");
			summary = summary.concat("\n Name: " + dependencyMapper.getMavenProject().getName());
			summary = summary.concat("\n Group ID: " + dependencyMapper.getMavenProject().getGroupId());
			summary = summary.concat("\n Artifact Id: " + dependencyMapper.getMavenProject().getArtifactId());
			String key = dependencyMapper.getMavenProject().getGroupId() + "."
					+ dependencyMapper.getMavenProject().getArtifactId();
			summary = summary.concat("\n Release Version: " + preReleaseVersion.get(key).replace("-SNAPSHOT", ""));
			summary = summary.concat("\n Next Developmentversion: " + postReleaseVersion.get(key) + "\n");
			for (MavenProject mavenProject : dependencyMapper.getChildProject()) {
				summary = summary.concat("\n Name: " + mavenProject.getName());
				summary = summary.concat("\n Group ID: " + mavenProject.getGroupId());
				summary = summary.concat("\n Artifact Id: " + mavenProject.getArtifactId());
				key = mavenProject.getGroupId() + "." + mavenProject.getArtifactId();
				summary = summary.concat("\n Release Version: " + preReleaseVersion.get(key).replace("-SNAPSHOT", ""));
				summary = summary.concat("\n Next Developmentversion: " + postReleaseVersion.get(key) + "\n");
			}
			summary = summary.concat("-------------------------");
		}
		getLog().info(summary);
	}

	/**
	 * Merge properties defined by user with class variablesS.
	 * 
	 * @throws MojoExecutionException the mojo execution exception
	 * @throws MojoFailureException the mojo failure exception
	 */
	private void mergeProperties() throws MojoExecutionException, MojoFailureException {
		getLog().info("Reading defined properties.");
		super.execute();
		Map<String, String> properties = PropertyResolver.getInstance().getMergedProperties();
		String property = properties.get("resume");
		if (property != null) {
			resume = Boolean.valueOf(property);
		}
	}

}
