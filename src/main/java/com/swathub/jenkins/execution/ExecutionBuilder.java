package com.swathub.jenkins.execution;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.remoting.Callable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.log4j.*;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link ExecutionBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Guo Shenyu
 */
public class ExecutionBuilder extends Builder {
	private final String testSetID;
	private final String nodeName;
	private final String nodeType;
	private final String platformCode;
	private final boolean isSequential;

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
	public ExecutionBuilder(String testSetID, String nodeName, String nodeType, String platformCode, boolean isSequential) {
		this.testSetID = testSetID;
		this.nodeName = nodeName;
		this.nodeType = nodeType;
		this.platformCode = platformCode;
		this.isSequential = isSequential;
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getTestSetID() {
		return testSetID;
	}

	public String getNodeName() {
		return nodeName;
	}

	public String getNodeType() {
		return nodeType;
	}

	public String getPlatformCode() {
		return platformCode;
	}

	public boolean getIsSequential() {
		return isSequential;
	}

	private static class PostCallable implements Callable<JSONObject, Exception> {
		private static final long serialVersionUID = 1L;

		private String apiUrl;
		private String accessKey;
		private String secretKey;
		private HashMap<String, String> proxy;

		public PostCallable(String apiUrl, String accessKey, String secretKey, final HashMap<String, String> proxy) {
			this.apiUrl = apiUrl;
			this.accessKey = accessKey;
			this.secretKey = secretKey;
			this.proxy = proxy;
		}

		public JSONObject call() throws Exception {
			Utils utils = new Utils();
			JSONObject result = utils.apiPost(apiUrl, accessKey, secretKey, "", proxy);

			return result;
		}

		public void checkRoles(RoleChecker roleChecker) throws SecurityException {

		}
	}

	private static class GetCallable implements Callable<JSONObject, Exception> {
		private static final long serialVersionUID = 1L;

		private String apiUrl;
		private String accessKey;
		private String secretKey;
		private HashMap<String, String> proxy;

		public GetCallable(String apiUrl, String accessKey, String secretKey, final HashMap<String, String> proxy) {
			this.apiUrl = apiUrl;
			this.accessKey = accessKey;
			this.secretKey = secretKey;
			this.proxy = proxy;
		}

		public JSONObject call() throws Exception {
			Utils utils = new Utils();
			JSONObject result = utils.apiGet(apiUrl, accessKey, secretKey, proxy);;

			return result;
		}

		public void checkRoles(RoleChecker roleChecker) throws SecurityException {

		}
	}


	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		// This is where you 'build' the project.
		// Since this is a dummy, we just say 'hello world' and call that a build.

		boolean result = true;
		Logger logger = LogManager.getLogger("logger");

		PatternLayout layout = new PatternLayout("%d %-5p - %m%n");
		StringWriter logsw = new StringWriter();
		logger.setLevel(Level.INFO);
		logger.addAppender(new WriterAppender(layout, logsw));

		Utils utils = new Utils();
		String domain = getDescriptor().getDomain();
		String ownerName = getDescriptor().getOwnerName();
		String workspace = getDescriptor().getWorkspace();
		String userName = getDescriptor().getUserName();
		String apiKey = getDescriptor().getApiKey();

		HashMap<String, String> proxy = new HashMap<String, String>();
		proxy.put("server", getDescriptor().getProxyServer());
		proxy.put("port", getDescriptor().getProxyPort());
		proxy.put("username", getDescriptor().getProxyUsername());
		proxy.put("password", getDescriptor().getProxyPassword());

		logger.info("userName:" + userName);
		logger.info("apiKey:" + apiKey);
		logger.info("proxy:" + proxy.toString());

		JSONObject execResult;
		ArrayList<String> completedList = new ArrayList<String>();

		try {
			String params = "nodeName=" + URLEncoder.encode(nodeName, "UTF-8") + "&nodeType=" + nodeType + "&platform=" + URLEncoder.encode(platformCode, "UTF-8") + "&isSequential=" + (isSequential?"true":"false");
			logger.info("post:" + domain + "/api/" + ownerName + "/" + workspace + "/sets/" + testSetID +"/run?" + params);
			JSONObject jobResult = launcher.getChannel().call(new PostCallable(domain + "/api/" + ownerName + "/" + workspace + "/sets/" + testSetID +"/run?" + params, userName, apiKey, proxy));

			while (true) {
				logger.info("get:" + domain + "/api/" + ownerName + "/" + workspace + "/jobs/" + jobResult.getString("jobID") +"/query");
				execResult = launcher.getChannel().call(new GetCallable(domain + "/api/" + ownerName + "/" + workspace + "/jobs/" + jobResult.getString("jobID") +"/query", userName, apiKey, proxy));
				JSONArray tasks = execResult.getJSONArray("tasks");
				for (int i = 0; i < tasks.size(); i++) {
					JSONObject task = tasks.getJSONObject(i);
					String status = task.getString("status");
					if (status.equals("stopped") || status.equals("finished") || status.equals("failed")) {

						String taskID = task.getString("taskID");
						if (!completedList.contains(taskID)) {
							completedList.add(taskID);
							SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							Timestamp now = new Timestamp(System.currentTimeMillis());
							String timestamp = df.format(now);
							String message = timestamp + " " + task.getString("description") +
									" :   " + task.getString("status") + " (" + completedList.size() + "/" +
									tasks.size() + ")";
							listener.getLogger().println(message);
						}
					}
				}

				if (execResult.getString("status").equals("stopped") || execResult.getString("status").equals("finished")) {
					break;
				}

				Thread.sleep(15000);
			}

			utils.createXmlFile(new FilePath(build.getWorkspace(), "swat_result.xml"), execResult);
		} catch (Exception e) {
			listener.getLogger().println(e.getMessage());

			StringWriter sw = new StringWriter();
			PrintWriter spw = new PrintWriter(sw);
			e.printStackTrace(spw);
			logger.error(sw.toString());
			spw.close();
			result = false;
		}

		try {
			FilePath logFile = new FilePath(build.getWorkspace(), "swathub.log");
			PrintWriter fpw = new PrintWriter(logFile.write());
			fpw.println(logsw.toString());
			logsw.close();
			fpw.close();
		} catch (Exception fe) {

		}
		// This also shows how you can consult the global configuration of the builder
		return result;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}

