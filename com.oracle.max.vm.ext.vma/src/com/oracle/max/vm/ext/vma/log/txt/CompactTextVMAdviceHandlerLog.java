/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.max.vm.ext.vma.log.txt;

import java.util.*;

import com.sun.max.program.*;

/**
 * Defines a compact textual format that uses short forms for class, field and thread names.
 * In addition, a repeated id (but not when used as a value) is, by default, passed as
 * {@value TextVMAdviceHandlerLog#REPEAT_ID} and the
 * underlying format knows how to interpret that. This can be suppressed by setting the
 * system property {@value NO_REPEATS_PROPERTY). Repeated ids are only generated
 * for records created by the same originating thread.
 *
 * <li>{@link #classDefinitionTracking}: G shortClass class clid
 * <li>{@link #classDefinitionFieldTracking}: F shortField field
 * <li>{@link #threadDefinitionTracking}: T shortThread thread
 * <li>{@link #methodDefinitionTracking}: M shortMethod method
 * </ul>
 *
 * By default instances of the short forms are represented simply by their
 * integer value. This minimises log size but makes the log very hard to read by a human.
 * A single character prefix (C, F, T) can be generated by setting the
 * system property {@value PREFIX_PROPERTY}.
 *
 * We do not know the multi-threaded behavior of callers. Many threads may be synchronously
 * logging or one or more threads may be asynchronously logging records generated by
 * other threads.
 *
 * The short form maps are (necessarily) global to all threads and must be thread-safe.
 * The repeated id handling uses a thread-local as it operates across multiple
 * methods and is thread-specific.
 */

public abstract class CompactTextVMAdviceHandlerLog extends TextVMAdviceHandlerLog {

    protected final TextVMAdviceHandlerLog del;

    private static Map<String, LastId> repeatedIds;
    private static int classInt;
    private static int fieldInt;
    private static int threadInt;
    private static int methodInt;
    private static final String NO_REPEATS_PROPERTY = "max.vma.norepeatids";
    private static final String PREFIX_PROPERTY = "max.vma.shortprefix";
    private static boolean doRepeats;
    private static boolean doPrefix;

    /**
     * Denotes a class name and classloader id.
     * Used as the value in the short forms map for a class.
     */
    public static class ClassNameId {
        public String name;
        public long clId;
        public ClassNameId(String name, long clId) {
            this.name = name;
            this.clId = clId;
        }

        @Override
        public int hashCode() {
            return (int) (name.hashCode() ^ clId);
        }

        @Override
        public boolean equals(Object other) {
            ClassNameId otherClassName = (ClassNameId) other;
            return name.equals(otherClassName.name) && clId == otherClassName.clId;
        }
    }

    /**
     * Denotes a qualified name. Used as the value in the short form maps for fields and methods.
     */
    public static class QualName {
        public ClassNameId className;
        public String name;
        public QualName(String className, long clId, String name) {
            this.className = new ClassNameId(className, clId);
            this.name = name;
        }

        public QualName(ClassNameId className, String name) {
            this.className = className;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return className.hashCode() ^ name.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            QualName otherQualName = (QualName) other;
            return className.equals(otherQualName.className) && name.equals(otherQualName.name);
        }
    }

    /**
     * Per-thread value that is used to check the map for existence.
     */
    private class ClassNameIdTL extends ThreadLocal<ClassNameId> {
        @Override
        public ClassNameId initialValue() {
            return new ClassNameId(null, 0);
        }
    }

    private final ClassNameIdTL classNameIdTL = new ClassNameIdTL();

    /**
     * Per-thread value that is used to check the map for existence.
     */
    private class QualNameTL extends ThreadLocal<QualName> {
        @Override
        public QualName initialValue() {
            return new QualName(classNameIdTL.get(), null);
        }
    }

    private final QualNameTL qualNameTL = new QualNameTL();

    public static enum ShortForm {
        C("C"),
        F("F"),
        T("T"),
        M("M");

        public final String code;

        private Map<Object, String> shortForms = new HashMap<Object, String>();
        private int nextId;

        String createShortForm(CompactTextVMAdviceHandlerLog handler, Object key) {
            synchronized (shortForms) {
                String shortForm = shortForms.get(key);
                String classShortForm = null;
                if (shortForm == null) {
                    Object newKey = key;
                    shortForm = doPrefix ? (code + nextId) : Integer.toString(nextId);
                    nextId++;
                    switch (this) {
                        case T:
                            break;
                        case C:
                            ClassNameId tlKey = (ClassNameId) key;
                            newKey = new ClassNameId(tlKey.name, tlKey.clId);
                            break;
                        case F:
                        case M:
                            QualName tlQualName = (QualName) key;
                            newKey = new QualName(new ClassNameId(tlQualName.className.name, tlQualName.className.clId), tlQualName.name);
                            classShortForm = ShortForm.C.createShortForm(handler, tlQualName.className);
                            break;
                    }
                    shortForms.put(newKey, shortForm);
                    handler.defineShortForm(this, key, shortForm, classShortForm);
                }
                return shortForm;
            }

        }

