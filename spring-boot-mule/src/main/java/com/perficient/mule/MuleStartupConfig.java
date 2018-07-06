package com.perficient.mule;

import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.mule.runtime.api;
import org.mule.api.MuleException;
import org.mule.api.client.MuleClient;
import org.mule.api
import org.mule.api.context.MuleContextBuilder;
import org.mule.api.context.MuleContextFactory;
import org.mule.api.transaction.TransactionManagerFactory;
import org.mule.client.DefaultLocalMuleClient;
import org.mule.config.ConfigResource;
import org.mule.config.spring.SpringXmlConfigurationBuilder;
import org.mule.context.DefaultMuleContextBuilder;
import org.mule.context.DefaultMuleContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.json.JsonParser;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import javax.annotation.PostConstruct;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mule.module.spring.transaction.SpringTransactionFactory;
import org.mule.module.spring.transaction.SpringTransactionManagerFactory;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Created by tap on 10/30/16.
 */
@Configuration
public class MuleStartupConfig {
	private static final Logger logger = LoggerFactory.getLogger(MuleStartupConfig.class);

	private MuleContext muleContext;

	@Autowired
	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Autowired
	private ApplicationContext context;

	@Value("${mule.config.files}")
	String muleConfigFiles;

//	@Value("classpath*:/META-INF/mule-artifact/mule-artifact.json")
//	Resource[] muleArtifacts;
	
//	@Value("classpath*:*/*.xml")
//	Resource[] muleApps;
	
	@Bean
	public UserTransaction userTransaction() throws Throwable {
		logger.info("Creating UserTransaction");
		UserTransactionImp userTransactionImp = new UserTransactionImp();
		userTransactionImp.setTransactionTimeout(1000);
		return userTransactionImp;
	}

	@Configuration
	public static class TransactionManagerConfig {
		@Bean(initMethod = "init", destroyMethod = "close")
		public TransactionManager transactionManagerJTA() throws Throwable {

			logger.info("Creating TransactionManager");
			UserTransactionManager userTransactionManager = new UserTransactionManager();
			userTransactionManager.setForceShutdown(false);
			return userTransactionManager;
		}
	}

	@Bean
	public PlatformTransactionManager platformTransactionManager(UserTransaction userTransaction,
			TransactionManager transactionManager) throws Throwable {

		logger.info("Creating PlatformTransactionManager");
		return new JtaTransactionManager(userTransaction, transactionManager);
	}

	@Bean
	public TransactionManagerFactory transactionManagerFactory(PlatformTransactionManager platformTransactionManager) {
		logger.info("Creating TransactionManagerFactory");
		SpringTransactionFactory factory = new SpringTransactionFactory();
		factory.setManager(platformTransactionManager);
		SpringTransactionManagerFactory managerFactory = new SpringTransactionManagerFactory();
		managerFactory.setTransactionManager(new JtaTransactionManager().getTransactionManager());
		return managerFactory;
	}

	@Bean
	MuleClient muleClient(MuleContext muleContext) throws MuleException {
		logger.info("Creating MuleClient");
		MuleClient client = new DefaultLocalMuleClient(muleContext);
		return client;

	}

	@Bean(name = "muleContext")
	MuleContext muleContext() throws MuleException {
		logger.info("Creating MuleContext");
		MuleContextFactory muleContextFactory = new DefaultMuleContextFactory();
		logger.info("Loading Mule config files {}", muleConfigFiles);

		//setConfiguration();

		//String[] configFiles = muleConfigFiles.split(",");
		ArrayList configFiles = getConfigFiles();
		System.out.println("configFiles: " + configFiles);
		ConfigResource configs[] = createConfigResources(configFiles);
		// SpringXmlConfigurationBuilder builder = new
		// SpringXmlConfigurationBuilder(configFiles);
		SpringXmlConfigurationBuilder builder = new SpringXmlConfigurationBuilder(configs);
		builder.setParentContext(context);
		MuleContextBuilder contextBuilder = new DefaultMuleContextBuilder();
		MuleContext context = muleContextFactory.createMuleContext(builder, contextBuilder);
		logger.info("Created MuleContext");
		return context;
	}

