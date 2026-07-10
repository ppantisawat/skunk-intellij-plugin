package com.github.ppantisawat.skunk.intellij;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.components.libextensions.LibraryExtensionsManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class SkunkExtensionRegistrar implements ProjectActivity {
    private static final String PLUGIN_ID = "cc.pakanon.skunk.sql";
    private static final String INTELLIJ_COMPAT_MANIFEST = "META-INF/intellij-compat.xml";
    private static final Logger LOG = Logger.getInstance(SkunkExtensionRegistrar.class);

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        Path extensionJar = extensionJarPath();
        if (extensionJar == null) {
            return Unit.INSTANCE;
        }

        try {
            LibraryExtensionsManager.getInstance(project).addExtension(extensionJar);
        } catch (Exception exception) {
            if (!alreadyLoaded(exception)) {
                LOG.warn("Failed to register Skunk Scala extension from " + extensionJar, exception);
            }
        }
        return Unit.INSTANCE;
    }

    private static boolean alreadyLoaded(Exception exception) {
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("already loaded");
    }

    private static Path extensionJarPath() {
        IdeaPluginDescriptor descriptor = PluginManager.getInstance().findEnabledPlugin(PluginId.getId(PLUGIN_ID));
        if (descriptor == null) {
            return null;
        }

        Path pluginPath = descriptor.getPluginPath();
        if (Files.isRegularFile(pluginPath) && hasIntellijCompatManifest(pluginPath)) {
            return pluginPath;
        }

        Path libPath = pluginPath.resolve("lib");
        if (!Files.isDirectory(libPath)) {
            return null;
        }

        try (Stream<Path> jars = Files.list(libPath)) {
            return jars
                .filter(path -> path.getFileName().toString().endsWith(".jar"))
                .filter(SkunkExtensionRegistrar::hasIntellijCompatManifest)
                .findFirst()
                .orElse(null);
        } catch (IOException exception) {
            LOG.warn("Failed to scan Skunk plugin lib directory " + libPath, exception);
            return null;
        }
    }

    private static boolean hasIntellijCompatManifest(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            return jar.getJarEntry(INTELLIJ_COMPAT_MANIFEST) != null;
        } catch (IOException exception) {
            return false;
        }
    }
}
