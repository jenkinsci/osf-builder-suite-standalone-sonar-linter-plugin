package org.jenkinsci.plugins.osfbuildersuite.standalonesonar;

import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;

public class DefaultClientInputFile implements ClientInputFile {
    private final File dir;
    private final String path;

    @SuppressWarnings("WeakerAccess")
    public DefaultClientInputFile(File dir, String path) {
        this.dir = dir;
        this.path = path;
    }

    @Override
    public String getPath() {
        return (new File(dir, path)).getAbsolutePath();
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
        return Files.newInputStream((new File(dir, path)).toPath());
    }

    @Override
    public String contents() throws IOException {
        return new String(Files.readAllBytes((new File(dir, path)).toPath()), Charset.forName("UTF-8"));
    }

    @Override
    public String relativePath() {
        return path;
    }

    @Override
    public URI uri() {
        return (new File(dir, path)).toURI();
    }
}
