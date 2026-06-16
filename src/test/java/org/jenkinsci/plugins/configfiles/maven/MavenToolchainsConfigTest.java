/*
 * The MIT License
 *
 * Copyright 2020, GEBIT Solutions GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.configfiles.maven;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.model.JDK;
import hudson.slaves.DumbSlave;
import hudson.tools.ToolLocationNodeProperty;
import jenkins.model.Jenkins;

@WithJenkins
class MavenToolchainsConfigTest {

    @Test
    void withoutJDKSubstitution(JenkinsRule r) throws Exception {
        // Smokes:
        String fileId = "m2toolchains";
        String jdk8Name = "SeparateJDK"; // not referenced in toolchain, so not substituted
        String jdk8Home = "/opt/separate-jdk";

        List<JDK> jdks = new ArrayList<>();
        JDK jdk8 = new JDK(jdk8Name, jdk8Home);
        jdks.add(jdk8);
        Jenkins.get().setJDKs(jdks);

        String toolchainsContents = "<toolchains><toolchain><type>jdk</type><provides><version>1.8</version><id>JDK8</id></provides><configuration><jdkHome>undefined</jdkHome></configuration></toolchain></toolchains>\n";

        GlobalConfigFiles.get().save(new MavenToolchainsConfig(fileId, "m2toolchains", "", toolchainsContents));
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(String.format("node {configFileProvider([configFile(fileId: '%1$s', variable: 'SETTINGS')]) {echo readFile(env.SETTINGS)}}", fileId), true));
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains("provisioning config files", b1);
        r.assertLogContains(String.format("copy managed file [%1$s]", fileId), b1);
        r.assertLogNotContains(jdk8Home, b1);
        r.assertLogContains("WARNING: unable to set jdkHome in generated toolchains.xml file because no toolchains with id '" + jdk8Name + "' are available" , b1);
    }

    @Test
    void withJDKSubstitution(JenkinsRule r) throws Exception {
        // Smokes:
        String fileId = "m2toolchains";
        String jdk8Name = "JDK8"; // referenced in toolchain
        String jdk8Home = "/usr/lib/jvm/java-8-openjdk";

        String jdk11Name = "JDK11"; // not referenced in toolchain
        String jdk11Home = "/usr/lib/jvm/java-11-openjdk";

        List<JDK> jdks = new ArrayList<>();
        JDK jdk8 = new JDK(jdk8Name, jdk8Home);
        JDK jdk11 = new JDK(jdk11Name, jdk11Home);
        jdks.add(jdk8);
        Jenkins.get().setJDKs(jdks);

        String toolchainsContentsTemplate = "<toolchains><toolchain><type>jdk</type><provides><version>1.8</version><id>%1$s</id></provides><configuration><jdkHome>undefined</jdkHome></configuration></toolchain></toolchains>\n";
        String toolchainsContents1 = String.format(toolchainsContentsTemplate, jdk8Name);

        GlobalConfigFiles.get().save(new MavenToolchainsConfig(fileId, "m2toolchains", "", toolchainsContents1));
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(String.format("node {configFileProvider([configFile(fileId: '%1$s', variable: 'SETTINGS')]) {echo readFile(env.SETTINGS)}}", fileId), true));
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains("provisioning config files", b1);
        r.assertLogContains(String.format("copy managed file [%1$s]", fileId), b1);
        r.assertLogContains(jdk8Home, b1);
        r.assertLogNotContains("WARNING: unable to set jdkHome in generated toolchains.xml file", b1);

        jdks.add(jdk11);
        Jenkins.get().setJDKs(jdks);

        WorkflowRun b2 = r.buildAndAssertSuccess(p);
        r.assertLogContains("provisioning config files", b2);
        r.assertLogContains(String.format("copy managed file [%1$s]", fileId), b2);
        r.assertLogContains(jdk8Home, b2);
        r.assertLogContains("WARNING: unable to set jdkHome in generated toolchains.xml file because no toolchains with id '" + jdk11Name + "' are available", b2);
    }

    @Test
    void withUnavailableJDKRemoval(JenkinsRule r) throws Exception {
        testUnavailableJDKRemoval(r, true);
    }

    @Test
    void withoutUnavailableJDKRemoval(JenkinsRule r) throws Exception {
        testUnavailableJDKRemoval(r, false);
    }

    @Test
    void withJDKSubstitutionOnNodeWithOverride(JenkinsRule r) throws Exception {
        // Verify that node-specific tool location overrides are used when generating
        // the toolchains.xml, rather than the globally configured JDK home paths.
        String fileId = "m2toolchains";
        String jdk8Name = "JDK8";
        String globalJdk8Home = "/usr/lib/jvm/java-8-openjdk-global";
        String nodeJdk8Home = "/usr/lib/jvm/java-8-openjdk-node";

        List<JDK> jdks = new ArrayList<>();
        JDK jdk8 = new JDK(jdk8Name, globalJdk8Home);
        jdks.add(jdk8);
        Jenkins.get().setJDKs(jdks);

        // Create an agent with a ToolLocationNodeProperty that overrides the JDK path
        DumbSlave agent = r.createOnlineSlave();
        JDK.DescriptorImpl jdkDescriptor = Jenkins.get().getDescriptorByType(JDK.DescriptorImpl.class);
        agent.getNodeProperties().add(new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(jdkDescriptor, jdk8Name, nodeJdk8Home)));

        String toolchainsContents = String.format(
                "<toolchains><toolchain><type>jdk</type><provides><version>1.8</version><id>%1$s</id></provides><configuration><jdkHome>undefined</jdkHome></configuration></toolchain></toolchains>\n",
                jdk8Name);
        GlobalConfigFiles.get().save(new MavenToolchainsConfig(fileId, "m2toolchains", "", toolchainsContents));

        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        // Run the pipeline on the agent node so that its tool location override is applied
        p.setDefinition(new CpsFlowDefinition(
                String.format("node('%1$s') {configFileProvider([configFile(fileId: '%2$s', variable: 'SETTINGS')]) {echo readFile(env.SETTINGS)}}",
                        agent.getNodeName(), fileId),
                true));
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        // The node-specific override path must appear in the generated toolchains.xml
        r.assertLogContains(nodeJdk8Home, b1);
        // The global (non-overridden) path must NOT appear
        r.assertLogNotContains(globalJdk8Home, b1);
    }

    private void testUnavailableJDKRemoval(JenkinsRule r, boolean remove) throws Exception {
        // Smokes:
        String fileId = "m2toolchains";
        String jdk8Name = "JDK8"; // referenced in toolchains.xml, but certainly does not exist in filesystem
        String jdk8Home = "/some/path/that/does/not/exist";

        String existingJdkName = "ExistingJDK";
        String existingJdkHome = System.getProperty("user.dir");

        List<JDK> jdks = new ArrayList<>();
        JDK jdk8 = new JDK(jdk8Name, jdk8Home);
        JDK existingJdk = new JDK(existingJdkName, existingJdkHome);
        jdks.add(jdk8);
        jdks.add(existingJdk);
        Jenkins.get().setJDKs(jdks);

        String toolchainsContents = String.format("<toolchains><toolchain><type>jdk</type><provides><version>1.8</version><id>JDK8</id></provides><configuration><jdkHome>undefined</jdkHome></configuration></toolchain><toolchain><type>jdk</type><provides><version>1.8</version><id>ExistingJDK</id></provides><configuration><jdkHome>%1$s</jdkHome></configuration></toolchain></toolchains>\n", existingJdkHome);

        MavenToolchainsConfig config = new MavenToolchainsConfig(fileId, "m2toolchains", "", toolchainsContents);
        config.setRemoveUnavailableJdkToolchains(remove);
        GlobalConfigFiles.get().save(config);

        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(String.format("node {configFileProvider([configFile(fileId: '%1$s', variable: 'SETTINGS')]) {echo readFile(env.SETTINGS)}}", fileId), true));
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains("provisioning config files", b1);
        r.assertLogContains(String.format("copy managed file [%1$s]", fileId), b1);
        r.assertLogContains(existingJdkHome, b1);
        if (remove) {
            r.assertLogContains("Removing toolchain from provided toolchains, since jdk does not exist", b1);
        } else {
            r.assertLogNotContains("Removing toolchain from provided toolchains, since jdk does not exist", b1);
        }
    }
}
