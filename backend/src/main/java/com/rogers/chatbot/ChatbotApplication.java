package com.rogers.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatbotApplication {

  public static void main(String[] args) {
    useWindowsCertificates();
    SpringApplication.run(ChatbotApplication.class, args);
  }

  /**
   * On Windows, trust the same certificates as the browser.
   *
   * <p>Corporate networks often inspect HTTPS with a company certificate that is installed in
   * Windows but not in Java's own list, so calls to Google fail ("Failed computing credential
   * metadata", "PKIX path building failed"). Skipped if a trust store was configured explicitly.
   */
  private static void useWindowsCertificates() {
    boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    boolean configured = System.getProperty("javax.net.ssl.trustStore") != null
        || System.getProperty("javax.net.ssl.trustStoreType") != null;
    if (windows && !configured) {
      System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
    }
  }
}
