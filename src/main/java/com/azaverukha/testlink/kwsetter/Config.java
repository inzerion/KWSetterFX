package com.azaverukha.testlink.kwsetter;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Created by alexanderzaverukha on 3/12/16.
 */
public class Config {
    static Config instance;
    private String testlinkUrl = "";
    private String testLinkLogin = "";
    private String testLinkPassword = "";
    private Map<String, String> testLinkProjects = new HashMap<>();
    public Map<String, String> getTestLinkProjects() {
        return testLinkProjects;
    }


    private Config(){};

    public String getTestlinkUrl() {
        return testlinkUrl;
    }

    public String getTestLinkLogin() {
        return testLinkLogin;
    }

    public String getTestLinkPassword() {
        return testLinkPassword;
    }


    public static Config getInstance() {
        if(instance == null){
            instance = new Config();
        }
        return instance;
    }

    public void load() throws IOException {
        try(InputStream input = new FileInputStream("./config.properties")){
            load(input);
        }


    }

    private void load(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(input);
        testlinkUrl = properties.getProperty("testlink.url");
        testLinkLogin = properties.getProperty("testlink.login");
        testLinkPassword = properties.getProperty("testlink.password");
        String projects = properties.getProperty("testlink.projects");
        List<String> projectsMapsList = Arrays.asList(projects.split("[#|,]"));
        for(String projectMap : projectsMapsList){
            String[] projectMapArr = projectMap.split(":");
            testLinkProjects.put(projectMapArr[0].toLowerCase(), projectMapArr[1]);
        }

    }

    public void load(File file) throws IOException {
        load(new FileInputStream(file));
    }


}
