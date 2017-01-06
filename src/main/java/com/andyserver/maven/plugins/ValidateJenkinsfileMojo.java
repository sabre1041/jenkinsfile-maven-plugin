package com.andyserver.maven.plugins;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "validate", defaultPhase = LifecyclePhase.INTEGRATION_TEST)
public class ValidateJenkinsfileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(property = "jenkinsfile", defaultValue = "Jenkinsfile")
    private String jenkinsfile;

    @Parameter(property = "jenkins.username")
    private String username;

    @Parameter(property = "jenkins.password")
    private String password;

    @Parameter(property = "jenkins.insecureSSL")
    private boolean insecureSSL;

    @Parameter(property = "jenkins.server", defaultValue = "http://localhost:8080/jenkins")
    private String server;

    private static final String PIPELINE_VALIDATION_ENDPOINT = "/pipeline-model-converter/validate";
    private static final String SUCCESSFUL_VALIDATION = "Jenkinsfile successfully validated.";

    public void execute() throws MojoExecutionException {

        File projectJenkinsFile = new File(project.getBasedir().getAbsolutePath(), jenkinsfile);

        if (!projectJenkinsFile.exists()) {
            throw new MojoExecutionException("Jenkinsfile not found at " + projectJenkinsFile.getAbsolutePath());
        }

        String jenkinsValidationEndpoint = String.format("%s%s", server, PIPELINE_VALIDATION_ENDPOINT);

        HttpContext localContext = new BasicHttpContext();
        HttpClientBuilder httpBuilder = HttpClients.custom();

        // Configure Authentication
        if (username != null && password != null) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
            provider.setCredentials(AuthScope.ANY, credentials);
            httpBuilder.setDefaultCredentialsProvider(provider);
            httpBuilder.addInterceptorFirst(new PreemptiveAuth());
            localContext.setAttribute("preemptive-auth", new BasicScheme());
        }

        if (insecureSSL) {
            configureInsecureSSL(httpBuilder);
        }

        CloseableHttpClient httpClient = httpBuilder.build();

        HttpPost post = null;

        try {

            post = new HttpPost(jenkinsValidationEndpoint);

            Crumb crumb = getCsrfCrumb(httpClient, localContext);

            if (crumb != null) {
                post.addHeader(crumb.getCrumbRequestField(), crumb.getCrumb());
            }

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.STRICT);

            try {
                String fileString = new String(Files.readAllBytes(projectJenkinsFile.toPath()));

                builder.addTextBody("jenkinsfile", fileString);
                HttpEntity entity = builder.build();
                post.setEntity(entity);

            } catch (IOException e) {
                throw new MojoExecutionException("Error reading Jenkinsfile", e);
            }

            try {

                HttpResponse httpResponse = httpClient.execute(post, localContext);

                int statusCode = httpResponse.getStatusLine().getStatusCode();

                if (statusCode != HttpStatus.SC_OK) {

                    if (statusCode == HttpStatus.SC_NOT_FOUND) {
                        throw new MojoExecutionException(String.format(
                                "%n%nJenkinsfile validation REST endpoint not found on Jenkins server (%s).%nPlease confirm that the pipeline-model-definition plugin is installed.", server));
                    }

                    String errorMessageTemplate = "%n%nInvalid HTTP response code: %s.";

                    if (statusCode == HttpStatus.SC_FORBIDDEN){
                        errorMessageTemplate += "%nPlease note that Jenkins may return a 403 even if your credentials are incorrect, which would normally result in a 401.";
                    }

                    throw new MojoExecutionException(String.format(errorMessageTemplate, statusCode));
                }

                String response = EntityUtils.toString(httpResponse.getEntity());

                if (response != null && response.contains(SUCCESSFUL_VALIDATION)) {
                    getLog().info(jenkinsfile + " Successfully Validated");
                } else {
                    throw new MojoExecutionException(response);
                }

            } catch (IOException e) {
                throw new MojoExecutionException("Error communicating with Jenkins server", e);
            }

        } finally {
            if (post != null) {
                post.releaseConnection();
            }
        }
    }

    /**
     * Allow SSL connections to utilize untrusted certificates
     * 
     * @param httpClientBuilder
     * @throws MojoExecutionException
     */
    private void configureInsecureSSL(HttpClientBuilder httpClientBuilder) throws MojoExecutionException {
        try {
            SSLContextBuilder sslBuilder = new SSLContextBuilder();

            // Accept all Certificates
            sslBuilder.loadTrustMaterial(null, AcceptAllTrustStrategy.INSTANCE);

            SSLContext sslContext = sslBuilder.build();

            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
                    NoopHostnameVerifier.INSTANCE);
            httpClientBuilder.setSSLSocketFactory(sslsf);
        } catch (Exception e) {
            throw new MojoExecutionException("Error setting insecure SSL configuration", e);
        }
    }

    /**
     * Retrieve CSRF Crumb
     * 
     * @param httpClient
     * @param localContext
     * @return
     * @throws MojoExecutionException
     */
    private Crumb getCsrfCrumb(HttpClient httpClient, HttpContext localContext) throws MojoExecutionException {

        HttpGet httpGet = null;

        try {

            String crumbContext = "/crumbIssuer/api/xml?xpath="
                    + URLEncoder.encode("concat(//crumbRequestField,\":\",//crumb)", "UTF-8");
            String jenkinsCrumbPath = String.format("%s%s", server, crumbContext);

            httpGet = new HttpGet(jenkinsCrumbPath);

            HttpResponse httpResponse = httpClient.execute(httpGet, localContext);

            int responseCode = httpResponse.getStatusLine().getStatusCode();

            if (responseCode != HttpStatus.SC_OK) {
                getLog().warn("Could not obtain CSRF crumb. Response code: " + responseCode);
                return null;
            }

            String rawCrubResponse = EntityUtils.toString(httpResponse.getEntity());

            String[] crubResponse = rawCrubResponse.split(":");

            if (crubResponse.length != 2) {
                getLog().warn("Unexpected CSRF crumb response: " + responseCode);
                return null;
            }

            return new Crumb(crubResponse[0], crubResponse[1]);

        } catch (Exception e) {
            throw new MojoExecutionException("Error Retrieving Jenkins Crumb", e);
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
    }

}
