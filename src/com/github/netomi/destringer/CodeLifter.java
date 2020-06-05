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

import proguard.classfile.ClassConstants;
import proguard.classfile.ProgramClass;
import proguard.classfile.constant.Constant;
import proguard.classfile.editor.ConstantPoolEditor;
import proguard.classfile.editor.MemberAdder;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CodeLifter {

    public static ProgramClass copyClass(ProgramClass originalClass) {
        // Create an empty output class.
        ProgramClass duplicatedClass =
            new ProgramClass(originalClass.u4version,
                             1,
                             new Constant[1],
                             originalClass.u2accessFlags,
                             0,
                             0);

        ConstantPoolEditor constantPoolEditor =
            new ConstantPoolEditor(duplicatedClass);

        duplicatedClass.u2thisClass =
            constantPoolEditor.addClassConstant(originalClass.getName(), null);

        duplicatedClass.u2superClass =
            constantPoolEditor.addClassConstant(ClassConstants.NAME_JAVA_LANG_OBJECT, null);

        // Copy over the class members.
        MemberAdder memberAdder = new MemberAdder(duplicatedClass);

        originalClass.fieldsAccept(memberAdder);
        originalClass.methodsAccept(memberAdder);

        return duplicatedClass;
    }

    public static Class<?> loadClass(String externalClassName, byte[] content, URL inputURL) throws Exception {
        ClassLoader classLoader =
            new ByteClassLoader(new URL[] { inputURL  },
                                CodeLifter.class.getClassLoader(),
                                Collections.singletonMap(externalClassName, content));

        CodeSource codeSource = new CodeSource(inputURL, (CodeSigner[]) null);

        Class<?> loadedClass = Class.forName(externalClassName, true, classLoader);

        // replace code source in protection domain.
        ProtectionDomain domain = loadedClass.getProtectionDomain();

        java.lang.reflect.Field field = domain.getClass().getDeclaredField("codesource");
        field.setAccessible(true);
        field.set(domain, codeSource);

        return loadedClass;
    }

    private static class ByteClassLoader extends URLClassLoader {
        private final Map<String, byte[]> extraClassDefs;

        public ByteClassLoader(URL[] urls, ClassLoader parent, Map<String, byte[]> extraClassDefs) {
            super(urls, parent);
            this.extraClassDefs = new HashMap<>(extraClassDefs);
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            byte[] classBytes = this.extraClassDefs.remove(name);
            if (classBytes != null) {
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            return super.findClass(name);
        }

    }

}
