/**
 * This is the copy of a working example
 */

import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementService;
import com.microsoft.azure.management.compute.models.*;
import com.microsoft.azure.management.network.NetworkResourceProviderClient;
import com.microsoft.azure.management.network.NetworkResourceProviderService;
import com.microsoft.azure.management.network.models.AzureAsyncOperationResponse;
import com.microsoft.azure.management.network.models.PublicIpAddressGetResponse;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementService;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.network.models.DhcpOptions;
import com.microsoft.azure.management.storage.StorageManagementService;
import com.microsoft.azure.management.network.models.VirtualNetwork;
import com.microsoft.azure.utility.*;
import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Math;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class AzureVMApiDemo {
	private static final int LIMIT = 3000;
	
    private static ResourceManagementClient resourceManagementClient;
    private static StorageManagementClient storageManagementClient;
    private static ComputeManagementClient computeManagementClient;
    private static NetworkResourceProviderClient networkResourceProviderClient;

    // the source URI of VHD
    private static String sourceVhdUriLB = "https://chujianpro21.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/cc15619p22lbv2-osDisk.1cf68388-ac67-4165-bec0-67341257d50a.vhd";
    protected static String sourceVhdUriDC = "https://chujianpro21.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/cc15619p22dcv6-osDisk.b0c453f3-f75f-4a2d-bd9c-ae055b830124.vhd";
    private static String sourceVhdUriLG = "https://chujianpro21.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/cc15619p22lgv7-osDisk.c0410b8f-821e-4de3-b725-2a834fd10060.vhd";
    // configuration for your application token
    private static String baseURI = "https://management.azure.com/";
    private static String basicURI = "https://management.core.windows.net/";
    private static String endpointURL = "https://login.windows.net/";

    protected static String subscriptionId = "92ccc168-9150-4722-8ba4-840d893ea80d";
    private static String tenantID = "a6ad9e30-360d-41ae-b651-30df5d9d9c89";
    private static String applicationID = "d7c98aa1-4179-46b7-9146-95aafe193784";
    private static String applicationKey = "pro21";

    // configuration for your resource account/storage account
    protected static String storageAccountName = "chujianpro21";
    private static String resourceGroupNameWithVhdLG = "chujian";
    protected static String resourceGroupNameWithVhdDC = "chujian";
    protected static String sizeDC = VirtualMachineSizeTypes.STANDARD_A1;
    private static String sizeLG = "Standard_D1";
    private static String sizeLB = "Standard_D1";
    private static String region = "EastUs";
    private static String vmName = "";
    protected static String resourceGroupName = "Chujian";

    // configuration for your virtual machine
    private static String adminName = "ubuntu";
    /**
      * Password requirements:
      * 1) Contains an uppercase character
      * 2) Contains a lowercase character
      * 3) Contains a numeric digit
      * 4) Contains a special character.
      */
    private static String adminPassword = "Cloud@123";

    public AzureVMApiDemo() throws Exception{
        Configuration config = createConfiguration();
        resourceManagementClient = ResourceManagementService.create(config);
        storageManagementClient = StorageManagementService.create(config);
        computeManagementClient = ComputeManagementService.create(config);
        networkResourceProviderClient = NetworkResourceProviderService.create(config);
    }

    public static Configuration createConfiguration() throws Exception {
        // get token for authentication
        String token = AuthHelper.getAccessTokenFromServicePrincipalCredentials(
                        basicURI,
                        endpointURL,
                        tenantID,
                        applicationID,
                        applicationKey).getAccessToken();

        // generate Azure sdk configuration manager
        return ManagementConfiguration.configure(
                null, // profile
                new URI(baseURI), // baseURI
                subscriptionId, // subscriptionId
                token// token
                );
    }

    /***
     * Create a virtual machine given configurations.
     *
     * @param resourceGroupName: a new name for your virtual machine [customized], will create a new one if not already exist
     * @param vmName: a PUBLIC UNIQUE name for virtual machine
     * @param resourceGroupNameWithVhd: the resource group where the storage account for VHD is copied
     * @param sourceVhdUri: the Uri for VHD you copied
     * @param instanceSize
     * @param subscriptionId: your Azure account subscription Id
     * @param storageAccountName: the storage account where you VHD exist
     * @return created virtual machine IP
     */
    public static ResourceContext createVM (
        String resourceGroupName,
        String vmName,
        String resourceGroupNameWithVhd,
        String sourceVhdUri,
        String instanceSize,
        String subscriptionId,
        String storageAccountName) throws Exception {

        ResourceContext contextVhd = new ResourceContext(
                region, resourceGroupNameWithVhd, subscriptionId, false);
        ResourceContext context = new ResourceContext(
                region, resourceGroupName, subscriptionId, false);

        ComputeHelper.createOrUpdateResourceGroup(resourceManagementClient,context);
        context.setStorageAccountName(storageAccountName);
        contextVhd.setStorageAccountName(storageAccountName);
        context.setStorageAccount(StorageHelper.getStorageAccount(storageManagementClient,contextVhd));

        if (context.getNetworkInterface() == null) {
            if (context.getPublicIpAddress() == null) {
                NetworkHelper
                    .createPublicIpAddress(networkResourceProviderClient, context);
            }
            if (context.getVirtualNetwork() == null) {
                NetworkHelper
                    .createVirtualNetwork(networkResourceProviderClient, context);
            }

            VirtualNetwork vnet =  context.getVirtualNetwork();

            // set DhcpOptions
            DhcpOptions dop = new DhcpOptions();
            ArrayList<String> dnsServers = new ArrayList<String>(2);
            dnsServers.add("8.8.8.8");
            dop.setDnsServers(dnsServers);
            vnet.setDhcpOptions(dop);

            try {
                AzureAsyncOperationResponse response = networkResourceProviderClient.getVirtualNetworksOperations()
                    .createOrUpdate(context.getResourceGroupName(), context.getVirtualNetworkName(), vnet);
            } catch (ExecutionException ee) {
                if (ee.getMessage().contains("RetryableError")) {
                    AzureAsyncOperationResponse response2 = networkResourceProviderClient.getVirtualNetworksOperations()
                        .createOrUpdate(context.getResourceGroupName(), context.getVirtualNetworkName(), vnet);
                } else {
                    throw ee;
                }
            }


            NetworkHelper
                .createNIC(networkResourceProviderClient, context, context.getVirtualNetwork().getSubnets().get(0));

            NetworkHelper
                .updatePublicIpAddressDomainName(networkResourceProviderClient, resourceGroupName, context.getPublicIpName(), vmName);
        }

        System.out.println("[15319/15619] "+context.getPublicIpName());
        System.out.println("[15319/15619] Start Create VM...");

        try {
            // name for your VirtualHardDisk
            String osVhdUri = ComputeHelper.getVhdContainerUrl(context) + String.format("/os%s.vhd", vmName);

            VirtualMachine vm = new VirtualMachine(context.getLocation());

            vm.setName(vmName);
            vm.setType("Microsoft.Compute/virtualMachines");
            vm.setHardwareProfile(createHardwareProfile(context, instanceSize));
            vm.setStorageProfile(createStorageProfile(osVhdUri, sourceVhdUri));
            vm.setNetworkProfile(createNetworkProfile(context));
            vm.setOSProfile(createOSProfile(adminName, adminPassword, vmName));

            context.setVMInput(vm);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // Remove the resource group will remove all assets (VM/VirtualNetwork/Storage Account etc.)
        // Comment the following line to keep the VM.
        // resourceManagementClient.getResourceGroupsOperations().beginDeleting(context.getResourceGroupName());
        // computeManagementClient.getVirtualMachinesOperations().beginDeleting(resourceGroupName,"project2.2");
        return context;
        }

    /***
     * Check public IP address of virtual machine
     *
     * @param context
     * @param vmName
     * @return public IP
     */
    public static String checkVM(ResourceContext context, String vmName) {
        String ipAddress = null;

        try {
            VirtualMachine vmHelper = ComputeHelper.createVM(
                    resourceManagementClient, computeManagementClient, networkResourceProviderClient, storageManagementClient,
                    context, vmName, "ubuntu", "Cloud@123").getVirtualMachine();

            System.out.println("[15319/15619] "+vmHelper.getName() + " Is Created :)");
            while(ipAddress == null) {
                PublicIpAddressGetResponse result = networkResourceProviderClient.getPublicIpAddressesOperations().get(resourceGroupName, context.getPublicIpName());
                ipAddress = result.getPublicIpAddress().getIpAddress();
                Thread.sleep(10);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return ipAddress;
    }

    /***
     * Create a HardwareProfile for virtual machine
     *
     * @param context
     * @param instanceSize
     * @return created HardwareProfile
     */
    public static HardwareProfile createHardwareProfile(ResourceContext context, String instanceSize) {
        HardwareProfile hardwareProfile = new HardwareProfile();
        if (context.getVirtualMachineSizeType()!=null && !context.getVirtualMachineSizeType().isEmpty()) {
            hardwareProfile.setVirtualMachineSize(context.getVirtualMachineSizeType());
        } else {
            hardwareProfile.setVirtualMachineSize(instanceSize);
        }
        return hardwareProfile;
    }

    /***
     * Create a StorageProfile for virtual machine
     *
     * @param osVhdUri
     * @param sourceVhdUri
     * @return created StorageProfile
     */
    public static StorageProfile createStorageProfile(String osVhdUri, String sourceVhdUri) {
        StorageProfile storageProfile = new StorageProfile();

        VirtualHardDisk vHardDisk = new VirtualHardDisk();
        vHardDisk.setUri(osVhdUri);
        //set source image
        VirtualHardDisk sourceDisk = new VirtualHardDisk();
        sourceDisk.setUri(sourceVhdUri);

        OSDisk osDisk = new OSDisk("osdisk", vHardDisk, DiskCreateOptionTypes.FROMIMAGE);
        osDisk.setSourceImage(sourceDisk);
        osDisk.setOperatingSystemType(OperatingSystemTypes.LINUX);
        osDisk.setCaching(CachingTypes.NONE);

        storageProfile.setOSDisk(osDisk);

        return storageProfile;
    }

    /***
     * Create a NetworkProfile for virtual machine
     *
     * @param context
     * @return created NetworkProfile
     */
    public static NetworkProfile createNetworkProfile(ResourceContext context) {
        NetworkProfile networkProfile = new NetworkProfile();
        NetworkInterfaceReference nir = new NetworkInterfaceReference();
        nir.setReferenceUri(context.getNetworkInterface().getId());
        ArrayList<NetworkInterfaceReference> nirs = new ArrayList<NetworkInterfaceReference>(1);
        nirs.add(nir);
        networkProfile.setNetworkInterfaces(nirs);

        return networkProfile;
    }

    /***
     * Create a OSProfile for virtual machine
     *
     * @param adminName
     * @param adminPassword
     * @param vmName
     * @return created OSProfile
     */
    public static OSProfile createOSProfile(String adminName, String adminPassword, String vmName) {
        OSProfile osProfile = new OSProfile();
        osProfile.setAdminPassword(adminPassword);
        osProfile.setAdminUsername(adminName);
        osProfile.setComputerName(vmName);

        return osProfile;
    }

    /**
     * The main entry for the demo
     *
     * args0: resource group
     * args1: storage account
     * args2: image name
     * args3: subscription ID
     * args4: tenant ID
     * args5: application ID
     * args6: application Key
     */
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
    public static String startNewDC() throws Exception {
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
        return dcName+".eastus.cloudapp.azure.com";
    }
    public static void main(String[] args) throws Exception {
    	Properties properties = new Properties();
		String andrewID = null;		
		String password = null;		
		try {
			properties.load(AzureVMApiDemo.class.getResourceAsStream("/Credentials.properties"));
			andrewID = properties.getProperty("ANDREW_ID");
			System.out.println(andrewID);
			password = properties.getProperty("SUBMISSION_PASSWORD");
			System.out.println(password);
		} catch (IOException e) {
			System.out.println("Something wrong to reading your credentials");
			e.printStackTrace();
		}
        String seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
        vmName = String.format("cloud%s%s", seed, "vm");
        resourceGroupName = String.format("cloud%s%s", seed, "ResourceGroup");

        resourceGroupNameWithVhdLG = args[0].trim();
        resourceGroupNameWithVhdDC = args[0].trim();
        storageAccountName = args[1].trim();
        sourceVhdUriLG = String.format("https://%s.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/%s", storageAccountName, args[2].trim());
        sourceVhdUriDC = String.format("https://%s.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/%s", storageAccountName, args[7].trim());
        sourceVhdUriLB = String.format("https://%s.blob.core.windows.net/system/Microsoft.Compute/Images/vhds/%s", storageAccountName, args[8].trim());
        subscriptionId = args[3].trim();
        tenantID = args[4].trim();
        applicationID = args[5].trim();
        applicationKey = args[6].trim();

        System.out.println("Initializing Azure virtual machine:");
        System.out.println("Source VHD URL: "+sourceVhdUriLG);
        System.out.println("Storage account: "+storageAccountName);
        System.out.println("Subscription ID: "+subscriptionId);
        System.out.println("Tenent ID: "+tenantID);
        System.out.println("Application ID: "+applicationID);
        System.out.println("Application Key: "+applicationKey);
        System.out.println("VM Name: "+vmName);

        AzureVMApiDemo demoVM = new AzureVMApiDemo();

        System.out.println("[15319/15619] Project 2.1 Configured");
        //Create A load generator
        ResourceContext loadG = createVM (
                resourceGroupName,
                vmName,
                resourceGroupNameWithVhdLG,
                sourceVhdUriLG,
                sizeLG,
                subscriptionId,
                storageAccountName);
        System.out.println(checkVM(loadG, vmName));        
        //Submit the password and andrewID
        //String lgurl = "http://"+vmName+".eastus.cloudapp.azure.com/password?passwd="+password+"&andrewId="+ andrewID;
		System.out.println("Load Generator URL : " + vmName+".eastus.cloudapp.azure.com");
		//submit dns to data center
		//String dcurl = "http://"+dataCenter.getPublicDnsName()+"/test/horizontal?dns="+ dataCenter.getPublicDnsName();
		//System.out.println(dcurl);
		
		
		//String dcName = String.format("cloud%s%s", seed, "vm");
		for (int i = 0; i < 3; i++) {
			seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
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
			System.out.println("Data Center URL : " + dcName+".eastus.cloudapp.azure.com");
		}
		seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
		String dcName = String.format("cloud%s%s", seed, "vm");
		ResourceContext loadb = createVM (
                resourceGroupName,
                dcName,
                resourceGroupNameWithVhdDC,
                sourceVhdUriLB,
                sizeLB,
                subscriptionId,
                storageAccountName);
        System.out.println(checkVM(loadb, dcName));
		System.out.println("Load Balancer URL : " + dcName+".eastus.cloudapp.azure.com");
		
		System.exit(0);
		
		
		URL lg = null;
		URL dc = null;
		URL urlLG = null;
    	
        
        System.out.println("Load Generator id submit succeed");
		//initial the data centers to meet the requirement of 4000 RPS
		int numOfDataCenter = 0;
		double currRPS = 0;
		//ResourceContext dataC = null;
		boolean first = true;
		String testID = null;		
		while (currRPS < LIMIT) {
			//create a new data center
			seed = String.format("%d%d", (int) System.currentTimeMillis()%1000, (int)(Math.random()*1000));
			//String dcName = String.format("cloud%s%s", seed, "vm");
			ResourceContext dataC = createVM (
	                resourceGroupName,
	                dcName,
	                resourceGroupNameWithVhdDC,
	                sourceVhdUriDC,
	                sizeDC,
	                subscriptionId,
	                storageAccountName);
	        System.out.println(checkVM(dataC, dcName)); 
			numOfDataCenter++;
			System.out.println("Added a new data center");
			//sleepFor(30);
			//waitting for instance running
			//dataCenter = loopForInstanceState(ec2, dataCenter.getInstanceId());
			//System.out.println("Data Center Status : " + dataCenter.getState().getName());
			//System.out.println("Data center DNS : " + dataCenter.getPublicDnsName());

			//submit new data center to load generator and get the test id
			if (first) {
				String dcurl = "http://"+vmName+".eastus.cloudapp.azure.com/test/horizontal?dns="+ dcName+".eastus.cloudapp.azure.com";
				System.out.println("Data center URL : " + dcurl);
				
				URL urlDC = null;
				try {	
				    	urlDC = new URL(dcurl);
				        String response = getHTML(urlDC);
				        String[] split = response.split("test.");
						String end = split[1];
						System.out.println("end : "+end);
						int endindex = end.lastIndexOf(".log");
						testID = end.substring(0, endindex);
						System.out.println("Test ID : " + testID);
						//System.exit(0);
			        } catch (IOException e) {
			        	System.out.println("url = " + urlDC + " is not ready");
					}
		    	first = false;
			} else {
				String addURL = "http://"+ vmName+".eastus.cloudapp.azure.com/test/horizontal/add?dns="+dcName+".eastus.cloudapp.azure.com";
				System.out.println("Added URL : " + addURL);
				URL add = new URL(addURL);
				String addString = getHTML(add);  
				System.out.println("ADD STRING : " + addString);
			}
			sleepFor(80);	    	
	    	String rps = "http://"+  vmName+".eastus.cloudapp.azure.com/log?name=test."+ testID +".log";
	    	System.out.println(rps);
	    	URL rpsURL = new URL(rps);
	    	String resRPS = getHTML(rpsURL);
			System.out.println("Returned HTML : " + resRPS);
			currRPS = getRPS(resRPS);
			System.out.println("currRPS : " + currRPS);						
		}
    }
    
    private static double getRPS(String resRPS) {
		String[] split = resRPS.split("]");
		if (split != null) {
			String rps = split[split.length - 1];
			String[] res = rps.split(".com=");
			double total = 0.0;
			for (int i = 0; i < res.length; i++) {
				String str = res[i].substring(0, 6);
				try{
					total += Double.parseDouble(str);
				} catch(NumberFormatException e) {
					continue;
				}								
			}
			return total;
		} else {
			System.out.println("Something wrong in your html");
			return 0;
		}		
	}
	
	private static String getHTML(URL url)  {
		
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
				sleepFor(10);
				continue;
			}	
			return sb.toString();		
		}
	}
}
