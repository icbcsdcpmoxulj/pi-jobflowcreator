/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//      Contributors:      Xu Lijia 

package ci.xlj.plugins.jobflowcreator;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import ci.xlj.libs.procinvoker.ProcInvoker;
import ci.xlj.libs.statistics_collector.StatisticsCollector;
import ci.xlj.libs.utils.DateUtils;
import ci.xlj.libs.utils.OSUtils;
import ci.xlj.libs.utils.StringUtils;

@Extension
public class JobFlowCreatorPlugin implements RootAction, AccessControlled {

	private Logger logger = Logger.getLogger(JobFlowCreatorPlugin.class
			.getName());

	public String getDisplayName() {
		return Messages.DisplayName();
	}

	public String getIconFileName() {
		return "/plugin/pi-jobflowcreator/icons/icon.png";
	}

	public String getUrlName() {
		return "job-flow-creator";
	}

	public void doIndex(StaplerRequest req, StaplerResponse res)
			throws ServletException, IOException {
		res.sendRedirect(Jenkins.getInstance().getRootUrl() + getUrlName());
	}

	private File toolPath = new File(Jenkins.getInstance().getRootDir()
			+ File.separator + "/tools/pmo-jobbatchmaker");
	private File tool = new File(toolPath + "/jt.jar");

	public boolean toolExists() {
		if (!tool.exists()) {
			hint = Messages.CopyTool() + toolPath;
			return false;
		}

		hint = "";
		return true;
	}

	private ProcInvoker p;

	public void doCreateAJobFlow(StaplerRequest req, StaplerResponse res)
			throws ServletException, IOException, Exception {
		String username = Jenkins.getAuthentication().getName();

		StatisticsCollector.init("http://122.16.61.59:8080/JenkinsMaster/jfc");
		StatisticsCollector.sendMessage(username + " created a job flow with "
				+ jobName + " and " + newVersion);

		if (!toolExists()) {
			errorMsg = "";
			res.forwardToPreviousPage(req);
			return;
		}

		jobName = req.getParameter("_.jobName");
		newVersion = req.getParameter("_.newVersion");

		if (!StringUtils.isValid(jobName)
				|| !Pattern.matches(JOBNAME_PATTERN, jobName)) {
			errorMsg = Messages.InvalidJobName();
			res.forwardToPreviousPage(req);
			return;
		}

		if (!StringUtils.isValid(newVersion)
				|| !Pattern.matches(VERSION_PATTERN, newVersion)) {
			errorMsg = Messages.InvalidVersionName();
			res.forwardToPreviousPage(req);
			return;
		}

		String password = Jenkins.getAuthentication().getCredentials()
				.toString();

		String url = Jenkins.getInstance().getRootUrl();
		String jobdir = Jenkins.getInstance().getRootPath() + "/jobs";

		File temp;
		FileWriter w;
		String cmd;
		if (OSUtils.isWindows()) {
			temp = new File(toolPath + "/jt_temp_"
					+ DateUtils.toString(new Date()) + ".bat");

			w = new FileWriter(temp);
			cmd = "cmd.exe /c " + temp.getAbsolutePath();
		} else {
			temp = new File(toolPath + "/jt_temp"
					+ DateUtils.toString(new Date()) + ".sh");

			w = new FileWriter(temp);
			cmd = temp.getAbsolutePath();
		}

		if (username.contains("anonymous")) {
			w.append("java -jar " + tool + " " + jobName
					+ (OSUtils.isWindows() ? " " : " \\") + VERSION_PATTERN
					+ " " + newVersion + " " + url + " x x " + jobdir);
		} else if (StringUtils.isValid(password)) {
			w.append("java -jar " + tool + " " + jobName
					+ (OSUtils.isWindows() ? " " : " \\") + VERSION_PATTERN
					+ " " + newVersion + " " + url + " " + username + " "
					+ password + " " + jobdir);
		} else {
			errorMsg = Messages.SessionExpired();
			res.forwardToPreviousPage(req);
			return;
		}

		w.close();

		logger.info(cmd);

		if (!OSUtils.isWindows()) {
			p = new ProcInvoker("chmod +x " + temp.getAbsolutePath());
			p.invoke();
		}

		p = new ProcInvoker(cmd);
		result = p.invoke("gbk");

		if (result != 0) {
			if (result == -3) {
				errorMsg = Messages.NoSuchJob();
			} else
				errorMsg = p.getErrorMessage();
		} else {
			errorMsg = Messages.Success();
			logger.info(p.getOutput());
		}

		res.forwardToPreviousPage(req);
	}

	private static String JOBNAME_PATTERN = "[A-Z]{2}_[A-Z]{2}\\d{0,1}_[A-Z0-9]+_\\d{6}($||_[A-Za-z0-9_\\(\\)]+)";
	private static String VERSION_PATTERN = "\\d{6}";

	private String hint;

	public String getHint() {
		return hint;
	}

	private String jobName;

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	private String newVersion;

	public String getNewVersion() {
		return newVersion;
	}

	public void setNewVersion(String newVersion) {
		this.newVersion = newVersion;
	}

	private int result;

	public int getResult() {
		return result;
	}

	private String errorMsg;

	public String getErrorMsg() {
		return errorMsg;
	}

	public ACL getACL() {
		return Jenkins.getInstance().getAuthorizationStrategy().getRootACL();
	}

	public void checkPermission(Permission p) throws AccessDeniedException {
		getACL().checkPermission(Job.CREATE);
	}

	public boolean hasPermission(Permission p) {
		return getACL().hasPermission(p);
	}

}
