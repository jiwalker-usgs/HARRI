package gov.usgs.cida.harri.commons.datamodel;

import com.google.gson.Gson;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jxpath.JXPathContext;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author isuftin
 */
public class Tomcat extends Instance {

	private static org.slf4j.Logger LOG = LoggerFactory.getLogger(Tomcat.class);

	public static org.slf4j.Logger getLOG() {
		return LOG;
	}
	private String managerUsername = "";
	private transient String managerPassword = "";
	
	private Integer httpPort = 0;
	private Integer httpsPort = 0;

	private List<String> appList;
	private Map<String, ApplicationInfo> applicationMap;

	public Tomcat() {}
	
	public Tomcat(ProcessMD md) {
		this.processMetaData = md;
		this.applicationMap = new HashMap<String, ApplicationInfo>();
	}

	@Override
	public void populate() {
		String catalinaHomeLocation = this.processMetaData.getStartupOptions().get("catalina.base");
		File catalinaHome = new File(catalinaHomeLocation);
		File usersXML = FileUtils.getFile(catalinaHome, "conf", "tomcat-users.xml");
		File serverXML = FileUtils.getFile(catalinaHome, "conf", "server.xml");

		if (!catalinaHome.exists()) {
			getLOG().warn("The directory specified by catalina.home (" + catalinaHomeLocation + ") does not exist");
			return;
		} else if (!catalinaHome.canRead()) {
			getLOG().warn("The directory specified by catalina.home (" + catalinaHomeLocation + ") cannot be read");
			return;
		}

		if (!usersXML.exists()) {
			getLOG().warn("The catalina users file (" + usersXML.getPath() + ") does not exist");
		} else if (!usersXML.canRead()) {
			getLOG().warn("The catalina users file (" + usersXML.getPath() + ") cannot be read");
		} else {
			try {
				JXPathContext ctx = JXPathContext.newContext(parseXMLFile(usersXML).getDocumentElement());

				// Find the user that contains the manager role
				Node userNode = (Node) ctx.selectSingleNode("/user[contains(@roles, manager)]");
				this.setManagerUsername(userNode.getAttributes().getNamedItem("username").getTextContent());
				this.setManagerPassword(userNode.getAttributes().getNamedItem("password").getTextContent());
			} catch (SAXException ex) {
				getLOG().warn(ex.getMessage());
			} catch (IOException ex) {
				getLOG().warn(ex.getMessage());
			} catch (ParserConfigurationException ex) {
				getLOG().warn(ex.getMessage());
			}
		}

		if (!serverXML.exists()) {
			getLOG().warn("The catalina server config file (" + serverXML.getPath() + ") does not exist");
		} else if (!catalinaHome.canRead()) {
			getLOG().warn("The catalina users file (" + serverXML.getPath() + ") cannot be read");
		} else {
			try {
				JXPathContext ctx = JXPathContext.newContext(parseXMLFile(serverXML).getDocumentElement());
				Node httpPortNode = (Node) ctx.selectSingleNode("/Service[@name='Catalina']/Connector[@protocol='HTTP/1.1' and not(@SSLEnabled='true')]");
				Node httpsPortNode = (Node) ctx.selectSingleNode("/Service[@name='Catalina']/Connector[@protocol='HTTP/1.1' and @SSLEnabled='true']");

				if (httpPortNode != null) {
					this.setHttpPort((Integer) Integer.parseInt(httpPortNode.getAttributes().getNamedItem("port").getTextContent()));
				}
				if (httpsPortNode != null) {
					this.setHttpsPort((Integer) Integer.parseInt(httpsPortNode.getAttributes().getNamedItem("port").getTextContent()));
				}
			} catch (NumberFormatException ex) {
				getLOG().warn(ex.getMessage());
			} catch (SAXException ex) {
				getLOG().warn(ex.getMessage());
			} catch (IOException ex) {
				getLOG().warn(ex.getMessage());
			} catch (ParserConfigurationException ex) {
				getLOG().warn(ex.getMessage());
			}
		}

		if (getHttpPort() != 0) {
			getRemoteInfo();
		}
	}