        ShortForm(String code) {
            this.code = code;
        }
    }

    static class LastId {
        long id = REPEAT_ID_VALUE;
    }

    protected CompactTextVMAdviceHandlerLog(TextVMAdviceHandlerLog del) {
        super();
        this.del = del;
    }

    /**
     * Check if this {@code objId} is the same as the previous one.
     * Note that all traces that start with an {@code objId} must call this method!
     * @param id
     * @param threadName
     * @return {@link TextVMAdviceHandlerLog#REPEAT_ID_VALUE} for a match, {@code id} otherwise
     */
    private static long checkRepeatId(long id, String threadName) {
        LastId lastId;
        if (doRepeats) {
            lastId = getLastId(threadName);
            if (lastId.id == id) {
                return REPEAT_ID_VALUE;
            } else {
                lastId.id = id;
            }
        }
        return id;
    }

    private static LastId getLastId(String threadName) {
        synchronized (repeatedIds) {
            LastId lastId = repeatedIds.get(threadName);
            if (lastId == null) {
                lastId = new LastId();
                repeatedIds.put(threadName, lastId);
            }
            return lastId;
        }
    }

    @Override
    public boolean initializeLog() {
        repeatedIds = new HashMap<String, LastId>();
        doRepeats = System.getProperty(NO_REPEATS_PROPERTY) == null;
        doPrefix = System.getProperty(PREFIX_PROPERTY) != null;
        return del.initializeLog();
    }

    @Override
    public void finalizeLog() {
        del.finalizeLog();
    }

    @Override
    public void removal(long id) {
        del.removal(id);
    }

    @Override
    public void unseenObject(String threadName, long objId, String className, long clId) {
        del.unseenObject(getThreadShortForm(threadName), checkRepeatId(objId, threadName), getClassShortForm(className, clId), clId);
    }

    @Override
    public void resetTime() {
        del.resetTime();
    }


    protected String getClassShortForm(String className, long clId) {
        ClassNameId classNameId = classNameIdTL.get();
        classNameId.name = className;
        classNameId.clId = clId;
        return ShortForm.C.createShortForm(this, classNameId);
    }

    protected String getFieldShortForm(String className, long clId, String fieldName) {
        QualName qualNameId = qualNameTL.get();
        qualNameId.className.name = className;
        qualNameId.className.clId = clId;
        qualNameId.name = fieldName;
        return ShortForm.F.createShortForm(this, qualNameId);
    }

    protected String getThreadShortForm(String threadName) {
        return ShortForm.T.createShortForm(this, threadName);
    }

    protected String getMethodShortForm(String className, long clId, String fieldName) {
        QualName qualNameId = qualNameTL.get();
        qualNameId.className.name = className;
        qualNameId.className.clId = clId;
        qualNameId.name = fieldName;
        return ShortForm.M.createShortForm(this, qualNameId);
    }

    /**
     * Define a short form of {@code key}.
     *
     * @param type
     * @param key
     * @param shortForm
     * @param classShortForm only for fields and methods
     */
    public abstract void defineShortForm(ShortForm type, Object key, String shortForm, String classShortForm);

// START GENERATED CODE
// EDIT AND RUN CompactTextVMAdviceHandlerLogGenerator.main() TO MODIFY

    @Override
    public void adviseAfterNew(String arg1, long arg2, String arg3, long arg4) {
        del.adviseAfterNew(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4);
    }

    @Override
    public void adviseAfterNewArray(String arg1, long arg2, String arg3, long arg4, int arg5) {
        del.adviseAfterNewArray(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, arg5);
    }

    @Override
    public void adviseAfterMultiNewArray(String arg1, long arg2, String arg3, long arg4, int arg5) {
        ProgramError.unexpected("adviseAfterMultiNewArray");
    }

