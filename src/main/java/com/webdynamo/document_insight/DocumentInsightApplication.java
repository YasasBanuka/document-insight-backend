package com.webdynamo.document_insight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocumentInsightApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocumentInsightApplication.class, args);
	}

}
