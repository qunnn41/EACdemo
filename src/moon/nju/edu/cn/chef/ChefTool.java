//package moon.nju.edu.cn.chef;
//
//import java.io.File;
//import java.util.List;
//import java.util.Properties;
//
//import javax.inject.Singleton;
//
//import org.jclouds.Constants;
//import org.jclouds.ContextBuilder;
//import org.jclouds.chef.ChefApi;
//import org.jclouds.chef.ChefService;
//import org.jclouds.chef.config.ChefProperties;
//import org.jclouds.chef.domain.BootstrapConfig;
//import org.jclouds.chef.domain.BootstrapConfig.SSLVerifyMode;
//import org.jclouds.chef.domain.CookbookVersion;
//import org.jclouds.chef.util.RunListBuilder;
//import org.jclouds.compute.domain.ExecResponse;
//import org.jclouds.domain.LoginCredentials;
//import org.jclouds.scriptbuilder.domain.OsFamily;
//import org.jclouds.scriptbuilder.domain.Statement;
//import org.jclouds.ssh.SshClient;
//import org.jclouds.sshj.config.SshjSshClientModule;
//
//import com.google.common.base.Charsets;
//import com.google.common.collect.ImmutableSet;
//import com.google.common.io.Files;
//import com.google.common.net.HostAndPort;
//import com.google.inject.Key;
//import com.google.inject.TypeLiteral;
//
//import moon.nju.edu.cn.demo.Server;
//
//public class ChefTool {
//	private static ChefTool chefTool = null;
//	final ChefService chefService;
//	final SshClient.Factory sshFactory;
//	
//	@Singleton
//	public static ChefTool getInstance() throws Exception {
//		if (chefTool == null) {
//			chefTool = new ChefTool();
//		}
//		return chefTool;
//	}
//	
//	private ChefTool() throws Exception {
//		String client = "jsmith";
//    	String organization = "4thcoffee";
//    	String validator = organization + "-validator";
//    	//chef-server in /etc/hosts
//    	String endpoint = "https://chef-server/organizations/" + organization;
//    	String credentialPath = System.getProperty("user.home") + "/chef-repo/.chef/";
//    	String clientCredential = Files.toString(new File(credentialPath + client + ".pem"), Charsets.UTF_8);
//    	String validatorCredential = Files.toString(new File(credentialPath + validator + ".pem"), Charsets.UTF_8);
//    	
//    	Properties chefConfig = new Properties();
//    	chefConfig.put(ChefProperties.CHEF_VALIDATOR_NAME, validator);
//    	chefConfig.put(ChefProperties.CHEF_VALIDATOR_CREDENTIAL, validatorCredential);
//    	chefConfig.put(Constants.PROPERTY_RELAX_HOSTNAME, "true");
//    	//skip ssl verify
//    	chefConfig.put(Constants.PROPERTY_TRUST_ALL_CERTS, "true");
//    	
//    	ChefApi chefApi= ContextBuilder.newBuilder("chef")
//				.endpoint(endpoint)
//    			.credentials(client, clientCredential)
//    			.overrides(chefConfig)
//    			.modules(ImmutableSet.of(new SshjSshClientModule()))
//    			.buildApi(ChefApi.class);
//    	
//    	chefService = chefApi.chefService();
//    	
//    	sshFactory = ContextBuilder.newBuilder("chef")
//    			.endpoint(endpoint)
//    			.credentials(client, clientCredential)
//    			.overrides(chefConfig)
//    			.modules(ImmutableSet.of(new SshjSshClientModule()))
//    			.buildInjector()
//    			.getInstance(Key.get(new TypeLiteral<SshClient.Factory>() {}));
//	}
//	
//	public int install(Server server, String recipe) {
//		System.out.printf("install %s on %s\n", recipe, server);
//		int m = 1; if (m == 1) return m;
//		List<String> runlist = null;
//		Iterable<? extends CookbookVersion> cookbookVersions = chefService.listCookbookVersions();
//		for (CookbookVersion cookbook : cookbookVersions) {
//			if (recipe.equals(cookbook.getCookbookName()) || cookbook.getCookbookName().startsWith(recipe)) {
//				runlist = new RunListBuilder().addRecipe(recipe).build();
//			}
//		}
//		
//		if (runlist == null) {
//			System.out.printf("Recipe %s is not avaliable on chef server\n", recipe);
//			return -1;
//		}
//		
//    	BootstrapConfig bootstrapConfig = BootstrapConfig.builder()
//    			.runList(runlist)
//    			.sslVerifyMode(SSLVerifyMode.NONE)
//    			.build();
//    	
//    	/**
//    	 * @TODO change identifier of each node, here we use server.getIP()
//    	 */
//    	chefService.updateBootstrapConfigForGroup(server.getIP(), bootstrapConfig);
//    	Statement bootstrap = chefService.createBootstrapScriptForGroup(server.getIP());
//    	
//    	SshClient sshClient = sshFactory.create(HostAndPort.fromParts(server.getIP(), 22), 
//    			LoginCredentials.builder().authenticateSudo(true)
//    			.user(server.getUsername()).password(server.getPassword())
//    			.build());
//    	sshClient.connect();
//
//    	int res = -1;
//    	try {
//    		String rawScript = bootstrap.render(OsFamily.UNIX);
//    		
//    		//skip chef installation and change mode about file and folder
//    		rawScript = rawScript.substring(rawScript.indexOf("cat"));
//    		rawScript = "sudo chmod 777 /etc/chef \n" + rawScript;
//    		rawScript = "sudo chmod 777 /etc/chef/client.rb \n" + rawScript;
//    		
//    		rawScript = rawScript.replace("chef-client", "sudo chef-client");
//    		rawScript = rawScript.replace("cat > ", "sudo cat > ");
//    		
////    		System.out.println("Raw script rendered.\n\n" + rawScript + "\n\n");
//    		System.out.println("Bootstrap script executed...");
//    		
//    		/**
//    		 * the shell might not succeed during one process, that is the chef problem
//    		 * u can run "sudo chef-client -j /etc/chef/first-boot.json" until it is successful
//    		 */
//    		ExecResponse result = sshClient.exec(rawScript);
//    		System.out.println(result.toString());
//    		
//    		/**
//    		 * another solution
//    		while (result.getExitStatus() != 0) {
//    			result = sshClient.exec(rawScript);
//        		System.out.println(result.toString());
//    		}
//    		*/
//    		res = result.getExitStatus();
//    	} catch (Throwable t) {
//    		System.out.println("Exception: " + t.getMessage());
//    	} finally {
//    		sshClient.disconnect();
//    		System.out.println("SSH closed.");
//    	}
//    	
//    	return res;
//	}
//	
//	public static void main(String[] args) throws Exception {
//		ChefTool chefTool = ChefTool.getInstance();
////		chefTool.install("192.168.1.51", "vagrant", "vagrant", "apache_hadoop");
//		chefTool.install("192.168.1.14", "vagrant", "vagrant", "lamp_php");
//	}
//	
//	/**
//	 * test for method install
//	 * @param IP
//	 * @param username
//	 * @param password
//	 * @param recipe
//	 * @return
//	 */
//	public int install(String IP, String username, String password, String recipe) {
//		System.out.printf("install %s on %s\n", recipe, IP);
//		List<String> runlist = null;
//		Iterable<? extends CookbookVersion> cookbookVersions = chefService.listCookbookVersions();
//		for (CookbookVersion cookbook : cookbookVersions) {
//			//add start with to coordinate hadoop
//			if (recipe.equals(cookbook.getCookbookName()) || cookbook.getCookbookName().startsWith(recipe)) {
//				runlist = new RunListBuilder().addRecipe(recipe).build();
//			}
//		}
//		
//		if (runlist == null) {
//			System.out.printf("Recipe %s is not avaliable on chef server\n", recipe);
//			return -1;
//		}
//		
//    	BootstrapConfig bootstrapConfig = BootstrapConfig.builder()
//    			.runList(runlist)
//    			.sslVerifyMode(SSLVerifyMode.NONE)
//    			.build();
//    	
//    	/**
//    	 * @TODO change identifier of each node, here we use server.getIP()
//    	 */
//    	chefService.updateBootstrapConfigForGroup(IP, bootstrapConfig);
//    	Statement bootstrap = chefService.createBootstrapScriptForGroup(IP);
//    	
//    	SshClient sshClient = sshFactory.create(HostAndPort.fromParts(IP, 22), 
//    			LoginCredentials.builder().authenticateSudo(true)
//    			.user(username).password(password)
//    			.build());
//    	sshClient.connect();
//
//    	int res = -1;
//    	try {
//    		String rawScript = bootstrap.render(OsFamily.UNIX);
//    		
//    		//skip chef installation and change mode about file and folder
//    		rawScript = rawScript.substring(rawScript.indexOf("cat"));
//    		rawScript = "sudo chmod 777 /etc/chef \n" + rawScript;
//    		rawScript = "sudo chmod 777 /etc/chef/client.rb \n" + rawScript;
//    		
//    		rawScript = rawScript.replace("chef-client", "sudo chef-client");
//    		rawScript = rawScript.replace("cat > ", "sudo cat > ");
//    		
//    		System.out.println("Raw script rendered.\n\n" + rawScript + "\n\n");
//    		System.out.println("Bootstrap script executed...");
//    		
//    		/**
//    		 * the shell might not succeed during one process, that is the chef problem
//    		 * u can run "sudo chef-client -j /etc/chef/first-boot.json" until it is successful
//    		 */
//    		ExecResponse result = sshClient.exec(rawScript);
//    		System.out.println("result:\n\n" + result.toString());
//    		
//    		/**
//    		 * another solution
//    		while (result.getExitStatus() != 0) {
//    			result = sshClient.exec(rawScript);
//        		System.out.println(result.toString());
//    		}
//    		*/
//    		res = result.getExitStatus();
//    	} catch (Throwable t) {
//    		t.printStackTrace();
////    		System.out.println("Exception: " + t.getMessage());
//    	} finally {
//    		sshClient.disconnect();
//    		System.out.println("SSH closed.");
//    	}
//    	
//    	return res;
//	}
//}
