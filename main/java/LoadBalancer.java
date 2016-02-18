import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadBalancer{
	private static final int THREAD_POOL_SIZE = 4;
	private final ServerSocket socket;
	private final DataCenterInstance[] instances;
	//private static List<DataCenterInstance> list;
	
	
	public LoadBalancer(ServerSocket socket, DataCenterInstance[] instances) {
		this.socket = socket;
		this.instances = instances;
		//this.list = Arrays.asList(instances);
	}

	// Complete this function
	public synchronized void start() throws IOException {
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
		int i = 0;
		try {
			Runnable hc0 = new HealthCheck(instances, 0);
			Runnable hc1 = new HealthCheck(instances, 1);
			Runnable hc2 = new HealthCheck(instances, 2);
			Thread t1 = new Thread(hc0);
			Thread t2 = new Thread(hc1);
			Thread t3 = new Thread(hc2);
			t1.start();
			t2.start();
			t3.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
		while (true) {
			// By default, it will send all requests to the first instance
			if (instances[i % instances.length] != null) {
				Runnable requestHandler = new RequestHandler(socket.accept(), instances[i % instances.length]);
				executorService.execute(requestHandler);			
			}	
			i++;
			//DC 1 CPU rate
			/*String cpu1 = getHTML(new URL("http://cloud-344906vm.eastus.cloudapp.azure.com:8080/info/cpu")).split("<body>")[1];
			System.out.println("CPU1 : " + cpu1);
			int index1 = cpu1.indexOf("<");
			double dc1 = 0;
			try {
				dc1 = Double.parseDouble(cpu1.substring(0, index1));
			} catch (Exception e) {
				dc1 = 0;
			}
			
			String cpu2 = getHTML(new URL("http://cloud-359820vm.eastus.cloudapp.azure.com:8080/info/cpu")).split("<body>")[1];
			System.out.println("CPU2 : " + cpu2);
			int index2 = cpu2.indexOf("<");
			double dc2 = 0;
			try {
				dc2 = Double.parseDouble(cpu2.substring(0, index2));
			} catch (Exception e ) {
				dc2 = 0;
			}
			
			String cpu3 = getHTML(new URL("http://cloud-434828vm.eastus.cloudapp.azure.com:8080/info/cpu")).split("<body>")[1];
			System.out.println("CPU3 : " + cpu3);
			int index3 = cpu3.indexOf("<");
			double dc3 = 0;
			try {
				dc3 = Double.parseDouble(cpu3.substring(0, index3));
			} catch (Exception e) {
				dc3 = 0;
			}
			if (dc1 <= dc2 && dc1 <= dc3) {
				i = 0;
			} else if (dc1 >= dc2 && dc3 >= dc2) {
				i = 1;
			} else {
				i = 2;
			}				*/
		}
	}
	private static int getCode(URL url) {
		int code = 0;
		while(true) {	
			try {
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				code = conn.getResponseCode();
			} catch (IOException e) {
				continue;
			}	
			return code;		
		}
	}
	private static String getHTML(URL url) {
		
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
				continue;
			}	
			return sb.toString();		
		}
	}
}