	private ConfigResource[] createConfigResources(ArrayList<String> muleConfigFiles) {
		//String[] configFiles = muleConfigFiles.split(",");
		ConfigResource configs[] = new ConfigResource[muleConfigFiles.size()];
		for (int i = 0; i < muleConfigFiles.size(); i++) {
			String configFile = (String)muleConfigFiles.get(i);
			logger.debug("Loading config file {}", configFile);
			System.out.println("Loading config file {}" + configFile);;
			//Resource resource = resourceLoader.getResource(configFile);
			Resource resource = new ClassPathResource(configFile);
			//System.out.println("resources: " + getResourcesFromJarFile());
			try {
				configs[i] = new ConfigResource(resource.getURL());
			} catch (IOException e) {
				logger.error("Unable to load configuration {} ", configFile, e);
			}
		}

		return configs;
	}

	@Configuration
	public static class MuleContextPostConstruct {
		@Autowired
		MuleContext muleContext;

		@PostConstruct
		MuleContext createMuleContext() throws MuleException {
			logger.info("Starting MuleContext....");
			muleContext.start();
			return muleContext;
		}
	}

	public ArrayList getConfigFiles() {
		//Configuration configuration = null;
		ArrayList<String> configFiles = new ArrayList<String>();
		try {
			ClassLoader cl = this.getClass().getClassLoader();
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
			Resource[] resources = resolver.getResources("classpath*:/META-INF/mule-artifact/mule-artifact.json");
			if (resources.length == 0) {
				logger.warn("Configuration could not be loaded from file! Using default configuration.");
				// configuration = getDefaultConfiguration();
			}
			for (Resource resource : resources) {

				
				//configuration = new ObjectMapper().readValue(resource.getInputStream(), Configuration.class);
			 
				String artifactPath = resource.getURL().getPath();
				String path = artifactPath.substring(0, artifactPath.indexOf("META-INF"));
			    System.out.println("path" + artifactPath);
				JsonParser jsonParser = new BasicJsonParser();
				Map<String, Object> jsonMap = null;

			    try {
			    	 BufferedReader in = new BufferedReader(
			    		     new InputStreamReader(resource.getInputStream()));

			   // 	 JSONParser jsonParser = new JSONParser();
			   // 	 JSONObject jsonObject = (JSONObject)jsonParser.parse(
			    //	       new InputStreamReader(resource.getInputStream(), "UTF-8"));
			   // 	 String configFiles = (String) jsonObject.get("configs");
			    //	 System.out.println("configFiles: "+ configFiles);
//			    		        
			    	 String inputLine;
			    	 String fileString = "";
			    		        while ((inputLine = in.readLine()) != null)
			    		        	fileString += inputLine;
			    		        in.close();
			    		        
			    	System.out.println("json config file: " + fileString);
 			        jsonMap = jsonParser.parseMap(fileString);
			    } catch (IOException e) {
			        throw new RuntimeException("Failed to read file", e);
			    }
			    
			  //  List<String> myObjects = jsonMap.readValue("configs", new TypeReference<List<String>>(){});
			    
			    List<String> files  =   (ArrayList)jsonMap.get("configs");
			   
			    for(String file: files)
			    {
			    	//String url = path + file.replace("\"", "").trim();
			    	String url = file.replace("\"", "").trim();
			    	configFiles.add(url);
			    	 
			    }
			   System.out.println("configFiles: " + configFiles);
			 		 
			}
		} catch (Exception e) {
			logger.warn("Configuration could not be loaded from file! Using default configuration.", e);
			// configuration = getDefaultConfiguration();
		}
		
		return configFiles;
	}
	
	
	 private  Collection<String> getResourcesFromJarFile(
		        final File file,
		        final Pattern pattern){
		        final ArrayList<String> retval = new ArrayList<String>();
		        ZipFile zf;
		        try{
		            zf = new ZipFile(file);
		        } catch(final ZipException e){
		            throw new Error(e);
		        } catch(final IOException e){
		            throw new Error(e);
		        }
		        final Enumeration e = zf.entries();
		        while(e.hasMoreElements()){
		            final ZipEntry ze = (ZipEntry) e.nextElement();
		            final String fileName = ze.getName();
		            final boolean accept = pattern.matcher(fileName).matches();
		            if(accept){
		                retval.add(fileName);
		            }
		        }
		        try{
		            zf.close();
		        } catch(final IOException e1){
		            throw new Error(e1);
		        }
		        return retval;
		    }


}