	/**
	 * Descriptor for {@link ExecutionBuilder}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins that this is an implementation of an extension point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private String domain;
		private String ownerName;
		private String workspace;
		private String userName;
		private String apiKey;
		private String proxyServer;
		private String proxyPort;
		private String proxyUsername;
		private String proxyPassword;

		/**
		 * In order to load the persisted global configuration, you have to
		 * call load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 *      <p>
		 *      Note that returning {@link hudson.util.FormValidation#error(String)} does not
		 *      prevent the form from being saved. It just means that a message
		 *      will be displayed to the user.
		 */
		public FormValidation doCheckTestSetID(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please input the test set ID");
			return FormValidation.ok();
		}

		public FormValidation doCheckNodeType(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please input the node type");
			return FormValidation.ok();
		}

		public FormValidation doCheckPlatformCode(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please input the platform code");
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "SWAT Execution";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			domain = formData.getString("domain");
			ownerName = formData.getString("ownerName");
			workspace = formData.getString("workspace");
			userName = formData.getString("userName");
			apiKey = formData.getString("apiKey");
			proxyServer = formData.getString("proxyServer");
			proxyPort = formData.getString("proxyPort");
			proxyPort = proxyPort.isEmpty() ? "80" : proxyPort;
			try {
				int temp = Integer.parseInt(proxyPort);
			} catch (NumberFormatException nfe) {
				proxyPort = "80";
			}
			proxyUsername = formData.getString("proxyUsername");
			proxyPassword = formData.getString("proxyPassword");
			// ^Can also use req.bindJSON(this, formData);
			//  (easier when there are many fields; need set* methods for this, like setUseFrench)
			save();
			return super.configure(req,formData);
		}

		/**
		 * This method returns true if the global configuration says we should speak French.
		 *
		 * The method name is bit awkward because global.jelly calls this method to determine
		 * the initial state of the checkbox by the naming convention.
		 */
		public String getDomain() {
			return domain;
		}

		public String getOwnerName() {
			return ownerName;
		}

		public String getWorkspace() {
			return workspace;
		}

		public String getUserName() {
			return userName;
		}

		public String getApiKey() {
			return apiKey;
		}

		public String getProxyServer() {
			return proxyServer;
		}

		public String getProxyPort() {
			return proxyPort;
		}

		public String getProxyUsername() {
			return proxyUsername;
		}

		public String getProxyPassword() {
			return proxyPassword;
		}
	}
}
