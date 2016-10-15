package org.codehaus.openxma.mojo.multirelease.scm;

import java.io.File;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.ScmVersion;
import org.apache.maven.scm.command.checkin.CheckInScmResult;
import org.apache.maven.scm.manager.BasicScmManager;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.accurev.AccuRevScmProvider;
import org.apache.maven.scm.provider.bazaar.BazaarScmProvider;
import org.apache.maven.scm.provider.clearcase.ClearCaseScmProvider;
import org.apache.maven.scm.provider.cvslib.cvsexe.CvsExeScmProvider;
import org.apache.maven.scm.provider.cvslib.cvsjava.CvsJavaScmProvider;
import org.apache.maven.scm.provider.git.gitexe.GitExeScmProvider;
import org.apache.maven.scm.provider.hg.HgScmProvider;
import org.apache.maven.scm.provider.jazz.JazzScmProvider;
import org.apache.maven.scm.provider.local.LocalScmProvider;
import org.apache.maven.scm.provider.perforce.PerforceScmProvider;
import org.apache.maven.scm.provider.starteam.StarteamScmProvider;
import org.apache.maven.scm.provider.svn.svnexe.SvnExeScmProvider;
import org.apache.maven.scm.provider.synergy.SynergyScmProvider;
import org.apache.maven.scm.provider.vss.VssScmProvider;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.shared.invoker.PrintStreamLogger;

/***
 * SCM manager to commit files in the provided SCM. SCM is identified by the SCM URL.
 * @author Rakshit Jain
 * 
 */
public class CustomScmManager {

	private final ScmManager scmManager;

	/** Instance logger */
	private final static PrintStreamLogger log = new PrintStreamLogger();

	public CustomScmManager() {
		scmManager = new BasicScmManager();
		// Add all SCM providers we want to use
		scmManager.setScmProvider("cvs", new CvsJavaScmProvider());
		scmManager.setScmProvider("svn", new SvnExeScmProvider());
		scmManager.setScmProvider("accurev", new AccuRevScmProvider());
		scmManager.setScmProvider("bazaar", new BazaarScmProvider());
		scmManager.setScmProvider("clearcase", new ClearCaseScmProvider());
		scmManager.setScmProvider("hg", new HgScmProvider());
		scmManager.setScmProvider("local", new LocalScmProvider());
		scmManager.setScmProvider("perforce", new PerforceScmProvider());
		scmManager.setScmProvider("cvs_native", new CvsExeScmProvider());
		scmManager.setScmProvider("git", new GitExeScmProvider());
		scmManager.setScmProvider("starteam", new StarteamScmProvider());
		scmManager.setScmProvider("synergy", new SynergyScmProvider());
		scmManager.setScmProvider("vss", new VssScmProvider());
		scmManager.setScmProvider("jazz", new JazzScmProvider());
	}

	/**
	 * Commit files to the SCM.
	 * @param scmURL SCM url as defined in POM file.
	 * @param username SCM username to use.
	 * @param password SCM password to use.
	 * @param workingCopyPath current working directory
	 * @param scmCommentPrefix comment to be added in SCM on commit
	 * @throws ScmException
	 */
	public void checkin(String scmURL, String username, String password, File workingCopyPath, String scmCommentPrefix)
			throws ScmException {
		ScmRepository repository = scmManager.makeScmRepository(scmURL);
		repository.getProviderRepository().setUser(username);
		repository.getProviderRepository().setPassword(password);
		checkIn(repository, workingCopyPath, null, scmCommentPrefix);

	}

	private void checkIn(ScmRepository scmRepository, File workingDirectory, ScmVersion version, String scmCommentPrefix)
			throws ScmException {
		if (!workingDirectory.exists()) {
			System.err.println("The working directory doesn't exist: '" + workingDirectory.getAbsolutePath()
					+ "'.");
			return;
		}

		CheckInScmResult result =
				scmManager.checkIn(scmRepository, new ScmFileSet(workingDirectory), version, scmCommentPrefix);

		if (!result.isSuccess()) {
			showError(result);
			return;
		}

		List<ScmFile> checkedInFiles = result.getCheckedInFiles();

		log.info("Checked in these files: ");

		for (ScmFile file : checkedInFiles) {
			log.info(" " + file.getPath());
		}
	}

	/**
	 * Log the error occurred during SCM operation.
	 * @param result result of SCM operation.
	 */
	private void showError(ScmResult result) {
		System.err.println("There was a error while executing the SCM command.");

		String providerMessage = result.getProviderMessage();

		if (!StringUtils.isEmpty(providerMessage)) {
			log.error("Error message from the provider: " + providerMessage);
		}
		else {
			log.error("The provider didn't give a error message.");
		}

		String output = result.getCommandOutput();

		if (!StringUtils.isEmpty(output)) {
			log.error("Command output:");
			log.error(output);
		}
	}
}
