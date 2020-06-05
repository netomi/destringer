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

import proguard.classfile.Clazz;
import proguard.classfile.Method;
import proguard.classfile.ProgramClass;
import proguard.classfile.attribute.Attribute;
import proguard.classfile.attribute.CodeAttribute;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.constant.Constant;
import proguard.classfile.editor.*;
import proguard.classfile.instruction.Instruction;
import proguard.classfile.instruction.visitor.InstructionCounter;
import proguard.classfile.visitor.AllMethodVisitor;
import proguard.classfile.visitor.ClassVisitor;

/**
 * Thus class removes any protection mechanisms in the decrypt methods.
 * <p>
 * This includes things like:
 * <ul>
 *     <li>expected call traces</li>
 *     <li>expected constant pool size of calling class</li>
 * </ul>
 */
public class ProtectionRemover
    implements ClassVisitor,
               AttributeVisitor {

    private final Constant[]        constants;
    private final Instruction[][][] instructions;

    public ProtectionRemover(String className,
                             String methodName,
                             int    constantPoolSize) {

        InstructionSequenceBuilder ____ = new InstructionSequenceBuilder();

        instructions = new Instruction[][][]
            {
                {
                    ____.invokestatic("sun/misc/SharedSecrets", "getJavaLangAccess", "()Lsun/misc/JavaLangAccess;")
                        .aload(1)
                        .iconst_2()
                        .aaload()
                        .invokevirtual("java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;")
                        .invokestatic("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
                        .invokeinterface("sun/misc/JavaLangAccess", "getConstantPool", "(Ljava/lang/Class;)Lsun/reflect/ConstantPool;")
                        .invokevirtual("sun/reflect/ConstantPool", "getSize", "()I")
                        .invokevirtual("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;").__(),

                    ____.pushInt(constantPoolSize)
                        .invokevirtual("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;").__(),
                },

                {
                    ____.aload(1)
                        .iconst_2()
                        .aaload()
                        .invokevirtual("java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;")
                        .invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;").__(),

                    ____.ldc(className)
                        .invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;").__(),
                },

                {
                    ____.aload(1)
                        .iconst_2()
                        .aaload()
                        .invokevirtual("java/lang/StackTraceElement", "getMethodName", "()Ljava/lang/String;")
                        .invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;").__(),

                    ____.ldc(methodName)
                        .invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;").__(),
                }
            };

        constants = ____.constants();
    }

    @Override
    public void visitAnyClass(Clazz clazz) {}

    @Override
    public void visitProgramClass(ProgramClass programClass) {
        programClass.accept(
            new AllMethodVisitor(
            new AllAttributeVisitor(this)));

        programClass.accept(new ConstantPoolShrinker());
    }

    @Override
    public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}

    @Override
    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute) {
        CodeAttributeEditor codeAttributeEditor = new CodeAttributeEditor();

        clazz.accept(
            new AllMethodVisitor(
            new AllAttributeVisitor(
            new PeepholeEditor(codeAttributeEditor,
            new InstructionSequencesReplacer(constants,
                                             instructions,
                                             null,
                                             codeAttributeEditor,
                                             new InstructionCounter())))));
    }

}
