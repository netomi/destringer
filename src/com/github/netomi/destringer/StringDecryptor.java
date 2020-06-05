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

import proguard.classfile.*;
import proguard.classfile.attribute.Attribute;
import proguard.classfile.attribute.CodeAttribute;
import proguard.classfile.attribute.visitor.AllAttributeVisitor;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.constant.*;
import proguard.classfile.editor.CodeAttributeEditor;
import proguard.classfile.editor.ConstantPoolEditor;
import proguard.classfile.editor.ConstantPoolShrinker;
import proguard.classfile.instruction.*;
import proguard.classfile.instruction.visitor.InstructionVisitor;
import proguard.classfile.io.ProgramClassWriter;
import proguard.classfile.util.ClassUtil;
import proguard.classfile.util.InstructionSequenceMatcher;
import proguard.classfile.visitor.AllMethodVisitor;
import proguard.classfile.visitor.ClassPoolVisitor;
import proguard.classfile.visitor.ClassVisitor;

import java.io.*;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decrypts encrypted strings added by stringer v9+.
 */
public class   StringDecryptor
    implements ClassPoolVisitor,
               ClassVisitor,
               AttributeVisitor,
               InstructionVisitor {

    private static final String DECRYPT_METHOD_TYPE = "(Ljava/lang/Object;)Ljava/lang/String;";

    private static final int X = InstructionSequenceMatcher.X;
    private static final int Y = InstructionSequenceMatcher.Y;
    private static final int Z = InstructionSequenceMatcher.Z;

    private final Constant[] CONSTANTS = new Constant[]
        {
            new MethodrefConstant(1, 2, null, null),
            new ClassConstant(X, null),
            new NameAndTypeConstant(Y, 3),
            new Utf8Constant(DECRYPT_METHOD_TYPE),
        };

    private final Instruction[] INSTRUCTIONS = new Instruction[]
        {
            new ConstantInstruction(Instruction.OP_LDC, Z),
            new ConstantInstruction(Instruction.OP_INVOKESTATIC, 0),
        };

    private final Instruction[] INSTRUCTIONS2 = new Instruction[]
        {
            new ConstantInstruction(Instruction.OP_LDC_W, Z),
            new ConstantInstruction(Instruction.OP_INVOKESTATIC, 0),
        };

    private final InstructionSequenceMatcher matcher1 =
        new InstructionSequenceMatcher(CONSTANTS, INSTRUCTIONS);

    private final InstructionSequenceMatcher matcher2 =
        new InstructionSequenceMatcher(CONSTANTS, INSTRUCTIONS2);

    private final File                inputJar;
    private       AtomicInteger       decryptedStrings;
    private       ClassPool           programClassPool;

    private final CodeAttributeEditor codeAttributeEditor = new CodeAttributeEditor(true, true);
    private       ConstantPoolEditor  constantPoolEditor;
    private       int                 constantPoolLength;

    public StringDecryptor(File inputJar) {
        this.inputJar = inputJar;
    }

    @Override
    public void visitClassPool(ClassPool classPool) {
        decryptedStrings = new AtomicInteger(0);
        programClassPool = classPool;

        classPool.classesAccept(this);

        System.out.println("decrypted " + decryptedStrings.get() + " strings.");
    }

    @Override
    public void visitAnyClass(Clazz clazz) {}

    @Override
    public void visitProgramClass(ProgramClass programClass) {
        constantPoolEditor = new ConstantPoolEditor(programClass);

        constantPoolLength = programClass.constantPool.length;

        programClass.accept(
            new AllMethodVisitor(
            new AllAttributeVisitor(this)));

        programClass.accept(new ConstantPoolShrinker());
    }

    @Override
    public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}

    @Override
    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute) {
        codeAttributeEditor.reset(codeAttribute.u4codeLength);

        codeAttribute.instructionsAccept(clazz, method, this);

        if (codeAttributeEditor.isModified())
        {
            codeAttributeEditor.visitCodeAttribute(clazz, method, codeAttribute);
        }

        // Update the nested attributes.
        codeAttribute.attributesAccept(clazz, method, this);
    }

    @Override
    public void visitAnyInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, Instruction instruction) {

        instruction.accept(clazz, method, codeAttribute, offset, matcher1);
        instruction.accept(clazz, method, codeAttribute, offset, matcher2);

        // did we find a match?
        if (matcher1.isMatching() || matcher2.isMatching()) {
            InstructionSequenceMatcher matcher = matcher1.isMatching() ? matcher1 : matcher2;

            int classIndex = matcher.matchedConstantIndex(X);
            int nameIndex = matcher.matchedConstantIndex(Y);
            int stringIndex = matcher.matchedConstantIndex(Z);

            String className = clazz.getString(classIndex);
            String externalClassName = ClassUtil.externalClassName(className);
            String methodName = clazz.getString(nameIndex);
            String argument = clazz.getStringString(stringIndex);

            System.out.print(String.format("calling method %s.%s(%s)", className, methodName, argument));

            ProgramClass originalClass = (ProgramClass) programClassPool.getClass(className);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 DataOutputStream os = new DataOutputStream(baos)) {

                // clone the original class.
                ProgramClass modifiedClass = CodeLifter.copyClass(originalClass);

                // and modify its contents.
                modifiedClass.accept(
                    new ProtectionRemover(ClassUtil.externalClassName(clazz.getName()),
                                          method.getName(clazz),
                                          constantPoolLength));

                modifiedClass.accept(new ProgramClassWriter(os));
                os.flush();

                byte[] bytes = baos.toByteArray();

                try {
                    URL input = inputJar.toURI().toURL();

                    Class<?> decryptorClass = CodeLifter.loadClass(externalClassName, bytes, input);

                    java.lang.reflect.Method m = decryptorClass.getDeclaredMethod(methodName, new Class[]{Object.class});
                    m.setAccessible(true);
                    Object result = m.invoke(null, argument);

                    System.out.println(" = " + result);

                    int instructionOffset = matcher.matchedInstructionOffset(matcher.instructionCount() - 2);

                    int constantIndex = constantPoolEditor.addStringConstant(result.toString(), null, null);

                    codeAttributeEditor.replaceInstruction(instructionOffset, new ConstantInstruction(Instruction.OP_LDC, constantIndex));
                    codeAttributeEditor.deleteInstruction(matcher.matchedInstructionOffset(matcher.instructionCount() - 1));

                    decryptedStrings.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            matcher.reset();
        }
    }

}