    @Override
    public void adviseBeforeConstLoad(String arg1, float arg2) {
        del.adviseBeforeConstLoad(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeConstLoad(String arg1, long arg2) {
        del.adviseBeforeConstLoad(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeConstLoad(String arg1, double arg2) {
        del.adviseBeforeConstLoad(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeLoad(String arg1, int arg2) {
        del.adviseBeforeLoad(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeArrayLoad(String arg1, long arg2, int arg3) {
        del.adviseBeforeArrayLoad(getThreadShortForm(arg1),  checkRepeatId(arg2, arg1), arg3);
    }

    @Override
    public void adviseBeforeStore(String arg1, int arg2, long arg3) {
        del.adviseBeforeStore(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeStore(String arg1, int arg2, float arg3) {
        del.adviseBeforeStore(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeStore(String arg1, int arg2, double arg3) {
        del.adviseBeforeStore(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeArrayStore(String arg1, long arg2, int arg3, double arg4) {
        del.adviseBeforeArrayStore(getThreadShortForm(arg1),  checkRepeatId(arg2, arg1), arg3, arg4);
    }

    @Override
    public void adviseBeforeArrayStore(String arg1, long arg2, int arg3, long arg4) {
        del.adviseBeforeArrayStore(getThreadShortForm(arg1),  checkRepeatId(arg2, arg1), arg3, arg4);
    }

    @Override
    public void adviseBeforeArrayStore(String arg1, long arg2, int arg3, float arg4) {
        del.adviseBeforeArrayStore(getThreadShortForm(arg1),  checkRepeatId(arg2, arg1), arg3, arg4);
    }

    @Override
    public void adviseBeforeStackAdjust(String arg1, int arg2) {
        del.adviseBeforeStackAdjust(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeOperation(String arg1, int arg2, double arg3, double arg4) {
        del.adviseBeforeOperation(getThreadShortForm(arg1), arg2, arg3, arg4);
    }

    @Override
    public void adviseBeforeOperation(String arg1, int arg2, long arg3, long arg4) {
        del.adviseBeforeOperation(getThreadShortForm(arg1), arg2, arg3, arg4);
    }

    @Override
    public void adviseBeforeOperation(String arg1, int arg2, float arg3, float arg4) {
        del.adviseBeforeOperation(getThreadShortForm(arg1), arg2, arg3, arg4);
    }

    @Override
    public void adviseBeforeConversion(String arg1, int arg2, long arg3) {
        del.adviseBeforeConversion(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeConversion(String arg1, int arg2, double arg3) {
        del.adviseBeforeConversion(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeConversion(String arg1, int arg2, float arg3) {
        del.adviseBeforeConversion(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeIf(String arg1, int arg2, int arg3, int arg4) {
        del.adviseBeforeIf(getThreadShortForm(arg1), arg2, arg3, arg4);
    }

    @Override
    public void adviseBeforeBytecode(String arg1, int arg2) {
        del.adviseBeforeBytecode(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeReturn(String arg1) {
        del.adviseBeforeReturn(getThreadShortForm(arg1));
    }

    @Override
    public void adviseBeforeReturn(String arg1, long arg2) {
        del.adviseBeforeReturn(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeReturn(String arg1, float arg2) {
        del.adviseBeforeReturn(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeReturn(String arg1, double arg2) {
        del.adviseBeforeReturn(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeGetStatic(String arg1, String arg2, long arg3, String arg4) {
        del.adviseBeforeGetStatic(getThreadShortForm(arg1), getClassShortForm(arg2, arg3), arg3, getFieldShortForm(arg2, arg3, arg4));
    }

    @Override
    public void adviseBeforePutStatic(String arg1, String arg2, long arg3, String arg4, float arg5) {
        del.adviseBeforePutStatic(getThreadShortForm(arg1), getClassShortForm(arg2, arg3), arg3, getFieldShortForm(arg2, arg3, arg4), arg5);
    }

    @Override
    public void adviseBeforePutStatic(String arg1, String arg2, long arg3, String arg4, double arg5) {
        del.adviseBeforePutStatic(getThreadShortForm(arg1), getClassShortForm(arg2, arg3), arg3, getFieldShortForm(arg2, arg3, arg4), arg5);
    }

    @Override
    public void adviseBeforePutStatic(String arg1, String arg2, long arg3, String arg4, long arg5) {
        del.adviseBeforePutStatic(getThreadShortForm(arg1), getClassShortForm(arg2, arg3), arg3, getFieldShortForm(arg2, arg3, arg4), arg5);
    }

    @Override
    public void adviseBeforeGetField(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseBeforeGetField(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getFieldShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseBeforePutField(String arg1, long arg2, String arg3, long arg4, String arg5, float arg6) {
        del.adviseBeforePutField(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getFieldShortForm(arg3, arg4, arg5), arg6);
    }

    @Override
    public void adviseBeforePutField(String arg1, long arg2, String arg3, long arg4, String arg5, long arg6) {
        del.adviseBeforePutField(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getFieldShortForm(arg3, arg4, arg5), arg6);
    }

    @Override
    public void adviseBeforePutField(String arg1, long arg2, String arg3, long arg4, String arg5, double arg6) {
        del.adviseBeforePutField(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getFieldShortForm(arg3, arg4, arg5), arg6);
    }

    @Override
    public void adviseBeforeInvokeVirtual(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseBeforeInvokeVirtual(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseBeforeInvokeSpecial(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseBeforeInvokeSpecial(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseBeforeInvokeStatic(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseBeforeInvokeStatic(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseBeforeInvokeInterface(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseBeforeInvokeInterface(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseBeforeArrayLength(String arg1, long arg2, int arg3) {
        del.adviseBeforeArrayLength(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeThrow(String arg1, long arg2) {
        del.adviseBeforeThrow(getThreadShortForm(arg1), checkRepeatId(arg2, arg1));
    }

    @Override
    public void adviseBeforeCheckCast(String arg1, long arg2, String arg3, long arg4) {
        del.adviseBeforeCheckCast(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4);
    }

    @Override
    public void adviseBeforeInstanceOf(String arg1, long arg2, String arg3, long arg4) {
        del.adviseBeforeInstanceOf(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4);
    }

    @Override
    public void adviseBeforeMonitorEnter(String arg1, long arg2) {
        del.adviseBeforeMonitorEnter(getThreadShortForm(arg1), checkRepeatId(arg2, arg1));
    }

    @Override
    public void adviseBeforeMonitorExit(String arg1, long arg2) {
        del.adviseBeforeMonitorExit(getThreadShortForm(arg1), checkRepeatId(arg2, arg1));
    }

    @Override
    public void adviseAfterInvokeVirtual(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseAfterInvokeVirtual(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseAfterInvokeSpecial(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseAfterInvokeSpecial(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseAfterInvokeStatic(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseAfterInvokeStatic(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseAfterInvokeInterface(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseAfterInvokeInterface(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseAfterMethodEntry(String arg1, long arg2, String arg3, long arg4, String arg5) {
        del.adviseAfterMethodEntry(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getMethodShortForm(arg3, arg4, arg5));
    }

    @Override
    public void adviseBeforeGC(String arg1) {
        del.adviseBeforeGC(getThreadShortForm(arg1));
    }

    @Override
    public void adviseAfterGC(String arg1) {
        del.adviseAfterGC(getThreadShortForm(arg1));
    }

    @Override
    public void adviseBeforeThreadStarting(String arg1) {
        del.adviseBeforeThreadStarting(getThreadShortForm(arg1));
    }

    @Override
    public void adviseBeforeThreadTerminating(String arg1) {
        del.adviseBeforeThreadTerminating(getThreadShortForm(arg1));
    }

    @Override
    public void adviseBeforeConstLoadObject(String arg1, long arg2) {
        del.adviseBeforeConstLoadObject(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforeStoreObject(String arg1, int arg2, long arg3) {
        del.adviseBeforeStoreObject(getThreadShortForm(arg1), arg2, arg3);
    }

    @Override
    public void adviseBeforeArrayStoreObject(String arg1, long arg2, int arg3, long arg4) {
        del.adviseBeforeArrayStoreObject(getThreadShortForm(arg1),  checkRepeatId(arg2, arg1), arg3, arg4);
    }

    @Override
    public void adviseBeforeIfObject(String arg1, int arg2, long arg3, long arg4) {
        del.adviseBeforeIfObject(getThreadShortForm(arg1), arg2, arg3, arg4);
    }

    @Override
    public void adviseBeforeReturnObject(String arg1, long arg2) {
        del.adviseBeforeReturnObject(getThreadShortForm(arg1), arg2);
    }

    @Override
    public void adviseBeforePutStaticObject(String arg1, String arg2, long arg3, String arg4, long arg5) {
        del.adviseBeforePutStaticObject(getThreadShortForm(arg1), getClassShortForm(arg2, arg3), arg3, getFieldShortForm(arg2, arg3, arg4), arg5);
    }

    @Override
    public void adviseBeforePutFieldObject(String arg1, long arg2, String arg3, long arg4, String arg5, long arg6) {
        del.adviseBeforePutFieldObject(getThreadShortForm(arg1), checkRepeatId(arg2, arg1), getClassShortForm(arg3, arg4), arg4, getFieldShortForm(arg3, arg4, arg5), arg6);
    }

// END GENERATED CODE

}
