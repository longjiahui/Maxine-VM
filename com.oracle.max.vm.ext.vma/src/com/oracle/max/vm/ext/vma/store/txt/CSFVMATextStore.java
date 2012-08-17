/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.vm.ext.vma.store.txt;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.sun.max.vm.thread.*;

/**
 * Defines a compact textual format that uses short forms for class, field and thread names.
 * In addition, a repeated id (but not when used as a value) is, by default, passed as
 * {@link CVMATextStore.Key#REPEAT_ID} and the
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
 * integer value. This minimizes log size but makes the log very hard to read by a human.
 * A single character prefix (C, F, T) can be generated by setting the
 * system property {@link PREFIX_PROPERTY}.
 *
 * We do not know the multi-threaded behavior of callers. Many threads may be synchronously
 * logging or one or more threads may be asynchronously logging records generated by
 * other threads.
 *
 * The short form maps are (necessarily) global to all threads and must be thread-safe.
 * The values used to represent short forms are immutable when stored in the map.
 * However, the class definitions do not define the fields as {@code final} because
 * mutable, thread-local, instances are used to do the initial lookup in the map
 * to avoid unnecessary allocation.
 *
 * The class implements all the {@link VMATextStore} methods and, after handling the compression
 * based on the arguments, invokes the same method with the possibly transformed arguments,
 * on a {#link {@link #del delegate instance}. A concrete subclass must implement the
 * {@link #defineShortForm(ShortForm, Object, String, String)} method, which is called
 * whenever a new short form is created.
 */

public abstract class CSFVMATextStore extends CVMATextStore {

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
    private static class ClassNameIdTL extends ThreadLocal<ClassNameId> {
        @Override
        public ClassNameId initialValue() {
            return new ClassNameId(null, 0);
        }
    }

    private static final ClassNameIdTL classNameIdTL = new ClassNameIdTL();

    /**
     * Per-thread value that is used to check the map for existence.
     */
    private static class QualNameTL extends ThreadLocal<QualName> {
        @Override
        public QualName initialValue() {
            return new QualName(classNameIdTL.get(), null);
        }
    }

    private static final QualNameTL qualNameTL = new QualNameTL();

    public static enum ShortForm {
        C("C"),
        F("F"),
        T("T"),
        M("M");

        public final String code;

        private ConcurrentMap<Object, String> shortForms = new ConcurrentHashMap<Object, String>();
        private AtomicInteger nextIdA = new AtomicInteger();

        String createShortForm(CSFVMATextStore handler, Object key) {
            VmThread vmThread = null;
            if (this == T) {
                if (key instanceof VmThread) {
                    vmThread = (VmThread) key;
                    key = vmThread.getName();
                }
            }
            String shortForm = shortForms.get(key);
            String classShortForm = null;
            if (shortForm == null) {
                Object newKey = key;
                int nextId = nextIdA.incrementAndGet();
                shortForm = doPrefix ? (code + nextId) : Integer.toString(nextId);
                switch (this) {
                    case T:
                        if (vmThread != null) {
                            key = vmThread;
                        }
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
                String winner = shortForms.putIfAbsent(newKey, shortForm);
                // Another thread may have beaten us to it.
                if (winner != null) {
                    shortForm = winner;
                } else {
                    handler.defineShortForm(this, key, shortForm, classShortForm);
                }
            }
            return shortForm;
        }

        ShortForm(String code) {
            this.code = code;
        }
    }

    /**
     * Mutable "long" for recording last object id used by a thread.
     */
    private static class LastId {
        long id = REPEAT_ID_VALUE;
    }

    /**
     * Maintains the last id for the set of threads in the trace.
     */
    private static ConcurrentMap<String, LastId> repeatedIds;

    /**
     * Check if this {@code objId} is the same as the previous one.
     * Note that all traces that start with an {@code objId} must call this method!
     * @param id
     * @param threadName
     * @return {@link TextVMAdviceHandlerLog#REPEAT_ID_VALUE} for a match, {@code id} otherwise
     */
    protected static long checkRepeatId(long id, String threadName) {
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
        LastId lastId = repeatedIds.get(threadName);
        if (lastId == null) {
            lastId = new LastId();
            LastId winner = repeatedIds.putIfAbsent(threadName, lastId);
            // Another thread may have beaten us to it.
            if (winner != null) {
                lastId = winner;
            }
        }
        return lastId;
    }

    protected void initStaticState(boolean perThread) {
        repeatedIds = new ConcurrentHashMap<String, LastId>();
        doRepeats = System.getProperty(NO_REPEATS_PROPERTY) == null;
        doPrefix = System.getProperty(PREFIX_PROPERTY) != null;
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

    protected String getThreadShortForm(VmThread vmThread) {
        return ShortForm.T.createShortForm(this, vmThread);
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
    protected abstract void defineShortForm(ShortForm type, Object key, String shortForm, String classShortForm);


}