	@Override
	void getRemoteInfo() {
		try {
			URL url = new URL("http://127.0.0.1:" + this.getHttpPort() + "/manager/status/all");
			String encoding = Base64.encode((this.getManagerUsername() + ":" + this.getManagerPassword()).getBytes());

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setDoOutput(true);
			connection.setRequestProperty("Authorization", "Basic " + encoding);
			List<String> managerOutput = IOUtils.readLines(connection.getInputStream());
			StringBuilder sb = new StringBuilder("");
			for (String outputLine : managerOutput) {
				if (!outputLine.trim().toLowerCase().contains("<img")) {
					sb.append(outputLine.replace(" nowrap", "")
							.replace("<br>", "<br />")
							.replace("<hr size=\"1\" noshade=\"noshade\">", "")
							.replace("&copy;", ""));
				}
			}
			Document doc = parseXMLInputStream(IOUtils.toInputStream(sb.toString()));
			JXPathContext ctx = JXPathContext.newContext(doc.getDocumentElement());

			String memStats = ((Node) ctx.selectSingleNode("//h1[text()='JVM']/following-sibling::p[1]")).getTextContent();
			List<Node> appListingNodes = (List<Node>) ctx.selectNodes("/body/a[@class='A.name']");
			for (Node node : appListingNodes) {
				String appName = node.getChildNodes().item(0).getTextContent().substring("localhost".length());
				String appInfo = node.getNextSibling().getTextContent();
				String startTime = appInfo.substring(0, appInfo.indexOf("Startup time"));
				String startupTime = appInfo.substring(startTime.length(), appInfo.indexOf("TLD"));
				Boolean running = !startTime.contains("1969");
				ApplicationInfo applicationInfo = new ApplicationInfo(
						appName.trim(),
						startTime.substring(startTime.indexOf(":") + 1).trim(),
						startupTime.substring(startupTime.indexOf(":") + 1).trim(),
						running);
				this.applicationMap.put(appName, applicationInfo);
			}
		} catch (Exception ex) {
			getLOG().warn(ex.getMessage());
		}

	}

	private Document parseXMLFile(File xml) throws ParserConfigurationException, SAXException, IOException {
		return parseXMLInputStream(FileUtils.openInputStream(xml));
	}

	private Document parseXMLInputStream(InputStream is) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		dbFactory.setValidating(false); // Tomcat gives very invalid html back :(
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(is);
		doc.getDocumentElement().normalize();
		return doc;
	}

	public String getManagerUsername() {
		return managerUsername;
	}

	public String getManagerPassword() {
		return managerPassword;
	}

	@Override
	public Integer getHttpPort() {
		return httpPort;
	}

	@Override
	public Integer getHttpsPort() {
		return httpsPort;
	}

	@Override
	public Map<String, ApplicationInfo> getApplicationMap() {
		return Collections.unmodifiableMap(applicationMap);
	}

	public void setManagerUsername(String managerUsername) {
		this.managerUsername = managerUsername;
	}

	public void setManagerPassword(String managerPassword) {
		this.managerPassword = managerPassword;
	}

	public void setHttpPort(Integer httpPort) {
		this.httpPort = httpPort;
	}

	public void setHttpsPort(Integer httpsPort) {
		this.httpsPort = httpsPort;
	}

	public void setAppList(List<String> appList) {
		this.appList = appList;
	}

	public void setApplicationMap(Map<String, ApplicationInfo> applicationMap) {
		this.applicationMap = applicationMap;
	}
	
	@Override
	public List<String> getAppList() {
		// Cheating here
		this.setAppList(new ArrayList<String>());
		Iterator<String> keyIter = getApplicationMap().keySet().iterator();
		while (keyIter.hasNext()) {
			String appContext = keyIter.next();
			this.appList.add(appContext.substring(1));
		}

		return Collections.unmodifiableList(this.appList);
	}

	@Override
	public String toJSON() {
		return new Gson().toJson(this);
	}

	public static Tomcat fromJSON(String json) {
		Tomcat tomcat = new Gson().fromJson(json, Tomcat.class);
		return tomcat;
	}

}
