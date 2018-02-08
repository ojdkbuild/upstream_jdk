/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.misc
 * @build BasicLayerTest ModuleUtils
 * @compile layertest/Test.java
 * @run testng BasicLayerTest
 * @summary Basic tests for java.lang.reflect.Layer
 */

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Layer;
import java.lang.reflect.LayerInstantiationException;
import java.lang.reflect.Module;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.internal.misc.SharedSecrets;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class BasicLayerTest {

    /**
     * Creates a "non-strict" builder for building a module. This allows the
     * test the create ModuleDescriptor objects that do not require java.base.
     */
    private static ModuleDescriptor.Builder newBuilder(String mn) {
        return SharedSecrets.getJavaLangModuleAccess()
                .newModuleBuilder(mn, false, Set.of());
    }

    /**
     * Exercise Layer.empty()
     */
    public void testEmpty() {
        Layer emptyLayer = Layer.empty();

        assertTrue(emptyLayer.parents().isEmpty());

        assertTrue(emptyLayer.configuration() == Configuration.empty());

        assertTrue(emptyLayer.modules().isEmpty());

        assertFalse(emptyLayer.findModule("java.base").isPresent());

        try {
            emptyLayer.findLoader("java.base");
            assertTrue(false);
        } catch (IllegalArgumentException expected) { }
    }


    /**
     * Exercise Layer.boot()
     */
    public void testBoot() {
        Layer bootLayer = Layer.boot();

        // configuration
        Configuration cf = bootLayer.configuration();
        assertTrue(cf.findModule("java.base").get()
                .reference()
                .descriptor()
                .exports()
                .stream().anyMatch(e -> (e.source().equals("java.lang")
                                         && !e.isQualified())));

        // modules
        Set<Module> modules = bootLayer.modules();
        assertTrue(modules.contains(Object.class.getModule()));
        int count = (int) modules.stream().map(Module::getName).count();
        assertEquals(count, modules.size()); // module names are unique

        // findModule
        Module base = Object.class.getModule();
        assertTrue(bootLayer.findModule("java.base").get() == base);
        assertTrue(base.getLayer() == bootLayer);

        // findLoader
        assertTrue(bootLayer.findLoader("java.base") == null);

        // parents
        assertTrue(bootLayer.parents().size() == 1);
        assertTrue(bootLayer.parents().get(0) == Layer.empty());
    }


    /**
     * Exercise Layer defineModules, created with empty layer as parent
     */
    public void testLayerOnEmpty() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .exports("p1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("m3")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2, descriptor3);

        Configuration cf = resolve(finder, "m1");

        // map each module to its own class loader for this test
        ClassLoader loader1 = new ClassLoader() { };
        ClassLoader loader2 = new ClassLoader() { };
        ClassLoader loader3 = new ClassLoader() { };
        Map<String, ClassLoader> map = new HashMap<>();
        map.put("m1", loader1);
        map.put("m2", loader2);
        map.put("m3", loader3);

        Layer layer = Layer.empty().defineModules(cf, map::get);

        // configuration
        assertTrue(layer.configuration() == cf);
        assertTrue(layer.configuration().modules().size() == 3);

        // modules
        Set<Module> modules = layer.modules();
        assertTrue(modules.size() == 3);
        Set<String> names = modules.stream()
            .map(Module::getName)
            .collect(Collectors.toSet());
        assertTrue(names.contains("m1"));
        assertTrue(names.contains("m2"));
        assertTrue(names.contains("m3"));

        // findModule
        Module m1 = layer.findModule("m1").get();
        Module m2 = layer.findModule("m2").get();
        Module m3 = layer.findModule("m3").get();
        assertEquals(m1.getName(), "m1");
        assertEquals(m2.getName(), "m2");
        assertEquals(m3.getName(), "m3");
        assertTrue(m1.getDescriptor() == descriptor1);
        assertTrue(m2.getDescriptor() == descriptor2);
        assertTrue(m3.getDescriptor() == descriptor3);
        assertTrue(m1.getLayer() == layer);
        assertTrue(m2.getLayer() == layer);
        assertTrue(m3.getLayer() == layer);
        assertTrue(modules.contains(m1));
        assertTrue(modules.contains(m2));
        assertTrue(modules.contains(m3));
        assertFalse(layer.findModule("godot").isPresent());

        // findLoader
        assertTrue(layer.findLoader("m1") == loader1);
        assertTrue(layer.findLoader("m2") == loader2);
        assertTrue(layer.findLoader("m3") == loader3);
        try {
            ClassLoader loader = layer.findLoader("godot");
            assertTrue(false);
        } catch (IllegalArgumentException ignore) { }

        // parents
        assertTrue(layer.parents().size() == 1);
        assertTrue(layer.parents().get(0) == Layer.empty());
    }


    /**
     * Exercise Layer defineModules, created with boot layer as parent
     */
    public void testLayerOnBoot() {
        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("m2")
                .requires("java.base")
                .exports("p1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires("java.base")
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m1");

        ClassLoader loader = new ClassLoader() { };

        Layer layer = Layer.boot().defineModules(cf, mn -> loader);

        // configuration
        assertTrue(layer.configuration() == cf);
        assertTrue(layer.configuration().modules().size() == 2);

        // modules
        Set<Module> modules = layer.modules();
        assertTrue(modules.size() == 2);
        Set<String> names = modules.stream()
            .map(Module::getName)
            .collect(Collectors.toSet());
        assertTrue(names.contains("m1"));
        assertTrue(names.contains("m2"));

        // findModule
        Module m1 = layer.findModule("m1").get();
        Module m2 = layer.findModule("m2").get();
        assertEquals(m1.getName(), "m1");
        assertEquals(m2.getName(), "m2");
        assertTrue(m1.getDescriptor() == descriptor1);
        assertTrue(m2.getDescriptor() == descriptor2);
        assertTrue(m1.getLayer() == layer);
        assertTrue(m2.getLayer() == layer);
        assertTrue(modules.contains(m1));
        assertTrue(modules.contains(m2));
        assertTrue(layer.findModule("java.base").get() == Object.class.getModule());
        assertFalse(layer.findModule("godot").isPresent());

        // findLoader
        assertTrue(layer.findLoader("m1") == loader);
        assertTrue(layer.findLoader("m2") == loader);
        assertTrue(layer.findLoader("java.base") == null);

        // parents
        assertTrue(layer.parents().size() == 1);
        assertTrue(layer.parents().get(0) == Layer.boot());
    }


    /**
     * Exercise Layer defineModules with a configuration of two modules that
     * have the same module-private package.
     */
    public void testPackageContainedInSelfAndOther() {
        ModuleDescriptor descriptor1 =  newBuilder("m1")
                .requires("m2")
                .packages(Set.of("p"))
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .packages(Set.of("p"))
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf = resolve(finder, "m1");
        assertTrue(cf.modules().size() == 2);

        // one loader per module, should be okay
        Layer.empty().defineModules(cf, mn -> new ClassLoader() { });

        // same class loader
        try {
            ClassLoader loader = new ClassLoader() { };
            Layer.empty().defineModules(cf, mn -> loader);
            assertTrue(false);
        } catch (LayerInstantiationException expected) { }
    }


    /**
     * Exercise Layer defineModules with a configuration that is a partitioned
     * graph. The same package is exported in both partitions.
     */
    public void testSameExportInPartitionedGraph() {

        // m1 reads m2, m2 exports p to m1
        ModuleDescriptor descriptor1 =  newBuilder("m1")
                .requires("m2")
                .build();
        ModuleDescriptor descriptor2 =  newBuilder("m2")
                .exports("p", Set.of("m1"))
                .build();

        // m3 reads m4, m4 exports p to m3
        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m4")
                .build();
        ModuleDescriptor descriptor4 = newBuilder("m4")
                .exports("p", Set.of("m3"))
                .build();

        ModuleFinder finder
            = ModuleUtils.finderOf(descriptor1,
                                   descriptor2,
                                   descriptor3,
                                   descriptor4);

        Configuration cf = resolve(finder, "m1", "m3");
        assertTrue(cf.modules().size() == 4);

        // one loader per module
        Layer.empty().defineModules(cf, mn -> new ClassLoader() { });

        // m1 & m2 in one loader, m3 & m4 in another loader
        ClassLoader loader1 = new ClassLoader() { };
        ClassLoader loader2 = new ClassLoader() { };
        Map<String, ClassLoader> map = new HashMap<>();
        map.put("m1", loader1);
        map.put("m2", loader1);
        map.put("m3", loader2);
        map.put("m4", loader2);
        Layer.empty().defineModules(cf, map::get);

        // same loader
        try {
            ClassLoader loader = new ClassLoader() { };
            Layer.empty().defineModules(cf, mn -> loader);
            assertTrue(false);
        } catch (LayerInstantiationException expected) { }
    }


    /**
     * Exercise Layer defineModules with a configuration with a module that
     * contains a package that is the same name as a non-exported package in
     * a parent layer.
     */
    public void testContainsSamePackageAsBootLayer() {

        // check assumption that java.base contains sun.launcher
        ModuleDescriptor base = Object.class.getModule().getDescriptor();
        assertTrue(base.packages().contains("sun.launcher"));

        ModuleDescriptor descriptor = newBuilder("m1")
               .requires("java.base")
               .packages(Set.of("sun.launcher"))
               .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor);

        Configuration parent = Layer.boot().configuration();
        Configuration cf = parent.resolve(finder, ModuleFinder.of(), Set.of("m1"));
        assertTrue(cf.modules().size() == 1);

        ClassLoader loader = new ClassLoader() { };
        Layer layer = Layer.boot().defineModules(cf, mn -> loader);
        assertTrue(layer.modules().size() == 1);
   }


    /**
     * Test layers with implied readability.
     *
     * The test consists of three configurations:
     * - Configuration/layer1: m1, m2 requires transitive m1
     * - Configuration/layer2: m3 requires m1
     */
    public void testImpliedReadabilityWithLayers1() {

        // cf1: m1 and m2, m2 requires transitive m1

        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolve(finder1, "m2");

        ClassLoader cl1 = new ClassLoader() { };
        Layer layer1 = Layer.empty().defineModules(cf1, mn -> cl1);


        // cf2: m3, m3 requires m2

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m3");

        ClassLoader cl2 = new ClassLoader() { };
        Layer layer2 = layer1.defineModules(cf2, mn -> cl2);

        assertTrue(layer1.parents().size() == 1);
        assertTrue(layer1.parents().get(0) == Layer.empty());

        assertTrue(layer2.parents().size() == 1);
        assertTrue(layer2.parents().get(0) == layer1);

        Module m1 = layer2.findModule("m1").get();
        Module m2 = layer2.findModule("m2").get();
        Module m3 = layer2.findModule("m3").get();

        assertTrue(m1.getLayer() == layer1);
        assertTrue(m2.getLayer() == layer1);
        assertTrue(m3.getLayer() == layer2);

        assertTrue(m1.getClassLoader() == cl1);
        assertTrue(m2.getClassLoader() == cl1);
        assertTrue(m3.getClassLoader() == cl2);

        assertTrue(m1.canRead(m1));
        assertFalse(m1.canRead(m2));
        assertFalse(m1.canRead(m3));

        assertTrue(m2.canRead(m1));
        assertTrue(m2.canRead(m2));
        assertFalse(m2.canRead(m3));

        assertTrue(m3.canRead(m1));
        assertTrue(m3.canRead(m2));
        assertTrue(m3.canRead(m3));
    }


    /**
     * Test layers with implied readability.
     *
     * The test consists of three configurations:
     * - Configuration/layer1: m1
     * - Configuration/layer2: m2 requires transitive m3, m3 requires m2
     */
    public void testImpliedReadabilityWithLayers2() {

        // cf1: m1

        ModuleDescriptor descriptor1 = newBuilder("m1").build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder1, "m1");

        ClassLoader cl1 = new ClassLoader() { };
        Layer layer1 = Layer.empty().defineModules(cf1, mn -> cl1);


        // cf2: m2, m3: m2 requires transitive m1, m3 requires m2

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2, descriptor3);

        Configuration cf2 = resolve(cf1, finder2, "m3");

        ClassLoader cl2 = new ClassLoader() { };
        Layer layer2 = layer1.defineModules(cf2, mn -> cl2);

        assertTrue(layer1.parents().size() == 1);
        assertTrue(layer1.parents().get(0) == Layer.empty());

        assertTrue(layer2.parents().size() == 1);
        assertTrue(layer2.parents().get(0) == layer1);

        Module m1 = layer2.findModule("m1").get();
        Module m2 = layer2.findModule("m2").get();
        Module m3 = layer2.findModule("m3").get();

        assertTrue(m1.getLayer() == layer1);
        assertTrue(m2.getLayer() == layer2);
        assertTrue(m3.getLayer() == layer2);

        assertTrue(m1.canRead(m1));
        assertFalse(m1.canRead(m2));
        assertFalse(m1.canRead(m3));

        assertTrue(m2.canRead(m1));
        assertTrue(m2.canRead(m2));
        assertFalse(m2.canRead(m3));

        assertTrue(m3.canRead(m1));
        assertTrue(m3.canRead(m2));
        assertTrue(m3.canRead(m3));
    }


    /**
     * Test layers with implied readability.
     *
     * The test consists of three configurations:
     * - Configuration/layer1: m1
     * - Configuration/layer2: m2 requires transitive m1
     * - Configuration/layer3: m3 requires m1
     */
    public void testImpliedReadabilityWithLayers3() {

        // cf1: m1

        ModuleDescriptor descriptor1 = newBuilder("m1").build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1);

        Configuration cf1 = resolve(finder1, "m1");

        ClassLoader cl1 = new ClassLoader() { };
        Layer layer1 = Layer.empty().defineModules(cf1, mn -> cl1);


        // cf2: m2 requires transitive m1

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor2);

        Configuration cf2 = resolve(cf1, finder2, "m2");

        ClassLoader cl2 = new ClassLoader() { };
        Layer layer2 = layer1.defineModules(cf2, mn -> cl2);


        // cf3: m3 requires m2

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires("m2")
                .build();

        ModuleFinder finder3 = ModuleUtils.finderOf(descriptor3);

        Configuration cf3 = resolve(cf2, finder3, "m3");

        ClassLoader cl3 = new ClassLoader() { };
        Layer layer3 = layer2.defineModules(cf3, mn -> cl3);

        assertTrue(layer1.parents().size() == 1);
        assertTrue(layer1.parents().get(0) == Layer.empty());

        assertTrue(layer2.parents().size() == 1);
        assertTrue(layer2.parents().get(0) == layer1);

        assertTrue(layer3.parents().size() == 1);
        assertTrue(layer3.parents().get(0) == layer2);

        Module m1 = layer3.findModule("m1").get();
        Module m2 = layer3.findModule("m2").get();
        Module m3 = layer3.findModule("m3").get();

        assertTrue(m1.getLayer() == layer1);
        assertTrue(m2.getLayer() == layer2);
        assertTrue(m3.getLayer() == layer3);

        assertTrue(m1.canRead(m1));
        assertFalse(m1.canRead(m2));
        assertFalse(m1.canRead(m3));

        assertTrue(m2.canRead(m1));
        assertTrue(m2.canRead(m2));
        assertFalse(m2.canRead(m3));

        assertTrue(m3.canRead(m1));
        assertTrue(m3.canRead(m2));
        assertTrue(m3.canRead(m3));
    }


    /**
     * Test layers with implied readability.
     *
     * The test consists of two configurations:
     * - Configuration/layer1: m1, m2 requires transitive m1
     * - Configuration/layer2: m3 requires transitive m2, m4 requires m3
     */
    public void testImpliedReadabilityWithLayers4() {

        // cf1: m1, m2 requires transitive m1

        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleDescriptor descriptor2 = newBuilder("m2")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m1")
                .build();

        ModuleFinder finder1 = ModuleUtils.finderOf(descriptor1, descriptor2);

        Configuration cf1 = resolve(finder1, "m2");

        ClassLoader cl1 = new ClassLoader() { };
        Layer layer1 = Layer.empty().defineModules(cf1, mn -> cl1);


        // cf2: m3 requires transitive m2, m4 requires m3

        ModuleDescriptor descriptor3 = newBuilder("m3")
                .requires(Set.of(Requires.Modifier.TRANSITIVE), "m2")
                .build();

        ModuleDescriptor descriptor4 = newBuilder("m4")
                .requires("m3")
                .build();


        ModuleFinder finder2 = ModuleUtils.finderOf(descriptor3, descriptor4);

        Configuration cf2 = resolve(cf1, finder2, "m3", "m4");

        ClassLoader cl2 = new ClassLoader() { };
        Layer layer2 = layer1.defineModules(cf2, mn -> cl2);

        assertTrue(layer1.parents().size() == 1);
        assertTrue(layer1.parents().get(0) == Layer.empty());

        assertTrue(layer2.parents().size() == 1);
        assertTrue(layer2.parents().get(0) == layer1);

        Module m1 = layer2.findModule("m1").get();
        Module m2 = layer2.findModule("m2").get();
        Module m3 = layer2.findModule("m3").get();
        Module m4 = layer2.findModule("m4").get();

        assertTrue(m1.getLayer() == layer1);
        assertTrue(m2.getLayer() == layer1);
        assertTrue(m3.getLayer() == layer2);
        assertTrue(m4.getLayer() == layer2);

        assertTrue(m1.canRead(m1));
        assertFalse(m1.canRead(m2));
        assertFalse(m1.canRead(m3));
        assertFalse(m1.canRead(m4));

        assertTrue(m2.canRead(m1));
        assertTrue(m2.canRead(m2));
        assertFalse(m1.canRead(m3));
        assertFalse(m1.canRead(m4));

        assertTrue(m3.canRead(m1));
        assertTrue(m3.canRead(m2));
        assertTrue(m3.canRead(m3));
        assertFalse(m3.canRead(m4));

        assertTrue(m4.canRead(m1));
        assertTrue(m4.canRead(m2));
        assertTrue(m4.canRead(m3));
        assertTrue(m4.canRead(m4));
    }


    /**
     * Attempt to use Layer defineModules to create a layer with a module
     * defined to a class loader that already has a module of the same name
     * defined to the class loader.
     */
    @Test(expectedExceptions = { LayerInstantiationException.class })
    public void testModuleAlreadyDefinedToLoader() {

        ModuleDescriptor md = newBuilder("m")
                .requires("java.base")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(md);

        Configuration parent = Layer.boot().configuration();

        Configuration cf = parent.resolve(finder, ModuleFinder.of(), Set.of("m"));

        ClassLoader loader = new ClassLoader() { };

        Layer.boot().defineModules(cf, mn -> loader);

        // should throw LayerInstantiationException as m1 already defined to loader
        Layer.boot().defineModules(cf, mn -> loader);

    }


    /**
     * Attempt to use Layer defineModules to create a Layer with a module
     * containing package {@code p} where the class loader already has a module
     * defined to it containing package {@code p}.
     */
    @Test(expectedExceptions = { LayerInstantiationException.class })
    public void testPackageAlreadyInNamedModule() {

        ModuleDescriptor md1 = newBuilder("m1")
                .packages(Set.of("p"))
                .requires("java.base")
                .build();

        ModuleDescriptor md2 = newBuilder("m2")
                .packages(Set.of("p"))
                .requires("java.base")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(md1, md2);

        ClassLoader loader = new ClassLoader() { };

        // define m1 containing package p to class loader

        Configuration parent = Layer.boot().configuration();

        Configuration cf1 = parent.resolve(finder, ModuleFinder.of(), Set.of("m1"));

        Layer layer1 = Layer.boot().defineModules(cf1, mn -> loader);

        // attempt to define m2 containing package p to class loader

        Configuration cf2 = parent.resolve(finder, ModuleFinder.of(), Set.of("m2"));

        // should throw exception because p already in m1
        Layer layer2 = Layer.boot().defineModules(cf2, mn -> loader);

    }


    /**
     * Attempt to use Layer defineModules to create a Layer with a module
     * containing a package in which a type is already loaded by the class
     * loader.
     */
    @Test(expectedExceptions = { LayerInstantiationException.class })
    public void testPackageAlreadyInUnnamedModule() throws Exception {

        Class<?> c = layertest.Test.class;
        assertFalse(c.getModule().isNamed());  // in unnamed module

        ModuleDescriptor md = newBuilder("m")
                .packages(Set.of(c.getPackageName()))
                .requires("java.base")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(md);

        Configuration parent = Layer.boot().configuration();
        Configuration cf = parent.resolve(finder, ModuleFinder.of(), Set.of("m"));

        Layer.boot().defineModules(cf, mn -> c.getClassLoader());
    }


    /**
     * Attempt to create a Layer with a module named "java.base".
     */
    public void testLayerWithJavaBase() {
        ModuleDescriptor descriptor = newBuilder("java.base")
                .exports("java.lang")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor);

        Configuration cf = Layer.boot()
            .configuration()
            .resolve(finder, ModuleFinder.of(), Set.of("java.base"));
        assertTrue(cf.modules().size() == 1);

        ClassLoader scl = ClassLoader.getSystemClassLoader();

        try {
            Layer.boot().defineModules(cf, mn -> new ClassLoader() { });
            assertTrue(false);
        } catch (LayerInstantiationException e) { }

        try {
            Layer.boot().defineModulesWithOneLoader(cf, scl);
            assertTrue(false);
        } catch (LayerInstantiationException e) { }

        try {
            Layer.boot().defineModulesWithManyLoaders(cf, scl);
            assertTrue(false);
        } catch (LayerInstantiationException e) { }
    }


    /**
     * Attempt to create a Layer with a module containing a "java." package.
     * This should only be allowed when the module is defined to the platform
     * class loader.
     */
    @Test(enabled = false)
    public void testLayerWithJavaPackage() {
        ModuleDescriptor descriptor = newBuilder("foo")
                .packages(Set.of("java.foo"))
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor);

        Configuration cf = Layer.boot()
                .configuration()
                .resolve(finder, ModuleFinder.of(), Set.of("foo"));
        assertTrue(cf.modules().size() == 1);

        ClassLoader pcl = ClassLoader.getPlatformClassLoader();
        ClassLoader scl = ClassLoader.getSystemClassLoader();

        try {
            Layer.boot().defineModules(cf, mn -> new ClassLoader() { });
            assertTrue(false);
        } catch (LayerInstantiationException e) { }

        try {
            Layer.boot().defineModulesWithOneLoader(cf, scl);
            assertTrue(false);
        } catch (LayerInstantiationException e) { }

        try {
            Layer.boot().defineModulesWithManyLoaders(cf, scl);
            assertTrue(false);
        } catch (LayerInstantiationException e) { }

        // create layer with module defined to platform class loader
        Layer layer = Layer.boot().defineModules(cf, mn -> pcl);
        Optional<Module> om = layer.findModule("foo");
        assertTrue(om.isPresent());
        Module foo = om.get();
        assertTrue(foo.getClassLoader() == pcl);
        assertTrue(foo.getPackages().length == 1);
        assertTrue(foo.getPackages()[0].equals("java.foo"));
    }


    /**
     * Attempt to create a Layer with a module defined to the boot loader
     */
    @Test(expectedExceptions = { LayerInstantiationException.class })
    public void testLayerWithBootLoader() {
        ModuleDescriptor descriptor = newBuilder("m1")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor);

        Configuration cf = Layer.boot()
            .configuration()
            .resolve(finder, ModuleFinder.of(), Set.of("m1"));
        assertTrue(cf.modules().size() == 1);

        Layer.boot().defineModules(cf, mn -> null );
    }


    /**
     * Parent of configuration != configuration of parent Layer
     */
    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testIncorrectParent1() {

        ModuleDescriptor descriptor1 = newBuilder("m1")
                .requires("java.base")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration parent = Layer.boot().configuration();
        Configuration cf = parent.resolve(finder, ModuleFinder.of(), Set.of("m1"));

        ClassLoader loader = new ClassLoader() { };
        Layer.empty().defineModules(cf, mn -> loader);
    }


    /**
     * Parent of configuration != configuration of parent Layer
     */
    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testIncorrectParent2() {

        ModuleDescriptor descriptor1 = newBuilder("m1")
                .build();

        ModuleFinder finder = ModuleUtils.finderOf(descriptor1);

        Configuration cf = resolve(finder, "m1");

        ClassLoader loader = new ClassLoader() { };
        Layer.boot().defineModules(cf, mn -> loader);
    }


    // null handling

    @Test(expectedExceptions = { NullPointerException.class })
    public void testCreateWithNull1() {
        ClassLoader loader = new ClassLoader() { };
        Layer.empty().defineModules(null, mn -> loader);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testCreateWithNull2() {
        Configuration cf = resolve(Layer.boot().configuration(), ModuleFinder.of());
        Layer.boot().defineModules(cf, null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testCreateWithNull3() {
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        Layer.empty().defineModulesWithOneLoader(null, scl);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testCreateWithNull4() {
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        Layer.empty().defineModulesWithManyLoaders(null, scl);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testFindModuleWithNull() {
        Layer.boot().findModule(null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testFindLoaderWithNull() {
        Layer.boot().findLoader(null);
    }


    // immutable sets

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void testImmutableSet() {
        Module base = Object.class.getModule();
        Layer.boot().modules().add(base);
    }


    /**
     * Resolve the given modules, by name, and returns the resulting
     * Configuration.
     */
    private static Configuration resolve(Configuration cf,
                                         ModuleFinder finder,
                                         String... roots) {
        return cf.resolve(finder, ModuleFinder.of(), Set.of(roots));
    }

    private static Configuration resolve(ModuleFinder finder,
                                         String... roots) {
        return resolve(Configuration.empty(), finder, roots);
    }
}
