import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import com.microsoft.azure.utility.ResourceContext;


public class HealthCheck extends AzureVMApiDemo implements Runnable{
	private DataCenterInstance[] list;
	private int index;
	public HealthCheck(DataCenterInstance[] list, int i) throws Exception {
		super();
		this.list = list;
		this.index = i;
	}

	@Override
	public void run() {
		while (true) {
			DataCenterInstance dc = list[index];
			try {
				URL url = new URL(dc.getUrl());
				int code = getCode(url);
				System.out.println("Health Check URL : " + dc.getUrl() +" Code : " + code);
				if (code != 200) {
					list[index] = null;
					//the data center has been killed start a new one
					String seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
					String dcName = String.format("cloud%s%s", seed, "vm");
					ResourceContext dataC = createVM (
			                resourceGroupName,
			                dcName,
			                resourceGroupNameWithVhdDC,
			                sourceVhdUriDC,
			                sizeDC,
			                subscriptionId,
			                storageAccountName);
			        System.out.println(checkVM(dataC, dcName));
					System.out.println("NEW Data Center URL : " + dcName+".eastus.cloudapp.azure.com");
					String newURl = "http://"+ dcName+".eastus.cloudapp.azure.com";
					DataCenterInstance newdc = new DataCenterInstance("NewDC", newURl);						
					sleepFor(200);
					while (true) {
						URL nurl = new URL (newURl);
						String res = getHTML(nurl);
						if (res != null) {
							break;
						}
						sleepFor(1);
					}
					list[index] = newdc;
				} else {
					//even the response code equals 200 we still needs to check the random URL
					String random = dc.getUrl() + "/lookup/random";
					URL randomurl = new URL(random);
					String response = getHTML(randomurl);
					System.out.println("Health Check Response : " + response);
					if (response == null) {
						//the data center still dead
						list[index] = null;
						String seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
						String dcName = String.format("cloud%s%s", seed, "vm");
						ResourceContext dataC = createVM (
				                resourceGroupName,
				                dcName,
				                resourceGroupNameWithVhdDC,
				                sourceVhdUriDC,
				                sizeDC,
				                subscriptionId,
				                storageAccountName);
				        System.out.println(checkVM(dataC, dcName));
						System.out.println("NEW Data Center URL : " + dcName+".eastus.cloudapp.azure.com");
						String newURl = "http://" + dcName+".eastus.cloudapp.azure.com";
						DataCenterInstance newdc = new DataCenterInstance("NewDC", newURl);							
						sleepFor(200);
						while (true) {
							URL nurl = new URL (newURl);
							String res = getHTML(nurl);
							if (res != null) {
								break;
							}
							sleepFor(1);
						}
						list[index] = newdc;
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				continue;
			}
		}
	}
	
	private int getCode(URL url) {
		int code = 0;
		while(true) {	
			try {
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				code = conn.getResponseCode();
			} catch (IOException e) {
				return 0;
			}	
			return code;		
		}
	}
	private String getHTML(URL url) {
		
		while(true) {	
			StringBuilder sb;
			try {
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String line;
				sb = new StringBuilder();
				while ((line = rd.readLine()) != null) {
					sb.append(line);
				}
				rd.close();			    
			} catch (IOException e) {
				return null;
			}	
			return sb.toString();		
		}
	}
	private static void sleepFor(int input) {
		System.out.println("Sleep for" + input + "seconds");
		for (int i = 0; i < input; i++) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}
}
