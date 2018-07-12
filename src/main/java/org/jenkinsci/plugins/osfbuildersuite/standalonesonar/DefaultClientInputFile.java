package org.jenkinsci.plugins.osfbuildersuite.standalonesonar;

import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;

public class DefaultClientInputFile implements ClientInputFile {
    private final String absolutePath;
    private final String relativePath;

    @SuppressWarnings("WeakerAccess")
    public DefaultClientInputFile(String absolutePath, String relativePath) {
        this.absolutePath = absolutePath;
        this.relativePath = relativePath;
    }

    @Override
    public String getPath() {
        return absolutePath;
    }

    @Override
    public boolean isTest() {
        return false;
    }

    @Override
    public Charset getCharset() {
        return Charset.forName("UTF-8");
    }

    @Override
    public <G> G getClientObject() {
        return null;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return Files.newInputStream((new File(absolutePath)).toPath());
    }

    @Override
    public String contents() throws IOException {
        return new String(Files.readAllBytes((new File(absolutePath)).toPath()), Charset.forName("UTF-8"));
    }

    @Override
    public String relativePath() {
        return relativePath;
    }
}
