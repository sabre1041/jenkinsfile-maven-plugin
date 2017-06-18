jenkinsfile-maven-plugin
========================

 [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.andyserver.maven.plugins/jenkinsfile-maven-plugin/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/com.andyserver.maven.plugins/jenkinsfile-maven-plugin)


Maven plugin the validate the syntax of a [Jenkinsfile](https://jenkins.io/doc/book/pipeline/jenkinsfile/)

## Overview

The [Pipelines-Model-Definition](https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Model+Definition+Plugin) plugin provides a declarative syntax to construct Jenkins pipelines. Aside from the definition itself, the plugin exposes an endpoint to validate the syntax of a Jenkinsfile. This plugin communicates with a Jenkins server configured with this plugin to validate the syntax of a Jenkinsfile defined within a Maven project

## Building

Clone the repository and build the plugin

```
mvn clean install
```

## Usage

Add the plugin to the `pom.xml`. The following example demonstrates how the plugin can be employed within a project:

```
<plugin>
	<groupId>com.andyserver.maven.plugins</groupId>
	<artifactId>jenkinsfile-maven-plugin</artifactId>
	<version>${jenkinsfile.maven.plugin.version}</version>
	<configuration>
		<username>admin</username>
		<password>password</password>
	</configuration>
</plugin>
```  
	
Execute the plugin

```
mvn jenkinsfile:validate
```

## Configuration

The following parameters are available to fine tune the plugin execution

| Element | Description | Property | Default |
| :---------- | :-------------- | :---------- | :-------- |
| jenkinsfile | Location of the Jenkinsfile within the project | `jenkinsfile` | Jenkinsfile |
| username | Jenkins username | `jenkins.username` | |
| password | Jenkins password | `jenkins.password` | |
| insecureSSL | Whether to ignore insecure SSL certificates | `jenkins.insecureSSL` | `false` |
| server | Jenkins server | `jenkins.server` | `http://localhost:8080/jenkins` |

