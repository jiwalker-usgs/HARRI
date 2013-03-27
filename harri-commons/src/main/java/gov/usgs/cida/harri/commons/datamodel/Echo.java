package gov.usgs.cida.harri.commons.datamodel;


/**
 *
 * @author isuftin
 */
public class Echo extends HarriBean {
	private static final long serialVersionUID = 1L;
	
	private String managerId;
	private String host;
	private String deviceName;
	
	public String getManagerId() {
		return managerId;
	}
	public void setManagerId(String managerId) {
		this.managerId = managerId;
	}
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public String getDeviceName() {
		return deviceName;
	}
	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}
}
