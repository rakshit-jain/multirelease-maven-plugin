package org.codehaus.openxma.mojo.multirelease.mojo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.codehaus.openxma.mojo.multirelease.scm.CustomScmManager;
import org.codehaus.openxma.mojo.multirelease.util.DependencyMapper;
import org.codehaus.openxma.mojo.multirelease.util.DependencyResolver;
import org.codehaus.openxma.mojo.multirelease.util.PropertyResolver;

public abstract class AbstractReleaseMojo extends AbstractMojo {

	/**
	 * List of projects in the reactor.
	 */
	@Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
	private List<MavenProject> reactorProjects;

	/**
	 * The project currently built.
	 */
	@Component
	private MavenProject parentProject;

	/**
	 * 
	 */
	@Component
	private MavenSession mavenSession;

	@Parameter(property = propertyFileKey)
	private String propertyFile;

	@Parameter(property = RELEASE_PROPERTY_KEY)
	private String releaseProperties;

	private final static String RELEASE_PROPERTIES = "release.properties";

	protected final Pattern propertyTagPattern = Pattern.compile("\\$\\{(.*)\\}");

	private final static String propertyFileKey = "property-location";

	private final static String RELEASE_PROPERTY_KEY = "release-property-location";

	public void execute() throws MojoExecutionException, MojoFailureException {
		File file = null;
		if (propertyFile != null) {
			String path = (String) mavenSession.getExecutionProperties().get(propertyFileKey);
			if (path != null) {
				propertyFile = path;
			}
			file = new File(propertyFile);
			if (file.isDirectory()) {
				file = new File(file, "multirelease.properties");
			}
			if (!file.exists()) {
				throw new MojoExecutionException("Invalid path specified for the property file");
			}
            getLog().info("Using custom multirelease.properties location: " + file);
		}

		PropertyResolver.getInstance().mergeProperties(mavenSession.getExecutionProperties(), parentProject, file);
	}

	/**
	 * Gets the builds the order in which projects will be built.
	 * @return the project build order
	 */
	protected List<DependencyMapper> getBuildOrder() {
		List<DependencyMapper> projects = getAvailableProjects();
		new DependencyResolver().getBuildOrder(projects);
		return projects;
	}

	/**
	 * Deletes the release.properties created while running the release goal.
	 */
	protected void cleanUpAfterRelease(File releaseProperty) {
		getLog().info("Cleaning up after release.");
		for (MavenProject mavenProject : getReactorProjects()) {
			String backupFile = mavenProject.getOriginalModel().getPomFile().getName().concat(".releaseBackup");
			String path = mavenProject.getOriginalModel().getProjectDirectory().getAbsolutePath();
			File file = new File(path, backupFile);
			if (file.exists()) {
				file.delete();
			}
			file = new File(path, RELEASE_PROPERTIES);
			if (file.exists()) {
				file.delete();
			}
		}
		if (releaseProperty.exists()) {
			releaseProperty.delete();
		}
	}

	/**
	 * Gets the available projects declared in parent POM file and child projects corresponding to parent project.
	 * @return the available projects
	 */
	private List<DependencyMapper> getAvailableProjects() {
		List<DependencyMapper> availableProjects = new ArrayList<DependencyMapper>();
		List<?> modules = parentProject.getModules();
		for (Object module : modules) {
			for (MavenProject mavenProject : reactorProjects) {
				if (((String) module).contains(mavenProject.getArtifactId())) {
					DependencyMapper dependencyMapper = new DependencyMapper(mavenProject);
					for (Object project : mavenProject.getCollectedProjects()) {
						dependencyMapper.getChildProject().add((MavenProject) project);
					}
					availableProjects.add(dependencyMapper);
					break;
				}
			}
		}
		return availableProjects;
	}

	/**
	 * Updates the POM with modified model and commits in SCM.
	 * 
	 * @param dependencyMapper {@link DependencyMapper}
	 * @param username the username
	 * @param password the password
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws FileNotFoundException the file not found exception
	 * @throws ScmException the scm exception
	 */
	protected void commitModifiedModel(DependencyMapper dependencyMapper, String username, String password,
			String scmCommentPrefix)
			throws IOException, FileNotFoundException, ScmException {
		// Commit modified POM to SCM.s
		getLog().info("commiting POM file in SVN with username " + username);
		MavenProject project = dependencyMapper.getMavenProject();
		CustomScmManager customScmManager = new CustomScmManager();
		customScmManager.checkin(project.getOriginalModel().getScm().getUrl(), username, password,
				project.getOriginalModel().getProjectDirectory(), scmCommentPrefix);
	}

	/**
	 * Write modified POM file to the file system.
	 * @param project {@link MavenProject}
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws FileNotFoundException the file not found exception
	 */
	protected void writePOM(MavenProject project) throws IOException, FileNotFoundException {
		getLog().info("Updating Dependency versions.");
		FileOutputStream fileOutputStream = null;
		try {
			MavenXpp3Writer xpp3Writer = new MavenXpp3Writer();
			fileOutputStream = new FileOutputStream(project.getOriginalModel().getPomFile());
			xpp3Writer.write(fileOutputStream, project.getOriginalModel());
		} finally {
			try {
				if (fileOutputStream != null) {
					fileOutputStream.close();
				}
			} catch (IOException e) {
				// Do nothing.
			}
		}
	}

	protected File getReleasePropertyfile() throws MojoExecutionException {
		File file = null;
		if (releaseProperties != null) {
			String path = (String) getMavenSession().getExecutionProperties().get(RELEASE_PROPERTY_KEY);
			if (path != null) {
				releaseProperties = path;
			}
			file = new File(releaseProperties);
			if (file.isDirectory()) {
				file = new File(file, RELEASE_PROPERTIES);
			}
            getLog().info("Using custom release.properties location: " + file);
		} else {
			file = new File(RELEASE_PROPERTIES);
		}
		return file;
	}

	protected List<MavenProject> getReactorProjects() {
		return reactorProjects;
	}

	protected MavenProject getParentProject() {
		return parentProject;
	}

	protected MavenSession getMavenSession() {
		return mavenSession;
	}

}
