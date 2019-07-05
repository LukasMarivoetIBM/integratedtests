package dev.voras.inttests.basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;

import org.apache.commons.logging.Log;

import dev.voras.BeforeClass;
import dev.voras.Test;
import dev.voras.common.artifact.ArtifactManager;
import dev.voras.common.artifact.IArtifactManager;
import dev.voras.common.artifact.IBundleResources;
import dev.voras.common.artifact.ISkeletonProcessor.SkeletonType;
import dev.voras.common.ipnetwork.ICommandShell;
import dev.voras.common.linux.ILinuxImage;
import dev.voras.common.linux.LinuxImage;
import dev.voras.core.manager.Logger;
import dev.voras.core.manager.StoredArtifactRoot;
import dev.voras.core.manager.TestProperty;

/**
 * This integration test will prove that the basic framework is working by running a 
 * few easy IVTs.
 * 
 *  The test will download the runtime.zip and extract the voras-boot.jar file.
 *  
 *  It will then run the IVTs from the command line.
 * 
 * @author Michael Baylis
 *
 */
public class RunCommandlineTests {
	
    @Logger
    public Log logger;
    
	@LinuxImage(capabilities= {"java8","maven"})
	public ILinuxImage linuxPrimary;
	
	@StoredArtifactRoot
	public Path storedArtifactRoot;
	
	// TODO provide direct access in CPS so we can ignore this suffix/prefix nonsense when it is a fixed property
	@TestProperty(prefix="integrated.tests", suffix="maven.repository")
	public String mavenRepository;  // The maven repository that contains the code we will be testing
	
	@ArtifactManager
	public IArtifactManager artifactManager;  // TODO we should get the bundleresources object direct
	
	private ICommandShell shell; // get a command shell
	private Path          homePath; // The home directory of the default userid
	
	/**
	 * Set up the shell and the filesystem we will use later
	 * 
	 * @throws Exception - standard catchall
	 */
	@BeforeClass
	public void setupShells() throws Exception {
		//*** Obtain the shell that we are going to use
		shell = linuxPrimary.getCommandShell();
		logger.info("Obtained command shell to linux server");
		
		//*** Obtain the home directory
		this.homePath = linuxPrimary.getHome();
	}
	
	/**
	 * Set up the .m2/settings.xml file read for mvn commands.  The repository is provided as a test property
	 * 
	 * @throws Exception - standard catchall
	 */
	@BeforeClass
	public void setupM2() throws Exception {
		//*** Create the .m2 directory if necessary
		Path settings = this.homePath.resolve(".m2/settings.xml");
		if (!Files.exists(settings.getParent())) {
			Files.createDirectory(settings.getParent());
		}
		
		// TODO should have this as an annotated file
		IBundleResources bundleResources = artifactManager.getBundleResources(getClass());
		
		// Get the skeleton settings.xml and provide the test repo
		HashMap<String,Object> parameters = new HashMap<>();
		parameters.put("vorasrepo", this.mavenRepository);		
		InputStream is = bundleResources.retrieveSkeletonFile("settings.xml", parameters, SkeletonType.VELOCITY);		
		
		//*** Copy the file to the test system
		Files.copy(is, settings);
	}
	
	/**
	 * Retreive the runtime.zip from maven and extract the voras-boot.jar file
	 * 
	 * @throws Exception - standard catchall
	 */
	@BeforeClass
	public void setupVorasBoot() throws Exception {
		//*** Retrieve the runtime zip from the maven repository
		String response = this.shell.issueCommand("mvn -B org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=dev.voras:runtime:0.3.0-SNAPSHOT:zip > mvn.log;echo maven-rc=$?");
		assertThat(response).as("maven rc search").contains("maven-rc=0"); // check we exited 0
		Path log = this.homePath.resolve("mvn.log");  // the log file
		Path saLog = this.storedArtifactRoot.resolve("mvn.log"); // stored artifact file
		Files.copy(log, saLog); //copy it
		
		this.logger.info("Runtime successfully download");
		
		//*** Unzip the runtime to get the voras-boot
		response = this.shell.issueCommand("unzip -o .m2/repository/dev/voras/runtime/0.3.0-SNAPSHOT/runtime-0.3.0-SNAPSHOT.zip > unzip.log;echo zip-rc=$?");
		assertThat(response).as("zip rc search").contains("zip-rc=0");  // check we exited 0
		log = this.homePath.resolve("unzip.log"); // the log file
		saLog = this.storedArtifactRoot.resolve("unzip.log"); // the stored artifact
		Files.copy(log, saLog); // copy it
		
		this.logger.info("voras-boot unzipped");
	}
	
	/**
	 * Run the CoreIVT 
	 * 
	 * @throws Exception - standard catchall
	 */
	@Test
	public void runCoreIVT() throws Exception {
		
		// Build the command line we need to run the core ivt
		StringBuilder sb = new StringBuilder();
		sb.append("java ");                                                        // Run with the default java installation
		sb.append("-jar voras-boot.jar ");                                         // The installed boot jar
		sb.append("--remotemaven ");
		sb.append(mavenRepository);                                                // The framework/test maven repository
		sb.append(" ");
		sb.append("--obr mvn:dev.voras/dev.voras.uber.obr/0.3.0-SNAPSHOT/obr ");    // the framework obr
		sb.append("--obr mvn:dev.voras/dev.voras.ivt.obr/0.3.0-SNAPSHOT/obr " );    // The IVT Obr
		sb.append("--test dev.voras.ivt.core/dev.voras.ivt.core.CoreManagerIVT "); //  The Core IVT
		sb.append("--trace ");                                                     // Lets get as much bask as we can in case of failure
		sb.append("> coreivt.log ");                                               // Save the log
		sb.append(";echo voras-boot-rc=$?");                                       // check that the run ended with exit code 0
		
		logger.info("About to issue the command :-\n" + sb.toString());
		
		Instant start = Instant.now();
		String response = shell.issueCommand(sb.toString());  // run the command
		Instant end = Instant.now();
		logger.info("Command returned - took " + (end.getEpochSecond() - start.getEpochSecond()) + " seconds to run");
		
		Path log = this.homePath.resolve("coreivt.log");  // the log file from the command
		Path runLog = this.storedArtifactRoot.resolve("coreivt.log"); // the stored artifact
		Files.copy(log, runLog); // copy to stored artifacts
		
		assertThat(response).as("run command").contains("voras-boot-rc=0");  // check we exited 0
	}

}
