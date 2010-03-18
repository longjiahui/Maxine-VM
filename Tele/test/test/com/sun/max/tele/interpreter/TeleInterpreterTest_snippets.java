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
package test.com.sun.max.tele.interpreter;

import junit.framework.*;
import test.com.sun.max.vm.cps.*;

/**
 * Test whether the internal Snippets get translated at all.
 * This test is subsumed by each of the other translator tests.
 * 
 * @author Bernd Mathiske
 */
public class TeleInterpreterTest_snippets extends CompilerTestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(TeleInterpreterTest_snippets.suite());
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(TeleInterpreterTest_snippets.class.getSimpleName());
        suite.addTestSuite(TeleInterpreterTest_snippets.class);
        return new TeleInterpreterTestSetup(suite); // This performs the test
    }

    public TeleInterpreterTest_snippets(String name) {
        super(name);
    }

    public void test() {
        // new TeleInterpreterTestSetup() performs: Snippet.translateAll() and Snippet.optimizeAll()
    }

}
