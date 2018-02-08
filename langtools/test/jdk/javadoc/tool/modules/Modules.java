/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8159305 8166127
 * @summary Tests primarily the module graph computations.
 * @modules
 *      jdk.javadoc/jdk.javadoc.internal.api
 *      jdk.javadoc/jdk.javadoc.internal.tool
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.TestRunner
 * @run main Modules
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import toolbox.*;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;

public class Modules extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new Modules().runTests();
    }

    @Test
    public void testBasicMoption(Path base) throws Exception {
        Files.createDirectory(base);
        Path src = base.resolve("src");
        ModuleBuilder mb = new ModuleBuilder(tb, "m1");
        mb.comment("The first module.")
                .exports("pub")
                .classes("package pub; /** Class A */ public class A {}")
                .classes("package pro; /** Class B */ public class B {}")
                .write(src);
        execTask("--module-source-path", src.toString(),
                 "--module", "m1");
        checkModulesSpecified("m1");
        checkPackagesIncluded("pub");
        checkTypesIncluded("pub.A");
    }

    @Test
    public void testMultipleModulesOption1(Path base) throws Exception {
        Path src = base.resolve("src");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("The first module.")
                .exports("m1pub")
                .requires("m2")
                .classes("package m1pub; /** Class A */ public class A {}")
                .classes("package m1pro; /** Class B */ public class B {}")
                .write(src);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("m2pub")
                .classes("package m2pub; /** Class A */ public class A {}")
                .classes("package m2pro; /** Class B */ public class B {}")
                .write(src);
        execTask("--module-source-path", src.toString(),
            "--module", "m1,m2");
        checkModulesSpecified("m1", "m2");
        checkPackagesIncluded("m1pub", "m2pub");
        checkTypesIncluded("m1pub.A", "m2pub.A");

    }

    @Test
    public void testMultipleModulesAggregatedModuleOption(Path base) throws Exception {
        Path src = base.resolve("src");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("The first module.")
                .exports("m1pub")
                .requires("m2")
                .classes("package m1pub; /** Class A */ public class A {}")
                .classes("package m1pro; /** Class B */ public class B {}")
                .write(src);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("m2pub")
                .classes("package m2pub; /** Class A */ public class A {}")
                .classes("package m2pro; /** Class B */ public class B {}")
                .write(src);
        execTask("--module-source-path", src.toString(),
            "--module", "m1",
            "--module", "m2");
        checkModulesSpecified("m1", "m2");
        checkPackagesIncluded("m1pub", "m2pub");
        checkTypesIncluded("m1pub.A", "m2pub.A");

    }

    @Test
    public void testModulePathOption(Path base) throws Exception {
        Path src = base.resolve("src");
        Path modulePath = base.resolve("modules");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("Module on module path.")
                .exports("pkg1")
                .classes("package pkg1; /** Class A */ public class A { }")
                .build(modulePath);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("pkg2")
                .requires("m1")
                .classes("package pkg2; /** Class B */ public class B { /** Field f */ public pkg1.A f; }")
                .write(src);
        execTask("--module-source-path", src.toString(),
                "--module-path", modulePath.toString(),
                "--module", "m2");
        checkModulesSpecified("m2");
        checkPackagesIncluded("pkg2");
        checkMembersSelected("pkg2.B.f");

        // module path option "-p"
        execTask("--module-source-path", src.toString(),
                "-p", modulePath.toString(),
                "--module", "m2");
        // no module path
        execNegativeTask("--module-source-path", src.toString(),
                "--module", "m2");
        assertErrorPresent("error: module not found: m1");
    }

    @Test
    public void testUpgradeModulePathOption(Path base) throws Exception {
        Path src = base.resolve("src");
        Path modulePath = base.resolve("modules");
        Path upgradePath = base.resolve("upgrades");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("Module on module path.")
                .exports("pkg1")
                .classes("package pkg1; /** Class A */ public class A { }")
                .build(modulePath);

        ModuleBuilder mbUpgrade = new ModuleBuilder(tb, "m1");
        mbUpgrade.comment("Module on upgrade module path.")
                .exports("pkg1")
                .classes("package pkg1; /** Class C */ public class C { }")
                .build(upgradePath);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("pkg2")
                .requires("m1")
                .classes("package pkg2; /** Class B */ public class B { /** Field f */ public pkg1.C f; }")
                .write(src);
        execTask("--module-source-path", src.toString(),
                "--module-path", modulePath.toString(),
                "--upgrade-module-path", upgradePath.toString(),
                "--module", "m2");
        checkModulesSpecified("m2");
        checkPackagesIncluded("pkg2");
        checkMembersSelected("pkg2.B.f");

        // no upgrade module path
        execNegativeTask("--module-source-path", src.toString(),
                "--module-path", modulePath.toString(),
                "--module", "m2");
        assertErrorPresent("error: cannot find symbol");

        // dependency from module path
        ModuleBuilder mb3 = new ModuleBuilder(tb, "m3");
        mb3.comment("The third module.")
                .exports("pkg3")
                .requires("m1")
                .classes("package pkg3; /** Class Z */ public class Z { /** Field f */ public pkg1.A f; }")
                .write(src);
        execNegativeTask("--module-source-path", src.toString(),
                "--module-path", modulePath.toString(),
                "--upgrade-module-path", upgradePath.toString(),
                "--module", "m3");
        assertErrorPresent("Z.java:1: error: cannot find symbol");
    }

    @Test
    public void testAddModulesOption(Path base) throws Exception {
        Path src = base.resolve("src");
        Path modulePath = base.resolve("modules");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("Module on module path.")
                .exports("pkg1")
                .classes("package pkg1; /** Class A */ public class A { }")
                .build(modulePath);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("pkg2")
                .classes("package pkg2; /** @see pkg1.A */ public class B { }")
                .write(src);

        Path out = base.resolve("out-1");
        Files.createDirectories(out);
        String log = new JavadocTask(tb)
                .outdir(out)
                .options("--module-source-path", src.toString(),
                        "--module-path", modulePath.toString(),
                        "--module", "m2")
                .run(Expect.FAIL)
                .writeAll()
                .getOutput(OutputKind.DIRECT);
        if (!log.contains("B.java:1: error: reference not found")) {
            throw new Exception("Error not found");
        }

        out = base.resolve("out-2");
        Files.createDirectories(out);
        new JavadocTask(tb)
                .outdir(out)
                .options("--module-source-path", src.toString(),
                        "--module-path", modulePath.toString(),
                        "--add-modules", "m1",
                        "--module", "m2")
                .run()
                .writeAll();
    }

    @Test
    public void testLimitModulesOption(Path base) throws Exception {
        Path src = base.resolve("src");
        Path modulePath = base.resolve("modules");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("Module on module path.")
                .build(modulePath);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("pkg2")
                .requires("m1")
                .classes("package pkg2; /** Class B */ public class B { }")
                .write(src);

        execNegativeTask("--module-source-path", src.toString(),
                "--module-path", modulePath.toString(),
                "--limit-modules", "java.base",
                "--module", "m2");
        assertErrorPresent("error: module not found: m1");
    }

    @Test
    public void testAddExportsOption(Path base) throws Exception {
        Path src = base.resolve("src");
        Path modulePath = base.resolve("modules");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("Module on module path.")
                .classes("package pkg1; /** Class A */ public class A { }")
                .build(modulePath);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("pkg2")
                .requires("m1")
                .classes("package pkg2; /** Class B */ public class B { /** Field f */ public pkg1.A f; }")
                .write(src);
        execTask("--module-source-path", src.toString(),
                "--module-path", modulePath.toString(),
                "--add-exports", "m1/pkg1=m2",
                "--module", "m2");
        checkModulesSpecified("m2");
        checkPackagesIncluded("pkg2");
        checkMembersSelected("pkg2.B.f");
    }

    @Test
    public void testPatchModuleOption(Path base) throws Exception {
        Path src = base.resolve("src");
        Path modulePath = base.resolve("modules");
        Path patchPath = base.resolve("patch");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("Module on module path.")
                .exports("pkg1")
                .classes("package pkg1; /** Class A */ public class A { }")
                .build(modulePath);

        tb.writeJavaFiles(patchPath, "package pkg1; /** Class A */ public class A { public static int k; }");
        new JavacTask(tb)
                .files(patchPath.resolve("pkg1/A.java"))
                .run();

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("pkg2")
                .requires("m1")
                .classes("package pkg2; /** Class B */ public class B { /** Field f */ public int f = pkg1.A.k; }")
                .write(src);
        execTask("--module-source-path", src.toString(),
                "--patch-module", "m1=" + patchPath.toString(),
                "--module-path", modulePath.toString(),
                "--module", "m2");
        checkModulesSpecified("m2");
        checkPackagesIncluded("pkg2");
        checkMembersSelected("pkg2.B.f");
    }

    @Test
    public void testAddReadsOption(Path base) throws Exception {
        Path src = base.resolve("src");
        Path modulePath = base.resolve("modules");

        ModuleBuilder mb1 = new ModuleBuilder(tb, "m1");
        mb1.comment("Module on module path.")
                .exports("pkg1")
                .classes("package pkg1; /** Class A */ public class A {}")
                .build(modulePath);

        ModuleBuilder mb2 = new ModuleBuilder(tb, "m2");
        mb2.comment("The second module.")
                .exports("pkg2")
                .classes("package pkg2; /** Class B */ public class B { /** Field f */ public pkg1.A f;}")
                .write(src);
        execTask("--module-source-path", src.toString(),
                "--module-path", modulePath.toString(),
                "--add-modules", "m1",
                "--add-reads", "m2=m1",
                "--module", "m2");
        checkModulesSpecified("m2");
        checkPackagesIncluded("pkg2");
        checkMembersSelected("pkg2.B.f");
    }

    @Test
    public void testModuleOptionsWithLegacy(Path base) throws Exception {
        Files.createDirectory(base);
        Path src = base.resolve("src");
        Path classpath = base.resolve("classpath");
        Path modulePath = base.resolve("modules");

        tb.writeJavaFiles(classpath, "package pkg1; /** Class C */ public class C { }");
        new JavacTask(tb)
                .files(classpath.resolve("pkg1/C.java"))
                .run();

        ModuleBuilder mb = new ModuleBuilder(tb, "m1");
        mb.comment("The first module.")
                .exports("pub")
                .classes("package pub; /** Class M */ public class M { }")
                .build(modulePath);

        tb.writeJavaFiles(src, "package pkg; /** Class L */ public class L { public pkg1.C f1; public pub.M f2; }");

        execTask("--source-path", src.toString(),
                "--class-path", classpath.toString(),
                "--module-path", modulePath.toString(),
                "--add-modules", "m1",
                "pkg");
        checkPackagesIncluded("pkg");
        checkTypesIncluded("pkg.L");
        checkMembersSelected("pkg.L.f1");
        checkMembersSelected("pkg.L.f2");
        assertAbsent("error", OutputKind.DIRECT);
    }

    /**
     * Tests diamond graph, inspired by javac diamond tests.
     *
     *
     * Module M : test module, with variable requires
     *
     * Module N :
     *     requires transitive O  --->   Module O:
     *                                   requires J   ---->   Module J:
     *                                   exports openO          exports openJ
     *
     *
     * Module L :
     *     requires transitive P  --->   Module P:
     *                                   exports openP
     *
     *
     */

    @Test
    public void testExpandRequiresNone(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requires("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execTask("--module-source-path", src.toString(),
                "--module", "M");

        checkModulesSpecified("M");
        checkModulesIncluded("M");
        checkPackagesIncluded("p");
        checkTypesIncluded("p.Main");
        checkPackagesNotIncluded(".*open.*");
    }

    @Test
    public void testExpandRequiresTransitive(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresTransitive("N", src)
                .requires("L", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execTask("--module-source-path", src.toString(),
                "--module", "M",
                "--expand-requires", "transitive");

        checkModulesSpecified("M", "N", "O");
        checkModulesIncluded("M", "N", "O");
        checkPackagesIncluded("p", "openN", "openO");
        checkTypesIncluded("p.Main", "openN.N", "openO.O");
    }

    @Test
    public void testExpandRequiresAll(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresTransitive("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execTask("--module-source-path", src.toString(),
                "--module", "M",
                "--expand-requires", "all");

        checkModulesSpecified("M", "java.base", "N", "L", "O");
        checkModulesIncluded("M", "java.base", "N", "L", "O");
        checkModulesNotIncluded("P", "J", "Q");
        checkPackagesIncluded("p", "openN", "openL", "openO");
        checkPackagesNotIncluded(".*openP.*", ".*openJ.*");
        checkTypesIncluded("p.Main", "openN.N", "openL.L", "openO.O");
        checkTypesNotIncluded(".*openP.*", ".*openJ.*");
    }

    @Test
    public void testMissingModule(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresTransitive("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execNegativeTask("--module-source-path", src.toString(),
                "--module", "MIA",
                "--expand-requires", "all");

        assertErrorPresent("javadoc: error - module MIA not found.");
    }

    @Test
    public void testMissingModuleMultiModuleCmdline(Path base) throws Exception {
        Path src = base.resolve("src");

        createAuxiliaryModules(src);

        new ModuleBuilder(tb, "M")
                .comment("The M module.")
                .requiresTransitive("N", src)
                .requires("L", src)
                .requires("O", src)
                .exports("p")
                .classes("package p; public class Main { openO.O o; openN.N n; openL.L l; }")
                .write(src);

        execNegativeTask("--module-source-path", src.toString(),
                "--module", "M,N,L,MIA,O,P",
                "--expand-requires", "all");

        assertErrorPresent("javadoc: error - module MIA not found");
    }

    void createAuxiliaryModules(Path src) throws IOException {

        new ModuleBuilder(tb, "J")
                .comment("The J module.")
                .exports("openJ")
                .classes("package openJ;  /** Class J open. */ public class J { }")
                .classes("package closedJ; /** Class J closed. */ public class J  { }")
                .write(src);

        new ModuleBuilder(tb, "L")
                .comment("The L module.")
                .exports("openL")
                . requiresTransitive("P")
                .classes("package openL; /** Class L open */ public class L { }")
                .classes("package closedL;  /** Class L closed */ public class L { }")
                .write(src);

        new ModuleBuilder(tb, "N")
                .comment("The N module.")
                .exports("openN")
                .requiresTransitive("O")
                .classes("package openN; /** Class N open */ public class N  { }")
                .classes("package closedN; /** Class N closed */ public class N { }")
                .write(src);

        new ModuleBuilder(tb, "O")
                .comment("The O module.")
                .exports("openO")
                .requires("J")
                .classes("package openO; /** Class O open. */ public class O { openJ.J j; }")
                .classes("package closedO;  /** Class O closed. */ public class O { }")
                .write(src);

        new ModuleBuilder(tb, "P")
                .comment("The O module.")
                .exports("openP")
                .requires("J")
                .classes("package openP; /** Class O open. */ public class O { openJ.J j; }")
                .classes("package closedP;  /** Class O closed. */ public class O { }")
                .write(src);

        new ModuleBuilder(tb, "Q")
                .comment("The Q module.")
                .exports("openQ")
                .requires("J")
                .classes("package openQ; /** Class Q open. */ public class Q { openJ.J j; }")
                .classes("package closedQ;  /** Class Q closed. */ public class Q { }")
                .write(src);

    }
}
