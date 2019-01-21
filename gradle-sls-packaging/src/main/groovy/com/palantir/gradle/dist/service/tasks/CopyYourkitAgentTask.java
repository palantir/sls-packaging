package com.palantir.gradle.dist.service.tasks;

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class CopyYourkitAgentTask extends DefaultTask {
    public CopyYourkitAgentTask() {
        setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
        setDescription("Copies YourKit agent");
    }

    @OutputFile
    public File getOutputFile() {
        return new File(getProject().getBuildDir() + "/libs/linux-x86-64/libyjpagent.so");
    }

    @TaskAction
    public void copyYourkitAgent() throws IOException {
        InputStream src = JavaServiceDistributionPlugin.class.getResourceAsStream("/linux-x86-64/libyjpagent.so");
        Path dest = getOutputFile().toPath();
        dest.getParent().toFile().mkdirs();
        Files.copy(src, dest);
    }

}
