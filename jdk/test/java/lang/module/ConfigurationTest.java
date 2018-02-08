/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @library /lib/testlibrary
 * @build ConfigurationTest ModuleUtils
 * @run testng ConfigurationTest
 * @summary Basic tests for java.lang.module.Configuration
 */

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Layer;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class ConfigurationTest {


    /**
     * Basic test of resolver
     *     m1 requires m2, m2 requires m3
     */
    public void testBasic() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires("m2")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m3")
                .build();

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 3);

        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());

        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        // m1 reads m2
        assertTrue(m1.reads().size() == 1);
        assertTrue(m1.reads().contains(m2));

        // m2 reads m3
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m3));

        // m3 reads nothing
        assertTrue(m3.reads().size() == 0);

        // toString
        assertTrue(cf.toString().contains("m1"));
        assertTrue(cf.toString().contains("m2"));
        assertTrue(cf.toString().contains("m3"));
    }


    /**
     * Basic test of "requires transitive":
     *     m1 requires m2, m2 requires transitive m3
     */
    public void testRequiresTransitive1() {
        // m1 requires m2, m2 requires transitive m3
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires("m2")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m3")
                .build();

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 3);

        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());

        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        // m1 reads m2 and m3
        assertTrue(m1.reads().size() == 2);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m1.reads().contains(m3));

        // m2 reads m3
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m3));

        // m3 reads nothing
        assertTrue(m3.reads().size() == 0);
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m3 requires m2
     */
    public void testRequiresTransitive2() {

        // cf1: m1 and m2, m2 requires transitive m1

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolveRequires(finder1, "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();

        assertTrue(m1.reads().size() == 0);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));


        // cf2: m3, m3 requires m2

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolveRequires(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m1").isPresent());  // in parent
        assertTrue(cf2.findModule("m2").isPresent());  // in parent
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m3 = cf2.findModule("m3").get();
        assertTrue(m3.configuration() == cf2);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m2 requires transitive m1, m3 requires m2
     */
    public void testRequiresTransitive3() {

        // cf1: m1

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolveRequires(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);


        // cf2: m2, m3: m2 requires transitive m1, m3 requires m2

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2, descriptor3);

        Configuration cf2 = resolveRequires(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m1").isPresent());   // in parent
        assertTrue(cf2.findModule("m2").isPresent());
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m2 = cf2.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m2.configuration() == cf2);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));

        assertTrue(m3.configuration() == cf2);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m2 requires transitive m1
     * - Configuraiton cf3: m3 requires m2
     */
    public void testRequiresTransitive4() {

        // cf1: m1

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolveRequires(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);


        // cf2: m2 requires transitive m1

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2);

        Configuration cf2 = resolveRequires(cf1, finder2, "m2");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m1").isPresent());  // in parent
        assertTrue(cf2.findModule("m2").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m2 = cf2.findModule("m2").get();

        assertTrue(m2.configuration() == cf2);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));


        // cf3: m3 requires m2

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m2")
                .build();

        ModuleFinder finder3 = ModuleUtils.finderOf(descriptor3);

        Configuration cf3 = resolveRequires(cf2, finder3, "m3");

        assertTrue(cf3.modules().size() == 1);
        assertTrue(cf3.findModule("m1").isPresent());  // in parent
        assertTrue(cf3.findModule("m2").isPresent());  // in parent
        assertTrue(cf3.findModule("m3").isPresent());
        assertTrue(cf3.parents().size() == 1);
        assertTrue(cf3.parents().get(0) == cf2);

        ResolvedModule m3 = cf3.findModule("m3").get();

        assertTrue(m3.configuration() == cf3);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m3 requires transitive m2, m4 requires m3
     */
    public void testRequiresTransitive5() {

        // cf1: m1, m2 requires transitive m1

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolveRequires(finder1, "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();

        assertTrue(m1.configuration() == cf1);
        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.configuration() == cf1);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));


        // cf2: m3 requires transitive m2, m4 requires m3

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m2")
                .build();

        ModuleDescriptor descriptor4
            = ModuleDescriptor.module("m4")
                .requires("m3")
                .build();


        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3, descriptor4);

        Configuration cf2 = resolveRequires(cf1, finder2, "m3", "m4");

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m1").isPresent());   // in parent
        assertTrue(cf2.findModule("m2").isPresent());   // in parent
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.findModule("m4").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        ResolvedModule m3 = cf2.findModule("m3").get();
        ResolvedModule m4 = cf2.findModule("m4").get();

        assertTrue(m3.configuration() == cf2);
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));

        assertTrue(m4.configuration() == cf2);
        assertTrue(m4.reads().size() == 3);
        assertTrue(m4.reads().contains(m1));
        assertTrue(m4.reads().contains(m2));
        assertTrue(m4.reads().contains(m3));
    }


    /**
     * Basic test of "requires transitive" with configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m1, m3 requires transitive m1
     * - Configuration cf3(cf1,cf2): m4 requires m2, m3
     */
    public void testRequiresTransitive6() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor4
            = ModuleDescriptor.module("m4")
                .requires("m2")
                .requires("m3")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);
        Configuration cf1 = resolveRequires(finder1, "m2");
        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());
        assertTrue(cf1.parents().size() == 1);
        assertTrue(cf1.parents().get(0) == Configuration.empty());

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, descriptor3);
        Configuration cf2 = resolveRequires(finder2, "m3");
        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.findModule("m1").isPresent());
        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == Configuration.empty());

        ModuleFinder finder3 = ModuleUtils.finderOf(descriptor4);
        Configuration cf3 = Configuration.resolveRequires(finder3,
                List.of(cf1, cf2),
                ModuleFinder.of(),
                Set.of("m4"));
        assertTrue(cf3.modules().size() == 1);
        assertTrue(cf3.findModule("m4").isPresent());

        ResolvedModule m1_l = cf1.findModule("m1").get();
        ResolvedModule m1_r = cf2.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();
        ResolvedModule m4 = cf3.findModule("m4").get();
        assertTrue(m4.configuration() == cf3);

        assertTrue(m4.reads().size() == 4);
        assertTrue(m4.reads().contains(m1_l));
        assertTrue(m4.reads().contains(m1_r));
        assertTrue(m4.reads().contains(m2));
        assertTrue(m4.reads().contains(m3));
    }


    /**
     * Basic test of "requires static":
     *     m1 requires static m2
     *     m2 is not observable
     *     resolve m1
     */
    public void testRequiresStatic1() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 1);

        ResolvedModule m1 = cf.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     *     m1 requires static m2
     *     m2
     *     resolve m1
     */
    public void testRequiresStatic2() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 1);

        ResolvedModule m1 = cf.findModule("m1").get();
        assertTrue(m1.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     *     m1 requires static m2
     *     m2
     *     resolve m1, m2
     */
    public void testRequiresStatic3() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolveRequires(finder, "m1", "m2");

        assertTrue(cf.modules().size() == 2);

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertTrue(m1.reads().size() == 1);
        assertTrue(m1.reads().contains(m2));

        assertTrue(m2.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     *     m1 requires m2, m3
     *     m2 requires static m2
     *     m3
     */
    public void testRequiresStatic4() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires("m2")
                .requires("m3")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.STATIC), "m3")
                .build();

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .build();

        ModuleFinder finder
                = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 3);

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        assertTrue(m1.reads().size() == 2);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m1.reads().contains(m3));

        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m3));

        assertTrue(m3.reads().size() == 0);
    }


    /**
     * Basic test of "requires static":
     * The test consists of three configurations:
     * - Configuration cf1: m1, m2
     * - Configuration cf2: m3 requires m1, requires static m2
     */
    public void testRequiresStatic5() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolveRequires(finder1, "m1", "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolveRequires(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m3").isPresent());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires static":
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m3 requires m1, requires static m2
     */
    public void testRequiresStatic6() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolveRequires(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m1")
                .requires(Set.of(Requires.Modifier.STATIC), "m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolveRequires(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m3").isPresent());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m1));
    }


    /**
     * Basic test of "requires static":
     *     (m1 not observable)
     *     m2 requires transitive static m1
     *     m3 requires m2
     */
    public void testRequiresStatic7() {
        ModuleDescriptor descriptor1 = null;  // not observable

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE,
                                Requires.Modifier.STATIC),
                         "m1")
                .build();

        ModuleDescriptor descriptor3
                = ModuleDescriptor.module("m3")
                .requires("m2")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor2, descriptor3);

        Configuration cf = resolveRequires(finder, "m3");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();
        assertTrue(m2.reads().isEmpty());
        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of "requires static":
     * - Configuration cf1: m2 requires transitive static m1
     * - Configuration cf2: m3 requires m2
     */
    public void testRequiresStatic8() {
        ModuleDescriptor descriptor1 = null;  // not observable

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE,
                                Requires.Modifier.STATIC),
                        "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor2);

        Configuration cf1 = resolveRequires(finder1, "m2");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m2").isPresent());
        ResolvedModule m2 = cf1.findModule("m2").get();
        assertTrue(m2.reads().isEmpty());

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolveRequires(cf1, finder2, "m3");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m3").isPresent());
        ResolvedModule m3 = cf2.findModule("m3").get();
        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Basic test of binding services
     *     m1 uses p.S
     *     m2 provides p.S
     */
    public void testServiceBinding1() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolveRequiresAndUses(finder, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertTrue(m1.configuration() == cf);
        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.configuration() == cf);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));
    }


    /**
     * Basic test of binding services
     *     m1 uses p.S1
     *     m2 provides p.S1, m2 uses p.S2
     *     m3 provides p.S2
     */
    public void testServiceBinding2() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .uses("p.S1")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .uses("p.S2")
                .contains("q")
                .provides("p.S1", "q.Service1Impl")
                .build();

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m1")
                .contains("q")
                .provides("p.S2", "q.Service2Impl")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolveRequiresAndUses(finder, "m1");

        assertTrue(cf.modules().size() == 3);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
        assertTrue(cf.parents().size() == 1);
        assertTrue(cf.parents().get(0) == Configuration.empty());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        assertTrue(m1.configuration() == cf);
        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.configuration() == cf);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));

        assertTrue(m3.configuration() == cf);
        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m1));
    }


    /**
     * Basic test of binding services with configurations.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1 uses p.S
     * - Configuration cf2: m2 provides p.S
     */
    public void testServiceBindingWithConfigurations1() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolveRequires(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2);

        Configuration cf2 = resolveRequiresAndUses(cf1, finder2); // no roots

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m2").isPresent());

        ResolvedModule m1 = cf1.findModule("m1").get();
        ResolvedModule m2 = cf2.findModule("m2").get();

        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));
    }


    /**
     * Basic test of binding services with configurations.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1 uses p.S && provides p.S,
     *                      m2 provides p.S
     * - Configuration cf2: m3 provides p.S
     *                      m4 provides p.S
     */
    public void testServiceBindingWithConfigurations2() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .uses("p.S")
                .contains("p1")
                .provides("p.S", "p1.ServiceImpl")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .contains("p2")
                .provides("p.S", "p2.ServiceImpl")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolveRequiresAndUses(finder1, "m1");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());


        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m1")
                .contains("p3")
                .provides("p.S", "p3.ServiceImpl")
                .build();

        ModuleDescriptor descriptor4
            = ModuleDescriptor.module("m4")
                .requires("m1")
                .contains("p4")
                .provides("p.S", "p4.ServiceImpl")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3, descriptor4);

        Configuration cf2 = resolveRequiresAndUses(cf1, finder2); // no roots

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m3").isPresent());
        assertTrue(cf2.findModule("m4").isPresent());

        ResolvedModule m1 = cf2.findModule("m1").get();  // should find in parent
        ResolvedModule m2 = cf2.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();
        ResolvedModule m4 = cf2.findModule("m4").get();

        assertTrue(m1.reads().size() == 0);

        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));

        assertTrue(m3.reads().size() == 1);
        assertTrue(m3.reads().contains(m1));

        assertTrue(m4.reads().size() == 1);
        assertTrue(m4.reads().contains(m1));
    }


    /**
     * Basic test of binding services with configurations.
     *
     * Configuration cf1: p@1.0 provides p.S
     * Test configuration cf2: m1 uses p.S, p@2.0 provides p.S
     * Test configuration cf2: m1 uses p.S
     */
    public void testServiceBindingWithConfigurations3() {

        ModuleDescriptor service
            = ModuleDescriptor.module("s")
                .exports("p")
                .build();

        ModuleDescriptor provider_v1
            = ModuleDescriptor.module("p")
                .version("1.0")
                .requires("s")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(service, provider_v1);

        Configuration cf1 = resolveRequires(finder1, "p");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("s").isPresent());
        assertTrue(cf1.findModule("p").isPresent());

        // p@1.0 in cf1
        ResolvedModule p = cf1.findModule("p").get();
        assertEquals(p.reference().descriptor(), provider_v1);


        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires("s")
                .uses("p.S")
                .build();

        ModuleDescriptor provider_v2
            = ModuleDescriptor.module("p")
                .version("2.0")
                .requires("s")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, provider_v2);


        // finder2 is the before ModuleFinder and so p@2.0 should be located

        Configuration cf2 = resolveRequiresAndUses(cf1, finder2, "m1");

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);
        assertTrue(cf2.modules().size() == 2);

        // p should be found in cf2
        p = cf2.findModule("p").get();
        assertTrue(p.configuration() == cf2);
        assertEquals(p.reference().descriptor(), provider_v2);


        // finder2 is the after ModuleFinder and so p@2.0 should not be located
        // as module p is in parent configuration.

        cf2 = resolveRequiresAndUses(cf1, ModuleFinder.of(), finder2, "m1");

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);
        assertTrue(cf2.modules().size() == 1);

        // p should be found in cf1
        p = cf2.findModule("p").get();
        assertTrue(p.configuration() == cf1);
        assertEquals(p.reference().descriptor(), provider_v1);
    }


    /**
     * Basic test with two module finders.
     *
     * Module m2 can be found by both the before and after finders.
     */
    public void testWithTwoFinders1() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .requires("m2")
                .build();

        ModuleDescriptor descriptor2_v1
            = ModuleDescriptor.module("m2")
                .version("1.0")
                .build();

        ModuleDescriptor descriptor2_v2
            = ModuleDescriptor.module("m2")
                .version("2.0")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor2_v1);
        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, descriptor2_v2);

        Configuration cf = resolveRequires(finder1, finder2, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertEquals(m1.reference().descriptor(), descriptor1);
        assertEquals(m2.reference().descriptor(), descriptor2_v1);
    }


    /**
     * Basic test with two modules finders and service binding.
     *
     * The before and after ModuleFinders both locate a service provider module
     * named "m2" that provide implementations of the same service type.
     */
    public void testWithTwoFinders2() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleDescriptor descriptor2_v1
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleDescriptor descriptor2_v2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2_v1);
        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2_v2);

        Configuration cf = resolveRequiresAndUses(finder1, finder2, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());

        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();

        assertEquals(m1.reference().descriptor(), descriptor1);
        assertEquals(m2.reference().descriptor(), descriptor2_v1);
    }


    /**
     * Basic test for resolving a module that is located in the parent
     * configuration.
     */
    public void testResolvedInParent1() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolveRequires(finder, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        Configuration cf2 = resolveRequires(cf1, finder, "m1");

        assertTrue(cf2.modules().size() == 1);
    }


    /**
     * Basic test for resolving a module that has a dependency on a module
     * in the parent configuration.
     */
    public void testResolvedInParent2() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolveRequires(finder1, "m1");

        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());


        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2);

        Configuration cf2 = resolveRequires(cf1, ModuleFinder.of(), finder2, "m2");

        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m2").isPresent());

        ResolvedModule m1 = cf2.findModule("m1").get();   // find in parent
        ResolvedModule m2 = cf2.findModule("m2").get();

        assertTrue(m1.reads().size() == 0);
        assertTrue(m2.reads().size() == 1);
        assertTrue(m2.reads().contains(m1));
    }


    /**
     * Basic test of resolving a module that depends on modules in two parent
     * configurations.
     *
     * The test consists of three configurations:
     * - Configuration cf1: m1
     * - Configuration cf2: m2
     * - Configuration cf3(cf1,cf2): m3 requires m1, m2
     */
    public void testResolvedInMultipleParents1() {

        // Configuration cf1: m1
        ModuleDescriptor descriptor1 = ModuleDescriptor.module("m1").build();
        Configuration cf1 = resolveRequires(ModuleUtils.finderOf(descriptor1), "m1");
        assertEquals(cf1.parents(), List.of(Configuration.empty()));
        assertTrue(cf1.findModule("m1").isPresent());
        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.configuration() == cf1);

        // Configuration cf2: m2
        ModuleDescriptor descriptor2 = ModuleDescriptor.module("m2").build();
        Configuration cf2 = resolveRequires(ModuleUtils.finderOf(descriptor2), "m2");
        assertEquals(cf2.parents(), List.of(Configuration.empty()));
        assertTrue(cf2.findModule("m2").isPresent());
        ResolvedModule m2 = cf2.findModule("m2").get();
        assertTrue(m2.configuration() == cf2);

        // Configuration cf3(cf1,cf2): m3 requires m1 and m2
        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m1")
                .requires("m2")
                .build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor3);
        Configuration cf3 = Configuration.resolveRequires(
                finder,
                List.of(cf1, cf2),  // parents
                ModuleFinder.of(),
                Set.of("m3"));
        assertEquals(cf3.parents(), List.of(cf1, cf2));
        assertTrue(cf3.findModule("m3").isPresent());
        ResolvedModule m3 = cf3.findModule("m3").get();
        assertTrue(m3.configuration() == cf3);

        // check readability
        assertTrue(m1.reads().isEmpty());
        assertTrue(m2.reads().isEmpty());
        assertEquals(m3.reads(), Set.of(m1, m2));
    }


    /**
     * Basic test of resolving a module that depends on modules in three parent
     * configurations arranged in a diamond (two direct parents).
     *
     * The test consists of four configurations:
     * - Configuration cf1: m1
     * - Configuration cf2(cf1): m2 requires m1
     * - Configuration cf3(cf3): m3 requires m1
     * - Configuration cf4(cf2,cf3): m4 requires m1,m2,m3
     */
    public void testResolvedInMultipleParents2() {
        // Configuration cf1: m1
        ModuleDescriptor descriptor1 = ModuleDescriptor.module("m1").build();
        Configuration cf1 = resolveRequires(ModuleUtils.finderOf(descriptor1), "m1");
        assertEquals(cf1.parents(), List.of(Configuration.empty()));
        assertTrue(cf1.findModule("m1").isPresent());
        ResolvedModule m1 = cf1.findModule("m1").get();
        assertTrue(m1.configuration() == cf1);

        // Configuration cf2(cf1): m2 requires m1
        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .build();
        Configuration cf2 = Configuration.resolveRequires(
                ModuleUtils.finderOf(descriptor2),
                List.of(cf1),  // parents
                ModuleFinder.of(),
                Set.of("m2"));
        assertEquals(cf2.parents(), List.of(cf1));
        assertTrue(cf2.findModule("m2").isPresent());
        ResolvedModule m2 = cf2.findModule("m2").get();
        assertTrue(m2.configuration() == cf2);

        // Configuration cf3(cf1): m3 requires m1
        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m1")
                .build();
        Configuration cf3 = Configuration.resolveRequires(
                ModuleUtils.finderOf(descriptor3),
                List.of(cf1),  // parents
                ModuleFinder.of(),
                Set.of("m3"));
        assertEquals(cf3.parents(), List.of(cf1));
        assertTrue(cf3.findModule("m3").isPresent());
        ResolvedModule m3 = cf3.findModule("m3").get();
        assertTrue(m3.configuration() == cf3);

        // Configuration cf4(cf2,cf3): m4 requires m1,m2,m3
        ModuleDescriptor descriptor4
            = ModuleDescriptor.module("m4")
                .requires("m1")
                .requires("m2")
                .requires("m3")
                .build();
        Configuration cf4 = Configuration.resolveRequires(
                ModuleUtils.finderOf(descriptor4),
                List.of(cf2, cf3),  // parents
                ModuleFinder.of(),
                Set.of("m4"));
        assertEquals(cf4.parents(), List.of(cf2, cf3));
        assertTrue(cf4.findModule("m4").isPresent());
        ResolvedModule m4 = cf4.findModule("m4").get();
        assertTrue(m4.configuration() == cf4);

        // check readability
        assertTrue(m1.reads().isEmpty());
        assertEquals(m2.reads(), Set.of(m1));
        assertEquals(m3.reads(), Set.of(m1));
        assertEquals(m4.reads(), Set.of(m1, m2, m3));
    }


    /**
     * Basic test of resolving a module that depends on modules in three parent
     * configurations arranged in a diamond (two direct parents).
     *
     * The test consists of four configurations:
     * - Configuration cf1: m1@1
     * - Configuration cf2: m1@2, m2@2
     * - Configuration cf3: m1@3, m2@3, m3@3
     * - Configuration cf4(cf1,cf2,cf3): m4 requires m1,m2,m3
     */
    public void testResolvedInMultipleParents3() {
        ModuleDescriptor descriptor1, descriptor2, descriptor3;

        // Configuration cf1: m1@1
        descriptor1 = ModuleDescriptor.module("m1").version("1").build();
        Configuration cf1 = resolveRequires(ModuleUtils.finderOf(descriptor1), "m1");
        assertEquals(cf1.parents(), List.of(Configuration.empty()));

        // Configuration cf2: m1@2, m2@2
        descriptor1 = ModuleDescriptor.module("m1").version("2").build();
        descriptor2 = ModuleDescriptor.module("m2").version("2").build();
        Configuration cf2 = resolveRequires(
                ModuleUtils.finderOf(descriptor1, descriptor2),
                "m1", "m2");
        assertEquals(cf2.parents(), List.of(Configuration.empty()));

        // Configuration cf3: m1@3, m2@3, m3@3
        descriptor1 = ModuleDescriptor.module("m1").version("3").build();
        descriptor2 = ModuleDescriptor.module("m2").version("3").build();
        descriptor3 = ModuleDescriptor.module("m3").version("3").build();
        Configuration cf3 = resolveRequires(
                ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3),
                "m1", "m2", "m3");
        assertEquals(cf3.parents(), List.of(Configuration.empty()));

        // Configuration cf4(cf1,cf2,cf3): m4 requires m1,m2,m3
        ModuleDescriptor descriptor4
                = ModuleDescriptor.module("m4")
                .requires("m1")
                .requires("m2")
                .requires("m3")
                .build();
        Configuration cf4 = Configuration.resolveRequires(
                ModuleUtils.finderOf(descriptor4),
                List.of(cf1, cf2, cf3),  // parents
                ModuleFinder.of(),
                Set.of("m4"));
        assertEquals(cf4.parents(), List.of(cf1, cf2, cf3));

        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf2.findModule("m1").isPresent());
        assertTrue(cf2.findModule("m2").isPresent());
        assertTrue(cf3.findModule("m1").isPresent());
        assertTrue(cf3.findModule("m2").isPresent());
        assertTrue(cf3.findModule("m3").isPresent());
        assertTrue(cf4.findModule("m4").isPresent());

        ResolvedModule m1_1 = cf1.findModule("m1").get();
        ResolvedModule m1_2 = cf2.findModule("m1").get();
        ResolvedModule m2_2 = cf2.findModule("m2").get();
        ResolvedModule m1_3 = cf3.findModule("m1").get();
        ResolvedModule m2_3 = cf3.findModule("m2").get();
        ResolvedModule m3_3 = cf3.findModule("m3").get();
        ResolvedModule m4   = cf4.findModule("m4").get();

        assertTrue(m1_1.configuration() == cf1);
        assertTrue(m1_2.configuration() == cf2);
        assertTrue(m2_2.configuration() == cf2);
        assertTrue(m1_3.configuration() == cf3);
        assertTrue(m2_3.configuration() == cf3);
        assertTrue(m3_3.configuration() == cf3);
        assertTrue(m4.configuration() == cf4);

        // check readability
        assertTrue(m1_1.reads().isEmpty());
        assertTrue(m1_2.reads().isEmpty());
        assertTrue(m2_2.reads().isEmpty());
        assertTrue(m1_3.reads().isEmpty());
        assertTrue(m2_3.reads().isEmpty());
        assertTrue(m3_3.reads().isEmpty());
        assertEquals(m4.reads(), Set.of(m1_1, m2_2, m3_3));
    }


    /**
     * Basic test of using the beforeFinder to override a module in a parent
     * configuration.
     */
    public void testOverriding1() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolveRequires(finder, "m1");
        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        Configuration cf2 = resolveRequires(cf1, finder, "m1");
        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m1").isPresent());
    }

    /**
     * Basic test of using the beforeFinder to override a module in a parent
     * configuration.
     */
    public void testOverriding2() {
        ModuleDescriptor descriptor1 = ModuleDescriptor.module("m1").build();
        Configuration cf1 = resolveRequires(ModuleUtils.finderOf(descriptor1), "m1");
        assertTrue(cf1.modules().size() == 1);
        assertTrue(cf1.findModule("m1").isPresent());

        ModuleDescriptor descriptor2 = ModuleDescriptor.module("m2").build();
        Configuration cf2 = resolveRequires(ModuleUtils.finderOf(descriptor2), "m2");
        assertTrue(cf2.modules().size() == 1);
        assertTrue(cf2.findModule("m2").isPresent());

        ModuleDescriptor descriptor3 = ModuleDescriptor.module("m3").build();
        Configuration cf3 = resolveRequires(ModuleUtils.finderOf(descriptor3), "m3");
        assertTrue(cf3.modules().size() == 1);
        assertTrue(cf3.findModule("m3").isPresent());

        // override m2, m1 and m3 should be found in parent configurations
        ModuleFinder finder = ModuleUtils.finderOf(descriptor2);
        Configuration cf4 = Configuration.resolveRequires(
                finder,
                List.of(cf1, cf2, cf3),
                ModuleFinder.of(),
                Set.of("m1", "m2", "m3"));
        assertTrue(cf4.modules().size() == 1);
        assertTrue(cf4.findModule("m2").isPresent());
        ResolvedModule m2 = cf4.findModule("m2").get();
        assertTrue(m2.configuration() == cf4);
    }


    /**
     * Basic test of using the beforeFinder to override a module in the parent
     * configuration but where implied readability in the picture so that the
     * module in the parent is read.
     *
     * The test consists of two configurations:
     * - Configuration cf1: m1, m2 requires transitive m1
     * - Configuration cf2: m1, m3 requires m2
     */
    public void testOverriding3() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolveRequires(finder1, "m2");

        assertTrue(cf1.modules().size() == 2);
        assertTrue(cf1.findModule("m1").isPresent());
        assertTrue(cf1.findModule("m2").isPresent());

        // cf2: m3 requires m2, m1

        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor1, descriptor3);

        Configuration cf2 = resolveRequires(cf1, finder2, "m1", "m3");

        assertTrue(cf2.parents().size() == 1);
        assertTrue(cf2.parents().get(0) == cf1);

        assertTrue(cf2.modules().size() == 2);
        assertTrue(cf2.findModule("m1").isPresent());
        assertTrue(cf2.findModule("m3").isPresent());

        ResolvedModule m1_1 = cf1.findModule("m1").get();
        ResolvedModule m1_2 = cf2.findModule("m1").get();
        ResolvedModule m2 = cf1.findModule("m2").get();
        ResolvedModule m3 = cf2.findModule("m3").get();

        assertTrue(m1_1.configuration() == cf1);
        assertTrue(m1_2.configuration() == cf2);
        assertTrue(m3.configuration() == cf2);


        // check that m3 reads cf1/m1 and cf2/m2
        assertTrue(m3.reads().size() == 2);
        assertTrue(m3.reads().contains(m1_1));
        assertTrue(m3.reads().contains(m2));
    }


    /**
     * Root module not found
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testRootNotFound() {
        resolveRequires(ModuleFinder.of(), "m1");
    }


    /**
     * Direct dependency not found
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testDirectDependencyNotFound() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1").requires("m2").build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);
        resolveRequires(finder, "m1");
    }


    /**
     * Transitive dependency not found
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testTransitiveDependencyNotFound() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1").requires("m2").build();
        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2").requires("m3").build();
        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);
        resolveRequires(finder, "m1");
    }


    /**
     * Service provider dependency not found
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testServiceProviderDependencyNotFound() {

        // service provider dependency (on m3) not found

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .uses("p.S")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .requires("m3")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // should throw ResolutionException because m3 is not found
        Configuration cf = resolveRequiresAndUses(finder, "m1");
    }


    /**
     * Simple cycle.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testSimpleCycle() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1").requires("m2").build();
        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2").requires("m3").build();
        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3").requires("m1").build();
        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);
        resolveRequires(finder, "m1");
    }

    /**
     * Basic test for detecting cycles involving a service provider module
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testCycleInProvider() {

        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .uses("p.S")
                .build();
        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .requires("m3")
                .contains("q")
                .provides("p.S", "q.T")
                .build();
        ModuleDescriptor descriptor3
            = ModuleDescriptor.module("m3")
                .requires("m2")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        // should throw ResolutionException because of the m2 <--> m3 cycle
        resolveRequiresAndUses(finder, "m1");
    }


    /**
     * Test two modules exporting package p to a module that reads both.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testPackageSuppliedByTwoOthers() {

        ModuleDescriptor descriptor1
            =  ModuleDescriptor.module("m1")
                .requires("m2")
                .requires("m3")
                .build();

        ModuleDescriptor descriptor2
            =  ModuleDescriptor.module("m2")
                .exports("p")
                .build();

        ModuleDescriptor descriptor3
            =  ModuleDescriptor.module("m3")
                .exports("p", Set.of("m1"))
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        // m2 and m3 export package p to module m1
        resolveRequires(finder, "m1");
    }


    /**
     * Test the scenario where a module contains a package p and reads
     * a module that exports package p.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testPackageSuppliedBySelfAndOther() {

        ModuleDescriptor descriptor1
            =  ModuleDescriptor.module("m1")
                .requires("m2")
                .contains("p")
                .build();

        ModuleDescriptor descriptor2
            =  ModuleDescriptor.module("m2")
                .exports("p")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // m1 contains package p, module m2 exports package p to m1
        resolveRequires(finder, "m1");
    }


    /**
     * Test the scenario where a module contains a package p and reads
     * a module that also contains a package p.
     */
    public void testContainsPackageInSelfAndOther() {
        ModuleDescriptor descriptor1
            =  ModuleDescriptor.module("m1")
                .requires("m2")
                .contains("p")
                .build();

        ModuleDescriptor descriptor2
            =  ModuleDescriptor.module("m2")
                .contains("p")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());

        // m1 reads m2, m2 reads nothing
        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        assertTrue(m1.reads().size() == 1);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m2.reads().size() == 0);
    }


    /**
     * Test the scenario where a module that exports a package that is also
     * exported by a module that it reads in a parent layer.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testExportSamePackageAsBootLayer() {
        ModuleDescriptor descriptor
            =  ModuleDescriptor.module("m1")
                .requires("java.base")
                .exports("java.lang")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor);

        Configuration bootConfiguration = Layer.boot().configuration();

        // m1 contains package java.lang, java.base exports package java.lang to m1
        resolveRequires(bootConfiguration, finder, "m1");
    }


    /**
     * Test "uses p.S" where p is contained in the same module.
     */
    public void testContainsService1() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .contains("p")
                .uses("p.S")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 1);
        assertTrue(cf.findModule("m1").isPresent());
    }


    /**
     * Test "uses p.S" where p is contained in a different module.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testContainsService2() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .contains("p")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .uses("p.S")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // m2 does not read a module that exports p
        resolveRequires(finder, "m2");
    }


    /**
     * Test "provides p.S" where p is contained in the same module.
     */
    public void testContainsService3() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .contains("p")
                .contains("q")
                .provides("p.S", "q.S1")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 1);
        assertTrue(cf.findModule("m1").isPresent());
    }


    /**
     * Test "provides p.S" where p is contained in a different module.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testContainsService4() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .contains("p")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .contains("q")
                .provides("p.S", "q.S1")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // m2 does not read a module that exports p
        resolveRequires(finder, "m2");
    }


    /**
     * Test "uses p.S" where p is not exported to the module.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testServiceTypePackageNotExported1() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .uses("p.S")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        // m1 does not read a module that exports p
        resolveRequires(finder, "m1");
    }


    /**
     * Test "provides p.S" where p is not exported to the module.
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testServiceTypePackageNotExported2() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .contains("q")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        // m1 does not read a module that exports p
        resolveRequires(finder, "m1");
    }


    /**
     * Test "provides p.S with q.T" where q.T is not local
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testProviderPackageNotLocal() {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.module("m1")
                .exports("p")
                .exports("q")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.module("m2")
                .requires("m1")
                .provides("p.S", "q.T")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        // q.T not in module m2
        resolveRequires(finder, "m2");
    }


    /**
     * Test the empty configuration.
     */
    public void testEmptyConfiguration() {
        Configuration cf = Configuration.empty();

        assertTrue(cf.parents().isEmpty());

        assertTrue(cf.modules().isEmpty());
        assertFalse(cf.findModule("java.base").isPresent());
    }


    // platform specific modules

    @DataProvider(name = "platformmatch")
    public Object[][] createPlatformMatches() {
        return new Object[][]{

            { "linux-*-*",       "*-*-*" },
            { "*-arm-*",         "*-*-*" },
            { "*-*-2.6",         "*-*-*" },

            { "linux-arm-*",     "*-*-*" },
            { "linux-*-2.6",     "*-*-*" },
            { "*-arm-2.6",       "*-*-*" },

            { "linux-arm-2.6",   "*-*-*" },

            { "linux-*-*",       "linux-*-*" },
            { "*-arm-*",         "*-arm-*"   },
            { "*-*-2.6",         "*-*-2.6"   },

            { "linux-arm-*",     "linux-arm-*" },
            { "linux-arm-*",     "linux-*-*"   },
            { "linux-*-2.6",     "linux-*-2.6" },
            { "linux-*-2.6",     "linux-arm-*" },

            { "linux-arm-2.6",   "linux-arm-2.6" },

        };

    };

    @DataProvider(name = "platformmismatch")
    public Object[][] createBad() {
        return new Object[][] {

            { "linux-*-*",        "solaris-*-*"   },
            { "linux-x86-*",      "linux-arm-*"   },
            { "linux-*-2.4",      "linux-x86-2.6" },
        };
    }

    /**
     * Test creating a configuration containing platform specific modules.
     */
    @Test(dataProvider = "platformmatch")
    public void testPlatformMatch(String s1, String s2) {

        ModuleDescriptor.Builder builder
            = ModuleDescriptor.module("m1").requires("m2");

        String[] s = s1.split("-");
        if (!s[0].equals("*"))
            builder.osName(s[0]);
        if (!s[1].equals("*"))
            builder.osArch(s[1]);
        if (!s[2].equals("*"))
            builder.osVersion(s[2]);

        ModuleDescriptor descriptor1 = builder.build();

        builder = ModuleDescriptor.module("m2");

        s = s2.split("-");
        if (!s[0].equals("*"))
            builder.osName(s[0]);
        if (!s[1].equals("*"))
            builder.osArch(s[1]);
        if (!s[2].equals("*"))
            builder.osVersion(s[2]);

        ModuleDescriptor descriptor2 = builder.build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolveRequires(finder, "m1");

        assertTrue(cf.modules().size() == 2);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
    }

    /**
     * Test attempting to create a configuration with modules for different
     * platforms.
     */
    @Test(dataProvider = "platformmismatch",
          expectedExceptions = ResolutionException.class )
    public void testPlatformMisMatch(String s1, String s2) {
        testPlatformMatch(s1, s2);
    }


    // no parents

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testResolveRequiresWithNoParents() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration.resolveRequires(empty, List.of(), empty, Set.of());
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testResolveRequiresAndUsesWithNoParents() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration.resolveRequiresAndUses(empty, List.of(), empty, Set.of());
    }


    // null handling

    // finder1, finder2, roots


    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresWithNull1() {
        resolveRequires((ModuleFinder)null, ModuleFinder.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresWithNull2() {
        resolveRequires(ModuleFinder.of(), (ModuleFinder)null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresWithNull3() {
        Configuration empty = Configuration.empty();
        Configuration.resolveRequires(null, List.of(empty),  ModuleFinder.of(), Set.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresWithNull4() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration.resolveRequires(empty, null, empty, Set.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresWithNull5() {
        Configuration cf = Layer.boot().configuration();
        Configuration.resolveRequires(ModuleFinder.of(), List.of(cf), null, Set.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresWithNull6() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration cf = Layer.boot().configuration();
        Configuration.resolveRequires(empty, List.of(cf), empty, null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresAndUsesWithNull1() {
        resolveRequiresAndUses((ModuleFinder) null, ModuleFinder.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresAndUsesWithNull2() {
        resolveRequiresAndUses(ModuleFinder.of(), (ModuleFinder) null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresAndUsesWithNull3() {
        Configuration empty = Configuration.empty();
        Configuration.resolveRequiresAndUses(null, List.of(empty), ModuleFinder.of(), Set.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresAndUsesWithNull4() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration.resolveRequiresAndUses(empty, null, empty, Set.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresAndUsesWithNull5() {
        Configuration cf = Layer.boot().configuration();
        Configuration.resolveRequiresAndUses(ModuleFinder.of(), List.of(cf), null, Set.of());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testResolveRequiresAndUsesWithNull6() {
        ModuleFinder empty = ModuleFinder.of();
        Configuration cf = Layer.boot().configuration();
        Configuration.resolveRequiresAndUses(empty, List.of(cf), empty, null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testFindModuleWithNull() {
        Configuration.empty().findModule(null);
    }

    // immutable sets

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void testImmutableSet1() {
        Configuration cf = Layer.boot().configuration();
        ResolvedModule base = cf.findModule("java.base").get();
        cf.modules().add(base);
    }

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void testImmutableSet2() {
        Configuration cf = Layer.boot().configuration();
        ResolvedModule base = cf.findModule("java.base").get();
        base.reads().add(base);
    }


    /**
     * Invokes parent.resolveRequires(...)
     */
    private Configuration resolveRequires(Configuration parent,
                                          ModuleFinder before,
                                          ModuleFinder after,
                                          String... roots) {
        return parent.resolveRequires(before, after, Set.of(roots));
    }

    private Configuration resolveRequires(Configuration parent,
                                          ModuleFinder before,
                                          String... roots) {
        return resolveRequires(parent, before, ModuleFinder.of(), roots);
    }

    private Configuration resolveRequires(ModuleFinder before,
                                          ModuleFinder after,
                                          String... roots) {
        return resolveRequires(Configuration.empty(), before, after, roots);
    }

    private Configuration resolveRequires(ModuleFinder before,
                                          String... roots) {
        return resolveRequires(Configuration.empty(), before, roots);
    }


    /**
     * Invokes parent.resolveRequiresAndUses(...)
     */
    private Configuration resolveRequiresAndUses(Configuration parent,
                                                 ModuleFinder before,
                                                 ModuleFinder after,
                                                 String... roots) {
        return parent.resolveRequiresAndUses(before, after, Set.of(roots));
    }

    private Configuration resolveRequiresAndUses(Configuration parent,
                                                 ModuleFinder before,
                                                 String... roots) {
        return resolveRequiresAndUses(parent, before, ModuleFinder.of(), roots);
    }

    private Configuration resolveRequiresAndUses(ModuleFinder before,
                                                 ModuleFinder after,
                                                 String... roots) {
        return resolveRequiresAndUses(Configuration.empty(), before, after, roots);
    }

    private Configuration resolveRequiresAndUses(ModuleFinder before,
                                                 String... roots) {
        return resolveRequiresAndUses(Configuration.empty(), before, roots);
    }


    /**
     * Returns {@code true} if the configuration contains module mn1
     * that reads module mn2.
     */
    static boolean reads(Configuration cf, String mn1, String mn2) {
        Optional<ResolvedModule> om1 = cf.findModule(mn1);
        if (!om1.isPresent())
            return false;

        return om1.get().reads().stream()
                .map(ResolvedModule::name)
                .anyMatch(mn2::equals);
    }


}
