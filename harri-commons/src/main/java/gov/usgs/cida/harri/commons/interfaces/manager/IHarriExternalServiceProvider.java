package gov.usgs.cida.harri.commons.interfaces.manager;

import org.teleal.cling.UpnpService;

public interface IHarriExternalServiceProvider {
	public void doServiceCalls(UpnpService harriManagerUpnpService);

}