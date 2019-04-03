package com.marklogic.utils;

import com.marklogic.client.eval.EvalResultIterator;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.hub.ApplicationConfig;
import com.marklogic.hub.impl.DataHubImpl;
import com.marklogic.hub.impl.HubConfigImpl;
import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

public class TestsHelper {

    String projectDir = new File("").getAbsolutePath();
    private HubConfigImpl _hubConfig;
    private HubConfigImpl _adminHubConfig;
    private DataHubImpl _dataHub;
    private DataHubImpl _adminDataHub;
    private Properties props;

    protected void allCombos(ComboListener listener) {
        listener.onCombo();
    }

    protected BuildResult runTask(String... task) {
        return GradleRunner.create()
                .withProjectDir(new File(projectDir))
                .withArguments(task)
                .withDebug(true)
                .build();
    }

    public void clearDatabases(String... databases) {
        ServerEvaluationCall eval = _adminHubConfig.newStagingClient().newServerEval();
        String installer =
                "declare variable $databases external;\n" +
                        "for $database in fn:tokenize($databases, \",\")\n" +
                        "return\n" +
                        "  xdmp:eval('\n" +
                        "    cts:uris() ! xdmp:document-delete(.)\n" +
                        "  ',\n" +
                        "  (),\n" +
                        "  map:entry(\"database\", xdmp:database($database))\n" +
                        "  )";
        eval.addVariable("databases", String.join(",", databases));
        EvalResultIterator result = eval.xquery(installer).eval();
        if (result.hasNext()) {
//            logger.error(result.next().getString());
            System.out.println(result.next().getString());
        }
    }

    protected void deleteResourceDocs() {
        try {
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "flows").toString()));
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "steps").toString()));
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "plugins").toString()));
//            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "plugins", "mappings").toString()));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected void copyRunFlowResourceDocs() {
        try {
            System.out.println(projectDir);
            FileUtils.copyDirectory(new File(Paths.get("src/test/resources/plugins").toString()),
                    new File(Paths.get("plugins").toString()));
//            FileUtils.copyDirectory(new File(Paths.get(projectDir,"src/test/resources/flows").toString()),
//                    new File(Paths.get(projectDir,"flows").toString()));
//            FileUtils.copyDirectory(new File(Paths.get(projectDir,"src/test/resources/steps").toString()),
//                    new File(Paths.get(projectDir,"steps").toString()));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected void configureHubConfig() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(ApplicationConfig.class);
        ctx.refresh();
        _hubConfig = ctx.getBean(HubConfigImpl.class);
        _hubConfig.createProject(projectDir);
        _dataHub = ctx.getBean(DataHubImpl.class);
        _hubConfig.refreshProject();
    }

    protected void configureAdminHubConfig() {
        AnnotationConfigApplicationContext ctx1 = new AnnotationConfigApplicationContext();
        ctx1.register(ApplicationConfig.class);
        ctx1.refresh();
        _adminHubConfig = ctx1.getBean(HubConfigImpl.class);
        _adminHubConfig.createProject(projectDir);
        _adminHubConfig.setMlUsername(getPropertyFromPropertiesFile("mlSecurityUsername"));
        _adminHubConfig.setMlPassword(getPropertyFromPropertiesFile("mlSecurityPassword"));
        _adminDataHub = ctx1.getBean(DataHubImpl.class);
        _adminHubConfig.refreshProject();
        _adminDataHub.wireClient();
    }

    protected void loadPropertiesFile() {
        props = new Properties();
        try {
            props.load(new FileInputStream("gradle.properties"));
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected String getPropertyFromPropertiesFile(String key) {
        return props.getProperty(key);
    }

    protected void setUpSpecs() {
        loadPropertiesFile();
        configureHubConfig();
        configureAdminHubConfig();
    }

    public HubConfigImpl hubConfig() {
        return _hubConfig;
    }

    public HubConfigImpl adminHubConfig() {
        return _adminHubConfig;
    }
}
