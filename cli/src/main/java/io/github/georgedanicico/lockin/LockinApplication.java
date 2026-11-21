package io.github.georgedanicico.lockin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LockinApplication {

	public static void main(String[] args) {
		var application = new SpringApplication(LockinApplication.class);
		// A command's --name=value options are its own, not Spring properties: --id=… must not configure the app.
		application.setAddCommandLineProperties(false);
		System.exit(SpringApplication.exit(application.run(args)));
	}

}
