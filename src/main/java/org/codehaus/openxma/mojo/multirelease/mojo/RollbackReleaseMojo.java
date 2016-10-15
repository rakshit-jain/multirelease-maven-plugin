package org.codehaus.openxma.mojo.multirelease.mojo;

import java.io.File;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.openxma.mojo.multirelease.exception.ProcessException;
import org.codehaus.openxma.mojo.multirelease.util.DependencyMapper;
import org.codehaus.openxma.mojo.multirelease.util.MavenReleasePluginExecutor;

/**
 * Rollback changes made by a previous release. This requires that the previous release descriptor
 * <tt>release.properties</tt> is still available in the local working copy.
 * 
 * @author Rakshit Jain
 */
@Mojo(name = "rollback", aggregator = true, defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class RollbackReleaseMojo extends AbstractReleaseMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		super.execute();
		try {
			for (DependencyMapper dependencyMapper : getBuildOrder()) {
				Model model = dependencyMapper.getMavenProject().getOriginalModel();
				String path = model.getProjectDirectory().getAbsolutePath()
						+ File.separator + "release.properties";
				File file = new File(path);
				if (file.exists()) {
					getLog().info("Executing Rollback goal");
					new MavenReleasePluginExecutor().rollback(dependencyMapper.getMavenProject());
					break;
				}
			}
			cleanUpAfterRelease(getReleasePropertyfile());
		} catch (IOException e) {
			getLog().error("Plugin execution failed because \n", e);
			throw new MojoFailureException("Rollback failed because of following error \n", e);
		} catch (ProcessException e) {
			getLog().error("Plugin execution failed because of process executions\n", e);
			throw new MojoExecutionException("Plugin not executed", e);
		}
	}

}
