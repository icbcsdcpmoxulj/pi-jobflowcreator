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
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import org.acegisecurity.AccessDeniedException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import ci.xlj.libs.procinvoker.ProcInvoker;
import ci.xlj.libs.utils.OSUtils;

@Extension
public class JobFlowCreator implements RootAction, AccessControlled {

	private Logger logger = Logger.getLogger(JobFlowCreator.class.getName());

	public String getDisplayName() {
		return "新建批量任务";
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

	// private static String JOBNAME_PATTERN =
	// "[A-Z]{2}_[A-Z]{2}\\d_[A-Z0-9]+_\\d{6}($|_[A-Za-z0-9\\(\\)]+.*)||PMO-.*||BJ_KF3_EBDP_HEAD_.*";
	private static String VERSION_PATTERN = "\\d{6}";

	private ProcInvoker p;
	private int result;

	public void doCreateBatchJobs(StaplerRequest req, StaplerResponse res)
			throws ServletException, IOException, Exception {
		logger.info("clicked!");

		if (!checkTool()) {
			errorMsg = "";
			res.forwardToPreviousPage(req);
			return;
		}

		jobName = req.getParameter("_.jobName");
		newVersion = req.getParameter("_.newVersion");

		if (jobName == null || jobName.trim().equals("")) {
			errorMsg = "任务命名不能为空，请整改后重试！";
			res.forwardToPreviousPage(req);
			return;
		}

		if (newVersion == null || newVersion.trim().equals("")
				|| !Pattern.matches(VERSION_PATTERN, newVersion)) {
			errorMsg = "输入的版本号不是有效的6位版本号（例如201406），请整改后重试！";
			res.forwardToPreviousPage(req);
			return;
		}

		logger.info(jobName);
		logger.info(newVersion);

		String username = Jenkins.getAuthentication().getName();
		String password = Jenkins.getAuthentication().getCredentials()
				.toString();
		String url = Jenkins.getInstance().getRootUrl();
		String jobdir = Jenkins.getInstance().getRootPath() + "/jobs";
		File jt = new File(Jenkins.getInstance().getRootDir() + File.separator
				+ "/tools/pmo-jobbatchmaker/");

		File temp;
		FileWriter w;
		String cmd;
		if (OSUtils.isWindows()) {
			temp = new File(jt + "/jt_temp.bat");
			w = new FileWriter(temp);
			w.append("set JT_HOME=" + jt + OSUtils.getOSLineSeparator());
			cmd = "cmd.exe /c " + temp.getAbsolutePath();
		} else {
			temp = new File(jt + "/jt_temp.sh");
			w = new FileWriter(temp);
			w.append("export JT_HOME=" + jt + OSUtils.getOSLineSeparator());
			cmd = temp.getAbsolutePath();
		}

		if (username.contains("anonymous")) {
			w.append("java -jar " + jt + "/jt.jar " + url + " " + jobdir
					+ " -cs " + jobName + " " + newVersion);
		} else {
			w.append("java -jar " + jt + "/jt.jar " + url + " " + username
					+ " " + password + " " + jobdir + " -cs " + jobName + " "
					+ newVersion);
		}
		w.close();

		logger.info(cmd);

		if (!OSUtils.isWindows()) {
			p = new ProcInvoker("chmod +x " + temp.getAbsolutePath());
			p.invoke();
		}

		p = new ProcInvoker(cmd);
		result = p.invoke("gbk");

		logger.info("Result: " + result);
		if (result != 0) {
			errorMsg = p.getErrorMessage();
		} else {
			errorMsg = "任务创建成功。 ";
			logger.info(p.getOutput());
		}

		res.forwardToPreviousPage(req);
	}

	private String newVName;

	public void doCreateAView(StaplerRequest req, StaplerResponse res)
			throws ServletException, IOException, Exception {
		logger.info("clicked!");

		newVName = req.getParameter("_.newVName");
		newVersion = req.getParameter("_.newVersion");

		if (jobName == null || jobName.trim().equals("")) {
			errorMsg = "任务命名不能为空，请整改后重试！";
			res.forwardToPreviousPage(req);
			return;
		}

		if (newVersion.trim() == ""
				|| !Pattern.matches(VERSION_PATTERN, newVersion)) {
			errorMsg = "输入的版本号不是有效的6位版本号（例如201406），请整改后重试！";
			res.forwardToPreviousPage(req);
			return;
		}

		logger.info(newVName);
		logger.info(newVersion);

		String username = Jenkins.getAuthentication().getName();
		String password = Jenkins.getAuthentication().getCredentials()
				.toString();
		String url = Jenkins.getInstance().getRootUrl();

		if (username.contains("anonymous")) {
		} else {
		}

		logger.info("Result: " + result);
		if (result != 0) {
			errorMsg = p.getErrorMessage();
		} else {
			errorMsg = "任务创建成功。 ";
			logger.info(p.getOutput());
		}

		res.forwardToPreviousPage(req);
	}

	public boolean checkTool() {
		File jt = new File(Jenkins.getInstance().getRootDir() + File.separator
				+ "/tools/pmo-jobbatchmaker/");
		if (!jt.exists()) {
			hint = "请安装插件依赖工具jt.jar到路径" + jt;
			return false;
		}

		hint = "";
		return true;
	}

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

	public int getResult() {
		return result;
	}

	public void setResult(int result) {
		this.result = result;
	}

	private String errorMsg;

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	private String viewName;

	public String getViewName() {
		return viewName;
	}

	public void setViewName(String viewName) {
		this.viewName = viewName;
	}

	public FormValidation doCheckJobName(@QueryParameter String value) {
		if (value.length() == 0) {
			return FormValidation.error("不能为空。");
		}

		return FormValidation.ok();
	}

	public FormValidation doCheckNewVersion(@QueryParameter String value) {
		if (value.length() == 0) {
			return FormValidation.error("不能为空。");
		}

		return FormValidation.ok();
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
