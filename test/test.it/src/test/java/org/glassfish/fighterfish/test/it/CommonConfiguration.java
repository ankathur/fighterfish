/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */


package org.glassfish.fighterfish.test.it;

import org.glassfish.fighterfish.test.util.PropertiesUtil;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.osgi.framework.Constants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.LibraryOptions.junitBundles;
import static org.ops4j.pax.exam.OptionUtils.*;

/**
 * Provides common PAX configuration for all tests.
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
public class CommonConfiguration {
//    static {
//        System.setProperty( "java.protocol.handler.pkgs", "org.ops4j.pax.url" );
//    }
    private File gfHome = new File(System.getProperty("com.sun.aas.installRoot"));
    private String platform = System.getProperty("GlassFish_Platform");

    protected long TIMEOUT = Long.getLong("fighterfish.test.timeout", 30000); // in ms
    
    protected Logger logger = Logger.getLogger(getClass().getPackage().getName());

    public CommonConfiguration() {
        checkAndSetDefaultProperties();
    }

    @Configuration
    public Option[] configure() throws IOException {
        return combine(frameworkConfiguration(), provisioningBundles());
    }

    private Option[] provisioningBundles() {
        // For various timing issues that come in when we rely on fileinstall to provision autostart bundles,
        // we disable fileinstall for autostart dir. We provision them diretcly.
        // see autostartBundles() for more details.
        return combine(options(bundle(new File(gfHome, "modules/glassfish.jar").toURI().toString()),
                mavenBundle().groupId("org.junit").artifactId("com.springsource.org.junit").version("4.8.1"),
                mavenBundle().groupId("org.glassfish.fighterfish").artifactId("test.util").version("1.0.0-SNAPSHOT"),
                mavenBundle().groupId("org.ops4j.pax.url").artifactId("pax-url-mvn").version("1.2.5"),
                systemProperty("pax-exam.framework.shutdown.timeout").value(System.getProperty("pax-exam.framework.shutdown.timeout"))
                ), autostartBundles()
        );
    }

    /**
     * For various timing issues that come in when we rely on fileinstall to provision autostart bundles,
     * we disable fileinstall for autostart dir. We provision them diretcly. Following are a few of the timing
     * issues observed so far:
     * 1. By the time fileinstall installs autostart modules, pax exam is sometimes finished running the test.
     * If we deploy test bundles which depend on osgi-cdi api or any api from autostart dir, then those tests fail to resolve,
     * 2. If osgi-jpa bundle is not installed before running JPA tests, JPA test bundles don't get enhanced.
     * Keeping all these in mind, we are provisioning all autostart bundles ahead of time.
     * We also have to configure fileinstall to monitor autodeploy/bundles only, else it tries to update
     * all bundles in autostart and that interferes with the tests.
     * @return
     */
    private Option[] autostartBundles() {
        List<Option> options = new ArrayList<Option>();
        for (File f : new File(gfHome, "modules/autostart").listFiles()) {
            if (f.isFile() && f.getName().endsWith(".jar")) {
                options.add(bundle(f.toURI().toString()));
            }
        }

        // Because we provision autostart bundles this way, we also have to disable fileinstall monitoring autostart bundles.
        options.add(systemProperty("felix.fileinstall.dir").value(System.getProperty("com.sun.aas.instanceRoot") + "/autodeploy/bundles/"));
        options.add(systemProperty("felix.fileinstall.log.level").value("3"));
        // rest all are read from domain.xml
        
        return options.toArray(new Option[options.size()]);
    }

    private Option[] frameworkConfiguration() throws IOException {
        List<Option> options = new ArrayList<Option>();
        Properties properties = readAndCustomizeFrameworkConfiguration();

        // parse boot delegation property and set it as a bootdelegation option as pax-exam's native test container implementation
        // does not use bootdelegation system property.
        for (String p : properties.getProperty(Constants.FRAMEWORK_BOOTDELEGATION, "").split(",")) {
            System.out.println("Boot delegation pkg = " + p);
            options.add(bootDelegationPackage(p.trim()));
        }
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (entry.getKey().equals(Constants.FRAMEWORK_BOOTDELEGATION)) continue; // already handled above
            options.add(systemProperty((String) entry.getKey()).value((String) entry.getValue()));
        }
        return options.toArray(new Option[options.size()]);
    }

    private void checkAndSetDefaultProperties() {
        if (System.getProperty("com.sun.aas.installRootURI") == null) {
            System.setProperty("com.sun.aas.installRootURI", gfHome.toURI().toString());
        }
        if (System.getProperty("com.sun.aas.instanceRoot") == null) {
            System.setProperty("com.sun.aas.instanceRoot",
                    new File(gfHome, "domains/domain1").getAbsolutePath());
        }
        if (System.getProperty("com.sun.aas.instanceRootURI") == null) {
            System.setProperty("com.sun.aas.instanceRootURI",
                    new File(System.getProperty("com.sun.aas.instanceRoot")).toURI().toString());
        }
    }

    private Properties readAndCustomizeFrameworkConfiguration() throws IOException {
        Properties properties = new Properties();
        URI uri;
        if (System.getProperty("glassfish.osgi-configuration")!= null) {
            uri = URI.create(System.getProperty("glassfish.osgi-configuration"));
        }
        if ("Felix".equals(platform)) {
            uri = new File(gfHome, "osgi/felix/conf/config.properties").toURI();
        } else if ("Equinox".equals(platform)) {
            uri = new File(gfHome, "osgi/equinox/configuration/config.ini").toURI();
        } else {
            throw new RuntimeException("GlassFish_Platform can only be Felix or Equinox");
        }
        logger.logp(Level.INFO, "CommonConfiguration", "readAndCustomizeFrameworkConfiguration", "uri = {0}", new Object[]{uri});
        InputStream is = uri.toURL().openStream();
        try {
            properties.load(is);
        } finally {
            is.close();
        }

        // Because the provisioning bundles are not uninstalled and because they are marked persistently started,
        // we use a different cache location.
        final String defaultLocation = properties.getProperty(Constants.FRAMEWORK_STORAGE) + "/pax-exam/";
        final String cacheLocation = System.getProperty(Constants.FRAMEWORK_STORAGE, defaultLocation);
        System.out.println("OSGi persistence store location = " + cacheLocation);
        properties.put(Constants.FRAMEWORK_STORAGE, cacheLocation);

        // Because some of our tests use JPA in Java SE mode, we have to set this property to enable the same.
        properties.put("org.glassfish.osgjpa.extension.useHybridPersistenceProviderResolver", "true");

        // GlassFish is ocnfigured to get bootstrap APIs from system bundle so that the main program can control GlassFish's life cycle.
        // But the bootstrap bundle's activator, GlassFishMainActivator, does not shutdown GlassFishRuntime in its stop(). As a result,
        // when the bootstrap bundle is again started in the same VM, it gets an exception sayng "Already bootstrapped."
        // To avoid this, we don't configure system bundle to export bootstrap APIs. They are then obtained from bootstrap bundle
        // itself and test bundles also import from that bundle.
        String oldValue = (String) properties.get("extra-system-packages");
        final String newValue = "${jre-${java.specification.version}} ${internal-jdk-pkgs-for-gf}";
        System.out.println("Replacing extra-system-properties from [" + oldValue + "] to [" + newValue + "]" );
        properties.put("extra-system-packages", newValue);

        // Now substitute properties
        PropertiesUtil.substVars(properties);
        return properties;
    }
}
