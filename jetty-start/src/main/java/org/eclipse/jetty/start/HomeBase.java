//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.start;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * File access for <code>${jetty.home}</code>, and optional <code>${jetty.base}</code>, directories
 */
public class HomeBase
{
    private static class AllFilter implements FileFilter
    {
        public static final AllFilter INSTANCE = new AllFilter();

        @Override
        public boolean accept(File pathname)
        {
            return true;
        }
    }

    public static String separators(String path)
    {
        StringBuilder ret = new StringBuilder();
        for (char c : path.toCharArray())
        {
            if ((c == '/') || (c == '\\'))
            {
                ret.append(File.separatorChar);
            }
            else
            {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    private File homeDir;
    private File baseDir;

    public HomeBase()
    {
        String userDir = System.getProperty("user.dir");
        this.homeDir = new File(System.getProperty("jetty.home",userDir));
        String base = System.getProperty("jetty.base");
        if (base != null)
        {
            this.baseDir = new File(base);
        }
    }

    public HomeBase(File homeDir, File baseDir)
    {
        this.homeDir = homeDir;
        this.baseDir = baseDir;
    }
    
    public boolean hasBase()
    {
        return this.baseDir != null;
    }
    
    public void setBaseDir(File dir)
    {
        this.baseDir = dir;
    }

    public File getBaseDir()
    {
        return baseDir;
    }

    public String getBase()
    {
        if (baseDir == null)
        {
            return null;
        }
        return baseDir.getAbsolutePath();
    }

    /**
     * Get a specific file reference.
     * <p>
     * File references go through 3 possibly scenarios.
     * <ol>
     * <li>If exists relative to <code>${jetty.base}</code>, return that reference</li>
     * <li>If exists relative to <code>${jetty.home}</code>, return that reference</li>
     * <li>Otherwise return absolute path reference</li>
     * </ol>
     * 
     * @param path
     *            the path to get.
     * @return the file reference.
     */
    public File getFile(String path)
    {
        String rpath = separators(path);

        // Relative to Base Directory First
        if (baseDir != null)
        {
            File file = new File(baseDir,rpath);
            if (file.exists())
            {
                return file;
            }
        }

        // Then relative to Home Directory
        File file = new File(homeDir,rpath);
        if (file.exists())
        {
            return file;
        }

        // Finally, as an absolute path
        return new File(rpath);
    }
    
    public void setHomeDir(File dir)
    {
        this.homeDir = dir;
    }

    public File getHomeDir()
    {
        return homeDir;
    }

    public String getHome()
    {
        return homeDir.getAbsolutePath();
    }

    /**
     * Get all of the files that are in a specific relative directory.
     * <p>
     * If the same found path exists in both <code>${jetty.base}</code> and <code>${jetty.home}</code>, then the one in <code>${jetty.base}</code> is returned
     * (it overrides the one in ${jetty.home})
     * 
     * @param relPathToDirectory
     *            the relative path to the directory
     * @return the list of files found.
     */
    public List<File> listFiles(String relPathToDirectory)
    {
        return listFiles(relPathToDirectory,AllFilter.INSTANCE);
    }

    /**
     * Get all of the files that are in a specific relative directory, with applied {@link FileFilter}
     * <p>
     * If the same found path exists in both <code>${jetty.base}</code> and <code>${jetty.home}</code>, then the one in <code>${jetty.base}</code> is returned
     * (it overrides the one in ${jetty.home})
     * 
     * @param relPathToDirectory
     *            the relative path to the directory
     * @param filter
     *            the filter to use
     * @return the list of files found.
     */
    public List<File> listFiles(String relPathToDirectory, FileFilter filter)
    {
        Objects.requireNonNull(filter,"FileFilter cannot be null");

        File homePath = new File(homeDir,separators(relPathToDirectory));
        List<File> homeFiles = new ArrayList<>();
        homeFiles.addAll(Arrays.asList(homePath.listFiles(filter)));

        if (baseDir != null)
        {
            // merge
            File basePath = new File(baseDir,separators(relPathToDirectory));
            File baseFiles[] = basePath.listFiles(filter);
            List<File> ret = new ArrayList<>();

            for (File base : baseFiles)
            {
                String relpath = toRelativePath(baseDir,base);
                File home = new File(homeDir,separators(relpath));
                if (home.exists())
                {
                    homeFiles.remove(home);
                }
                ret.add(base);
            }

            // add any remaining home files.
            ret.addAll(homeFiles);

            Collections.sort(ret, new NaturalSort.Files());
            return ret;
        }
        else
        {
            // simple return
            Collections.sort(homeFiles, new NaturalSort.Files());
            return homeFiles;
        }
    }

    private String toRelativePath(File dir, File path)
    {
        return dir.toURI().relativize(path.toURI()).toASCIIString();
    }

    /**
     * Convenience method for <code>toShortForm(file.getCanonicalPath())</code>
     */
    public String toShortForm(File path)
    {
        try
        {
            return toShortForm(path.getCanonicalPath());
        }
        catch (IOException ignore)
        {
            /* ignore */
        }
        return toShortForm(path.getAbsolutePath());
    }

    /**
     * Replace/Shorten arbitrary path with property strings <code>"${jetty.home}"</code> or <code>"${jetty.base}"</code> where appropriate.
     * 
     * @param path
     *            the path to shorten
     * @return the potentially shortened path
     */
    public String toShortForm(String path)
    {
        if (path == null)
        {
            return path;
        }

        String value = homeDir.getAbsolutePath();

        if (path.startsWith(value))
        {
            return "${jetty.home}" + path.substring(value.length());
        }

        if (baseDir != null)
        {
            value = baseDir.getAbsolutePath();
            if (path.startsWith(value))
            {
                return "${jetty.base}" + path.substring(value.length());
            }
        }
        return path;
    }
}