package com.rogers.chatbot.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.dialogflow.cx.v3.SessionsClient;
import com.google.cloud.dialogflow.cx.v3.SessionsSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class DialogflowConfig {

  private static final Logger log = LoggerFactory.getLogger(DialogflowConfig.class);

  @Bean(destroyMethod = "close")
  public SessionsClient sessionsClient(DialogflowProperties props) throws IOException {
    SessionsSettings.Builder settings = SessionsSettings.newBuilder().setEndpoint(props.endpoint());

    String keyFile = props.credentialsFile();
    if (StringUtils.hasText(keyFile) && Files.exists(Path.of(keyFile))) {
      try (InputStream in = Files.newInputStream(Path.of(keyFile))) {
        GoogleCredentials creds = GoogleCredentials.fromStream(in)
            .createScoped("https://www.googleapis.com/auth/cloud-platform");
        settings.setCredentialsProvider(FixedCredentialsProvider.create(creds));
      }
      log.info("Using service-account key {}", Path.of(keyFile).toAbsolutePath());
    } else {
      log.info("No key file at '{}', using Application Default Credentials", keyFile);
    }
    return SessionsClient.create(settings.build());
  }
}
