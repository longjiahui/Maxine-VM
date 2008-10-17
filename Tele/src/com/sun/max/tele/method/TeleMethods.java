/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.tele.method;

import java.lang.reflect.*;

import com.sun.max.annotate.*;
import com.sun.max.io.*;
import com.sun.max.program.*;
import com.sun.max.tele.*;
import com.sun.max.tele.field.*;
import com.sun.max.tele.field.TeleFields.*;
import com.sun.max.vm.code.*;
import com.sun.max.vm.tele.*;
import com.sun.max.vm.type.*;

/**
 * Centralized collection of the {@link TeleMethodAccess}s.
 * <p>
 * The {@link INSPECTED} annotation is employed to denote methods that will be accessed remotely.
 * A field of the appropriate {@link TeleMethodAccess} subtype is generated into this file
 * by executing the {@link #main(String[])} method in this class (ensuring that the VM
 * class path contains all the {@code com.sun.max} classes).
 *
 * @author Bernd Mathiske
 * @author Doug Simon
 */
public class TeleMethods extends TeleVMHolder {

    public TeleMethods(TeleVM teleVM) {
        super(teleVM);
        // Uncomment to enable verifying that the generated content in this class is up to date when running the inspector
        // updateSource(true);
    }

    // Checkstyle: stop field name check

    // START GENERATED CONTENT
    public final TeleStaticMethodAccess Code_codePointerToRuntimeStub = new TeleStaticMethodAccess(teleVM(), Code.class, "codePointerToRuntimeStub", SignatureDescriptor.create("(Lcom/sun/max/unsafe/Address;)Lcom/sun/max/vm/runtime/RuntimeStub;"));
    public final TeleStaticMethodAccess Code_codePointerToTargetMethod = new TeleStaticMethodAccess(teleVM(), Code.class, "codePointerToTargetMethod", SignatureDescriptor.create("(Lcom/sun/max/unsafe/Address;)Lcom/sun/max/vm/compiler/target/TargetMethod;"));
    public final TeleStaticMethodAccess Code_methodKeyToTargetMethods = new TeleStaticMethodAccess(teleVM(), Code.class, "methodKeyToTargetMethods", SignatureDescriptor.create("(Lcom/sun/max/vm/actor/member/MethodKey;)[Lcom/sun/max/vm/compiler/target/TargetMethod;"));
    public final TeleStaticMethodAccess TargetBreakpoint_findOriginalCode = new TeleStaticMethodAccess(teleVM(), TargetBreakpoint.class, "findOriginalCode", SignatureDescriptor.create("(J)[B"));
    // END GENERATED CONTENT

    // Checkstyle: resume field name check

    public static void main(String[] args) {
        Trace.begin(1, "TeleMethods updating GENERATED CONTENT");
        Trace.on(1);
        Trace.end(1, "TeleMethods updating GENERATED CONTENT");
        updateSource(false);
    }

    private static void updateSource(boolean inInspector) {
        final InspectedMemberReifier<Method> methodReifier = new InspectedMemberReifier<Method>() {
            @Override
            public void reify(Method method, IndentWriter writer) {
                final Class c = method.getDeclaringClass();
                final boolean isStatic = Modifier.isStatic(method.getModifiers());
                final String holder = c.getName().substring(c.getPackage().getName().length() + 1);
                final String name = method.getName();
                final SignatureDescriptor signature = SignatureDescriptor.fromJava(method);
                final String inspectorFieldName = holder + (name.charAt(0) == '_' ? name : '_' + name);
                final String inspectorFieldType = "Tele" + (isStatic ? "Static" : "Virtual") + "MethodAccess";
                writer.println("public final " + inspectorFieldType + " " + inspectorFieldName + " = new " + inspectorFieldType + "(teleVM(), " +
                                holder.replace('$', '.') + ".class, \"" + name + "\", SignatureDescriptor.create(\"" + signature + "\"));");
            }
        };
        TeleFields.updateSource(TeleMethods.class, Method.class, methodReifier, inInspector);
    }
}
