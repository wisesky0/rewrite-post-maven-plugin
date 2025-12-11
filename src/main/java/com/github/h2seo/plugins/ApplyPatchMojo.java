package com.github.h2seo.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Maven plugin goal to apply patch files from a specified directory.
 */
@Mojo(name = "patch")
public class ApplyPatchMojo extends AbstractMojo {

    /**
     * Directory containing patch files.
     */
    @Parameter(property = "rewrite-post.patchDirectory", defaultValue = "${project.basedir}/patches")
    private File patchDirectory;

    /**
     * Git root directory where patches will be applied (defaults to project base directory).
     */
    @Parameter(property = "rewrite-post.gitRootDirectory", defaultValue = "${project.basedir}")
    private File gitRootDirectory;

    /**
     * Fail on error if set to true.
     */
    @Parameter(property = "rewrite-post.failOnError", defaultValue = "true")
    private boolean failOnError;

    /**
     * Patch file extensions to process (comma-separated).
     */
    @Parameter(property = "rewrite-post.extensions", defaultValue = "patch,diff")
    private String extensions;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (patchDirectory == null || !patchDirectory.exists()) {
            getLog().warn("Patch directory does not exist: " + patchDirectory);
            return;
        }

        if (!patchDirectory.isDirectory()) {
            throw new MojoExecutionException("Patch directory is not a directory: " + patchDirectory);
        }

        getLog().info("Applying patches from: " + patchDirectory.getAbsolutePath());
        getLog().info("Git root directory: " + gitRootDirectory.getAbsolutePath());

        List<File> patchFiles = findPatchFiles(patchDirectory);
        
        if (patchFiles.isEmpty()) {
            getLog().info("No patch files found in: " + patchDirectory.getAbsolutePath());
            return;
        }

        getLog().info("Found " + patchFiles.size() + " patch file(s)");

        int successCount = 0;
        int failureCount = 0;

        for (File patchFile : patchFiles) {
            try {
                getLog().info("Applying patch: " + patchFile.getName());
                applyPatch(patchFile);
                successCount++;
                getLog().info("Successfully applied: " + patchFile.getName());
            } catch (Exception e) {
                failureCount++;
                String message = "Failed to apply patch: " + patchFile.getName() + " - " + e.getMessage();
                if (failOnError) {
                    throw new MojoExecutionException(message, e);
                } else {
                    getLog().warn(message);
                }
            }
        }

        getLog().info("Patch application completed. Success: " + successCount + ", Failed: " + failureCount);
    }

    // Package-private for testing
    List<File> findPatchFiles(File directory) {
        List<File> patchFiles = new ArrayList<>();
        String[] extensionsArray = extensions.split(",");
        
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                 .map(Path::toFile)
                 .filter(file -> {
                     String fileName = file.getName().toLowerCase();
                     for (String ext : extensionsArray) {
                         if (fileName.endsWith("." + ext.trim())) {
                             return true;
                         }
                     }
                     return false;
                 })
                 .forEach(patchFiles::add);
        } catch (IOException e) {
            getLog().warn("Error scanning for patch files: " + e.getMessage());
        }

        return patchFiles;
    }

    private void applyPatch(File patchFile) throws MojoExecutionException, IOException {
        try {
            // Initialize Git repository in git root directory
            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            Repository repository = repositoryBuilder
                    .setGitDir(new File(gitRootDirectory, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            
            try (Git git = new Git(repository);
                 InputStream patchStream = new FileInputStream(patchFile)) {
                
                // Apply patch using JGit
                org.eclipse.jgit.api.ApplyCommand applyCommand = git.apply();
                applyCommand.setPatch(patchStream);
                
                try {
                    applyCommand.call();
                    getLog().info("Successfully applied patch: " + patchFile.getName());
                } catch (GitAPIException e) {
                    throw new MojoExecutionException("Failed to apply patch using JGit: " + e.getMessage(), e);
                }
            } finally {
                repository.close();
            }
        } catch (IOException e) {
            // If not a git repository, initialize one temporarily
            getLog().debug("Git root directory is not a git repository, initializing temporary repository");
            applyPatchInTempRepository(patchFile);
        }
    }
    
    private void applyPatchInTempRepository(File patchFile) throws MojoExecutionException, IOException {
        File tempGitDir = new File(gitRootDirectory, ".git");
        boolean wasGitRepo = tempGitDir.exists();
        
        try {
            // Initialize git repository if it doesn't exist
            if (!wasGitRepo) {
                try (Git git = Git.init().setDirectory(gitRootDirectory).call()) {
                    // Add all existing files
                    git.add().addFilepattern(".").call();
                    git.commit().setMessage("Initial commit for patch application").setAllowEmpty(true).call();
                } catch (GitAPIException e) {
                    throw new MojoExecutionException("Failed to initialize git repository: " + e.getMessage(), e);
                }
            }
            
            // Apply patch
            try (Git git = Git.open(gitRootDirectory);
                 InputStream patchStream = new FileInputStream(patchFile)) {
                
                org.eclipse.jgit.api.ApplyCommand applyCommand = git.apply();
                applyCommand.setPatch(patchStream);
                
                try {
                    applyCommand.call();
                    getLog().info("Successfully applied patch: " + patchFile.getName());
                } catch (GitAPIException e) {
                    throw new MojoExecutionException("Failed to apply patch using JGit: " + e.getMessage(), e);
                }
            }
        } finally {
            // Clean up temporary git repository if we created it
            if (!wasGitRepo && tempGitDir.exists()) {
                deleteDirectory(tempGitDir);
            }
        }
    }
    
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}

