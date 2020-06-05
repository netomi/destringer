/*
 * Copyright (c) 2020 Thomas Neidhart
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.netomi.destringer;

import proguard.classfile.ClassPool;
import proguard.classfile.util.ClassReferenceInitializer;
import proguard.classfile.util.ClassSuperHierarchyInitializer;
import proguard.classfile.util.WarningPrinter;
import proguard.classfile.visitor.ClassPoolFiller;
import proguard.io.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class Destringer {

    private static ClassPool loadLibraryClassPool() throws IOException {
        ClassPool libraryClassPool = new ClassPool();

        File jmodDir = new File(System.getProperty("java.home") + "/jmods");
        if (jmodDir.exists()) {
            File[] jmodFiles = jmodDir.listFiles((dir, name) -> name.endsWith(".jmod"));
            for (File jmodFile : jmodFiles) {
                readJar(jmodFile.getAbsolutePath(), true, libraryClassPool);
            }
        } else {
            readJar(System.getProperty("java.home") + "/jre/lib/rt.jar", true, libraryClassPool);
        }

        return libraryClassPool;
    }

    private static ClassPool readJar(String    jarFileName,
                                     boolean   isLibrary,
                                     ClassPool classPool)
            throws IOException
    {
        DataEntrySource source = new FileSource(new File(jarFileName));

        source.pumpDataEntries(
            new JarReader(isLibrary,
            new ClassFilter(
            new ClassReader(isLibrary, false, false, false, null,
            new ClassPoolFiller(classPool)))));

        return classPool;
    }

    public static void writeJar(ClassPool programClassPool,
                                String    outputJarFileName)
            throws IOException
    {
        JarWriter jarWriter =
            new JarWriter(
            new ZipWriter(
            new FixedFileWriter(
            new File(outputJarFileName))));

        programClassPool.classesAccept(
            new DataEntryClassWriter(jarWriter));

        jarWriter.close();
    }

    public static void initialize(ClassPool programClassPool,
                                  ClassPool libraryClassPool)
    {
        PrintWriter printWriter    = new PrintWriter(System.err);
        WarningPrinter warningPrinter = new WarningPrinter(printWriter);

        // Initialize the class hierarchies.
        libraryClassPool.classesAccept(
            new ClassSuperHierarchyInitializer(programClassPool,
                                               libraryClassPool,
                                               null,
                                               null));

        programClassPool.classesAccept(
            new ClassSuperHierarchyInitializer(programClassPool,
                                               libraryClassPool,
                                               warningPrinter,
                                               null));

        // Initialize the other references from the program classes.
        programClassPool.classesAccept(
            new ClassReferenceInitializer(programClassPool,
                                          libraryClassPool,
                                          warningPrinter,
                                          warningPrinter,
                                          warningPrinter,
                                          null));

        // Flush the warnings.
        printWriter.flush();
    }

    public static void main(String[] args) {
        String inputJarFileName  = args[0];
        String outputJarFileName = args[1];

        File inputJar  = new File(inputJarFileName);

        try
        {
            ClassPool programClassPool = readJar(inputJarFileName, false, new ClassPool());
            // we do not really need the library classes.
            ClassPool libraryClassPool = new ClassPool(); //loadLibraryClassPool();

            //initialize(programClassPool, libraryClassPool);

            programClassPool.accept(new StringDecryptor(inputJar));

            writeJar(programClassPool, outputJarFileName);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
