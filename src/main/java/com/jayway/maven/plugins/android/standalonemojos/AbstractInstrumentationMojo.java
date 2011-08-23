/*
 * Copyright (C) 2009-2011 Jayway AB
 * Copyright (C) 2007-2008 JVending Masa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.maven.plugins.android.standalonemojos;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.jayway.maven.plugins.android.AbstractIntegrationtestMojo;
import com.jayway.maven.plugins.android.DeviceCallback;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;


import static com.android.ddmlib.testrunner.ITestRunListener.TestFailure.ERROR;

/**
 * AbstractInstrumentationMojo implements running the instrumentation
 * tests.
 *
 * @author hugo.josefson@jayway.com
 * @author Manfred Moser <manfred@simpligility.com>
 */
public abstract class AbstractInstrumentationMojo extends AbstractIntegrationtestMojo {
    /**
     * Package name of the apk we wish to instrument. If not specified, it is inferred from
     * <code>AndroidManifest.xml</code>.
     *
     * @optional
     * @parameter expression="${android.test.instrumentationPackage}
     */
    private String instrumentationPackage;

    /**
     * Class name of test runner. If not specified, it is inferred from <code>AndroidManifest.xml</code>.
     *
     * @optional
     * @parameter expression="${android.test.instrumentationRunner}"
     */
    private String instrumentationRunner;

    /**
     * Enable debug causing the test runner to wait until debugger is
     * connected with the Android debug bridge (adb).
     *
     * @optional
     * @parameter default-value=false expression="${android.test.debug}"
     */
    private boolean testDebug;


    /**
     * Enable or disable code coverage for this instrumentation test
     * run.
     *
     * @optional
     * @parameter default-value=false expression="${android.test.coverage}"
     */
    private boolean testCoverage;

    /**
     * Enable this flag to run a log only and not execute the tests.
     *
     * @optional
     * @parameter default-value=false expression="${android.test.logonly}"
     */
    private boolean testLogOnly;

    /**
     * If specified only execute tests of certain size as defined by
     * the Android instrumentation testing SmallTest, MediumTest and
     * LargeTest annotations. Use "small", "medium" or "large" as values.
     *
     * @see com.android.ddmlib.testrunner.IRemoteAndroidTestRunner
     *
     * @optional
     * @parameter expression="${android.test.testsize}"
     */
    private String testSize;

    /**
     * Create a junit xml format compatible output file containing
     * the test results for each device the instrumentation tests run
     * on.
     * <br /><br />
     * The files are stored in target/surefire-reports and named TEST-deviceid.xml.
     * The deviceid for an emulator is deviceSerialNumber_avdName. The
     * serial number is commonly emulator-5554 for the first emulator
     * started with numbers increasing.
     * The deviceid for an actual devices is
     * deviceSerialNumber_manufacturer_model.
     * <br /><br />
     * The file contains system properties from the system running
     * the Maven Android Plugin (JVM) and device properties from the
     * device/emulator the tests are running on.
     * <br /><br />
     * The file contains a single TestSuite for all tests and a
     * TestCase for each test method. Errors and failures are logged
     * in the file and the system log with full stack traces and other
     * details available.
     *
     * @optional
     * @parameter default-value=true expression="${android.test.createreport}"
     */
    private boolean testCreateReport;

    private boolean testClassesExists;
    private boolean testPackagesExists;
    private String testPackages;
    private String[] testClassesArray;


    protected void instrument() throws MojoExecutionException, MojoFailureException {
        if (instrumentationPackage == null) {
            instrumentationPackage = extractPackageNameFromAndroidManifest(androidManifestFile);
        }

        if (instrumentationRunner == null) {
            instrumentationRunner = extractInstrumentationRunnerFromAndroidManifest(androidManifestFile);
        }

        // only run Tests in specific package
        testPackages = buildTestPackagesString();
        testPackagesExists = StringUtils.isNotBlank(testPackages);

        if (testClasses != null) {
            testClassesArray = (String[]) testClasses.toArray();
            testClassesExists = testClassesArray.length > 0;
        } else {
            testClassesExists = false;
        }

        if(testClassesExists && testPackagesExists) {
            // if both testPackages and testClasses are specified --> ERROR
            throw new MojoFailureException("testPackages and testClasses are mutual exclusive. They cannot be specified at the same time. " +
                "Please specify either testPackages or testClasses! For details, see http://developer.android.com/guide/developing/testing/testing_otheride.html");
        }

        doWithDevices(new DeviceCallback() {
            public void doWithDevice(final IDevice device) throws MojoExecutionException, MojoFailureException {
                RemoteAndroidTestRunner remoteAndroidTestRunner =
                    new RemoteAndroidTestRunner(instrumentationPackage, instrumentationRunner, device);

                if(testPackagesExists) {
                    remoteAndroidTestRunner.setTestPackageName(testPackages);
                    getLog().info("Running tests for specified test packages: " + testPackages);
                }

                if(testClassesExists) {
                    remoteAndroidTestRunner.setClassNames(testClassesArray);
                    getLog().info("Running tests for specified test " +
                        "classes/methods: " + Arrays.toString(testClassesArray));
                }

                remoteAndroidTestRunner.setDebug(testDebug);
                remoteAndroidTestRunner.setCoverage(testCoverage);
                remoteAndroidTestRunner.setLogOnly(testLogOnly);

                if (StringUtils.isNotBlank(testSize)) {
                    IRemoteAndroidTestRunner.TestSize validSize =
                        IRemoteAndroidTestRunner.TestSize.getTestSize(testSize);
                    remoteAndroidTestRunner.setTestSize(validSize);
                }

                getLog().info("Running instrumentation tests in " + instrumentationPackage + " on " +
                    device.getSerialNumber() + " (avdName=" + device.getAvdName() + ")");
                try {
                    AndroidTestRunListener testRunListener = new AndroidTestRunListener(project, device);
                    remoteAndroidTestRunner.run(testRunListener);
                    if (testRunListener.hasFailuresOrErrors()) {
                        throw new MojoFailureException("Tests failed on device.");
                    }
                    if (testRunListener.threwException()) {
                        throw new MojoFailureException(testRunListener.getExceptionMessages());
                    }
                } catch (TimeoutException e) {
                    throw new MojoExecutionException("timeout", e);
                } catch (AdbCommandRejectedException e) {
                    throw new MojoExecutionException("adb command rejected", e);
                } catch (ShellCommandUnresponsiveException e) {
                    throw new MojoExecutionException("shell command " +
                        "unresponsive", e);
                } catch (IOException e) {
                    throw new MojoExecutionException("IO problem", e);
                }
            }
        });
    }

    /**
     * AndroidTestRunListener produces a nice output for the log for the test
     * run as well as an xml file compatible with the junit xml report file
     * format understood by many tools.
     *
     * It will do so for each device/emulator the tests run on.
     */
    private class AndroidTestRunListener implements ITestRunListener {
        /** the indent used in the log to group items that belong together visually **/
        private static final String INDENT = "  ";

        /**
         * Junit report schema documentation is sparse. Here are some hints
         * @see "http://mail-archives.apache.org/mod_mbox/ant-dev/200902.mbox/%3Cdffc72020902241548l4316d645w2e98caf5f0aac770@mail.gmail.com%3E"
         * @see "http://junitpdfreport.sourceforge.net/managedcontent/PdfTranslation"
         */
        private static final String TAG_TESTSUITES = "testsuites";

        private static final String TAG_TESTSUITE = "testsuite";
        private static final String ATTR_TESTSUITE_ERRORS = "errors";
        private static final String ATTR_TESTSUITE_FAILURES = "failures";
        private static final String ATTR_TESTSUITE_HOSTNAME = "hostname";
        private static final String ATTR_TESTSUITE_NAME = "name";
        private static final String ATTR_TESTSUITE_TESTS = "tests";
        private static final String ATTR_TESTSUITE_TIME = "time";
        private static final String ATTR_TESTSUITE_TIMESTAMP = "timestamp";

        private static final String TAG_PROPERTIES = "properties";
        private static final String TAG_PROPERTY = "property";
        private static final String ATTR_PROPERTY_NAME = "name";
        private static final String ATTR_PROPERTY_VALUE = "value";

        private static final String TAG_TESTCASE = "testcase";
        private static final String ATTR_TESTCASE_NAME = "name";
        private static final String ATTR_TESTCASE_CLASSNAME = "classname";
        private static final String ATTR_TESTCASE_TIME = "time";

        private static final String TAG_ERROR = "error";
        private static final String TAG_FAILURE = "failure";
        private static final String ATTR_MESSAGE = "message";
        private static final String ATTR_TYPE = "type";


        /** time format for the output of milliseconds in seconds in the xml file **/
        private  final NumberFormat timeFormatter = new DecimalFormat("#0.0000");

        private int testCount = 0;
        private int testFailureCount = 0;
        private int testErrorCount = 0;

        private final MavenProject project;
        /** the emulator or device we are running the tests on **/
        private final IDevice device;


        // junit xml report related fields
        private Document junitReport;
        private Node testSuiteNode;

        /** node for the current test case for junit report */
        private Node currentTestCaseNode;
        /** start time of current test case in millis, reset with each test start */
        private long currentTestCaseStartTime;

        // we track if we have problems and then report upstream
        private boolean threwException = false;
        private final StringBuilder exceptionMessages = new StringBuilder();

        public AndroidTestRunListener(MavenProject project, IDevice device) {
            this.project = project;
            this.device = device;
        }

        public void testRunStarted(String runName, int testCount) {
            this.testCount = testCount;
            getLog().info(INDENT + "Run started: " + runName + ", " + testCount + " tests:");

            if (testCreateReport) {
                try {
                    DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
                    DocumentBuilder parser = null;
                    parser = fact.newDocumentBuilder();
                    junitReport = parser.newDocument();

                    Node testSuitesNode = junitReport.createElement(TAG_TESTSUITES);
                    junitReport.appendChild(testSuitesNode);

                    testSuiteNode = junitReport.createElement(TAG_TESTSUITE);
                    NamedNodeMap testSuiteAttributes = testSuiteNode.getAttributes();

                    Attr nameAttr = junitReport.createAttribute(ATTR_TESTSUITE_NAME);
                    nameAttr.setValue(runName);
                    testSuiteAttributes.setNamedItem(nameAttr);

                    Attr hostnameAttr = junitReport.createAttribute(ATTR_TESTSUITE_HOSTNAME);
                    hostnameAttr.setValue(getDeviceIdentifier());
                    testSuiteAttributes.setNamedItem(hostnameAttr);

                    Node propertiesNode = junitReport.createElement(TAG_PROPERTIES);
                    Node propertyNode;
                    NamedNodeMap propertyAttributes;
                    Attr propNameAttr;
                    Attr propValueAttr;
                    for (Map.Entry<Object, Object> systemProperty : System.getProperties().entrySet()) {
                        propertyNode = junitReport.createElement(TAG_PROPERTY);
                        propertyAttributes = propertyNode.getAttributes();

                        propNameAttr = junitReport.createAttribute(ATTR_PROPERTY_NAME);
                        propNameAttr.setValue(systemProperty.getKey().toString());
                        propertyAttributes.setNamedItem(propNameAttr);

                        propValueAttr = junitReport.createAttribute(ATTR_PROPERTY_VALUE);
                        propValueAttr.setValue(systemProperty.getValue().toString());
                        propertyAttributes.setNamedItem(propValueAttr);

                        propertiesNode.appendChild(propertyNode);

                    }
                    Map<String, String> deviceProperties = device.getProperties();
                    for (Map.Entry<String, String> deviceProperty : deviceProperties.entrySet()) {
                        propertyNode = junitReport.createElement(TAG_PROPERTY);
                        propertyAttributes = propertyNode.getAttributes();

                        propNameAttr = junitReport.createAttribute(ATTR_PROPERTY_NAME);
                        propNameAttr.setValue(deviceProperty.getKey());
                        propertyAttributes.setNamedItem(propNameAttr);

                        propValueAttr = junitReport.createAttribute(ATTR_PROPERTY_VALUE);
                        propValueAttr.setValue(deviceProperty.getValue());
                        propertyAttributes.setNamedItem(propValueAttr);

                        propertiesNode.appendChild(propertyNode);
                    }

                    testSuiteNode.appendChild(propertiesNode);

                    testSuitesNode.appendChild(testSuiteNode);

                } catch (ParserConfigurationException e) {
                    threwException = true;
                    exceptionMessages.append("Failed to create document");
                    exceptionMessages.append(e.getMessage());
                }
            }
        }

        public void testStarted(TestIdentifier test) {
           getLog().info(INDENT + INDENT +"Start: " + test.toString());

            if (testCreateReport) {
                // reset start time for each test run
                currentTestCaseStartTime = new Date().getTime();

                currentTestCaseNode = junitReport.createElement(TAG_TESTCASE);
                NamedNodeMap testCaseAttributes = currentTestCaseNode.getAttributes();

                Attr classAttr = junitReport.createAttribute(ATTR_TESTCASE_CLASSNAME);
                classAttr.setValue(test.getClassName());
                testCaseAttributes.setNamedItem(classAttr);

                Attr methodAttr = junitReport.createAttribute(ATTR_TESTCASE_NAME);
                methodAttr.setValue(test.getTestName());
                testCaseAttributes.setNamedItem(methodAttr);
            }
        }

        public void testFailed(TestFailure status, TestIdentifier test, String trace) {
            if (status==ERROR) {
                ++testErrorCount;
            } else {
                ++testFailureCount;
            }
            getLog().info(INDENT + INDENT + status.name() + ":" + test.toString());
            getLog().info(INDENT + INDENT + trace);

            if (testCreateReport) {
                Node errorFailureNode;
                NamedNodeMap errorfailureAttributes;
                if (status == ERROR) {
                    errorFailureNode = junitReport.createElement(TAG_ERROR);
                    errorfailureAttributes = errorFailureNode.getAttributes();
                } else {
                    errorFailureNode = junitReport.createElement(TAG_FAILURE);
                    errorfailureAttributes= errorFailureNode.getAttributes();
                }

                errorFailureNode.setTextContent(trace);

                Attr msgAttr = junitReport.createAttribute(ATTR_MESSAGE);
                msgAttr.setValue(parseForMessage(trace));
                errorfailureAttributes.setNamedItem(msgAttr);

                Attr typeAttr = junitReport.createAttribute(ATTR_TYPE);
                typeAttr.setValue(parseForException(trace));
                errorfailureAttributes.setNamedItem(typeAttr);

                currentTestCaseNode.appendChild(errorFailureNode);
            }
        }

        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            getLog().info( INDENT + INDENT +"End: " + test.toString());
            logMetrics(testMetrics);

            if (testCreateReport) {
                testSuiteNode.appendChild(currentTestCaseNode);
                NamedNodeMap testCaseAttributes = currentTestCaseNode.getAttributes();

                Attr timeAttr = junitReport.createAttribute(ATTR_TESTCASE_TIME);

                long now = new Date().getTime();
                double seconds = (now - currentTestCaseStartTime)/1000.0;
                timeAttr.setValue(timeFormatter.format(seconds));
                testCaseAttributes.setNamedItem(timeAttr);
            }
        }

        public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
            getLog().info(INDENT +"Run ended: " + elapsedTime + " ms");
            if (hasFailuresOrErrors()) {
                getLog().error(INDENT + "FAILURES!!!");
            }
            getLog().info(INDENT + "Tests run: " + testCount + ",  Failures: "
                    + testFailureCount + ",  Errors: " + testErrorCount);
            if (testCreateReport) {
                NamedNodeMap testSuiteAttributes = testSuiteNode.getAttributes();

                Attr testCountAttr = junitReport.createAttribute(ATTR_TESTSUITE_TESTS);
                testCountAttr.setValue(Integer.toString(testCount));
                testSuiteAttributes.setNamedItem(testCountAttr);

                Attr testFailuresAttr = junitReport.createAttribute(ATTR_TESTSUITE_FAILURES);
                testFailuresAttr.setValue(Integer.toString(testFailureCount));
                testSuiteAttributes.setNamedItem(testFailuresAttr);

                Attr testErrorsAttr = junitReport.createAttribute(ATTR_TESTSUITE_ERRORS);
                testErrorsAttr.setValue(Integer.toString(testErrorCount));
                testSuiteAttributes.setNamedItem(testErrorsAttr);

                Attr timeAttr = junitReport.createAttribute(ATTR_TESTSUITE_TIME);
                timeAttr.setValue(timeFormatter.format(elapsedTime / 1000.0));
                testSuiteAttributes.setNamedItem(timeAttr);

                Attr timeStampAttr = junitReport.createAttribute(ATTR_TESTSUITE_TIMESTAMP);
                timeStampAttr.setValue(new Date().toString());
                testSuiteAttributes.setNamedItem(timeStampAttr);
            }

            logMetrics(runMetrics);

            if (testCreateReport) {
                writeJunitReportToFile();
            }
        }

        public void testRunFailed(String errorMessage) {
            getLog().info(INDENT +"Run failed: " + errorMessage);
        }

        public void testRunStopped(long elapsedTime) {
            getLog().info(INDENT +"Run stopped:" + elapsedTime);
        }


        /**
         * Parse a trace string for the message in it. Assumes that the message is located after ":" and before
         * "\r\n".
         * @param trace
         * @return message or empty string
         */
        private String parseForMessage(String trace) {
            if (StringUtils.isNotBlank(trace)) {
                String newline = "\r\n";
                boolean hasMessage = trace.indexOf(newline) > 0;
                if (hasMessage) {
                    return trace.substring(trace.indexOf(":") + 2, trace.indexOf("\r\n"));
                } else {
                    return StringUtils.EMPTY;
                }
            } else {
                return StringUtils.EMPTY;
            }
        }

        /**
         * Parse a trace string for the exception class. Assumes that it is the start of the trace and ends at the first
         * ":".
         * @param trace
         * @return  Exception class as string or empty string
         */
        private String parseForException(String trace) {
            if (StringUtils.isNotBlank(trace)) {
                return trace.substring(0, trace.indexOf(":"));
            } else {
                return StringUtils.EMPTY;
            }
        }

        /**
         * Write the junit report xml file.
         */
        private void writeJunitReportToFile() {
            TransformerFactory xfactory = TransformerFactory.newInstance();
            Transformer xformer = null;
            try {
                xformer = xfactory.newTransformer();
            } catch (TransformerConfigurationException e) {
                e.printStackTrace();
            }
            Source source = new DOMSource(junitReport);

            FileWriter writer = null;
            try {
                String directory =  new StringBuilder()
                        .append(project.getBuild().getDirectory())
                        .append("/surefire-reports")
                        .toString();

                FileUtils.forceMkdir(new File(directory));

                String fileName = new StringBuilder()
                        .append(directory)
                        .append("/TEST-")
                        .append(getDeviceIdentifier())
                        .append(".xml")
                        .toString();
                File reportFile = new File(fileName);
                writer = new FileWriter(reportFile);
                Result result = new StreamResult(writer);

                xformer.transform(source, result);
                getLog().info("Report file written to " + reportFile.getAbsolutePath());
            } catch (IOException e) {
                threwException = true;
                exceptionMessages.append("Failed to write test report file");
                exceptionMessages.append(e.getMessage());
            } catch (TransformerException e) {
                threwException = true;
                exceptionMessages.append("Failed to transform document to write to test report file");
                exceptionMessages.append(e.getMessage());
            } finally {
                IOUtils.closeQuietly(writer);
            }
        }

        /**
         * Log all the metrics out in to key: value lines.
         * @param metrics
         */
        private void logMetrics(Map<String, String> metrics) {
            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                getLog().info(INDENT + INDENT + entry.getKey() + ": "
                    + entry.getValue());
            }
        }

        /**
         * @return if any failures or errors occurred in the test run.
         */
        public boolean hasFailuresOrErrors() {
            return testErrorCount > 0 || testFailureCount > 0;
        }

        /**
         * @return if any exception was thrown during the test run
         * on the build system (not the Android device or emulator)
         */
        public boolean threwException() {
            return threwException;
        }

        /**
         * @return all exception messages thrown during test execution
         * on the test run time (not the Android device or emulator)
         */
        public String getExceptionMessages() {
            return exceptionMessages.toString();
        }

        /**
         * Get a device identifier string. More documentation at the
         * AbstractInstrumentationMojo#testCreateReport javadoc since
         * that is the public documentation.
         *
x         * @return
         */
        private String getDeviceIdentifier() {
            // if any of this logic changes update javadoc for
            // AbstractInstrumentationMojo#testCreateReport
            String SEPARATOR = "_";
            StringBuilder identfier = new StringBuilder()
                    .append(device.getSerialNumber());
            if (device.getAvdName() != null) {
                identfier.append(SEPARATOR).append(device.getAvdName());
            } else {
                String manufacturer = StringUtils.deleteWhitespace(device.getProperty("ro.product.manufacturer"));
                if (StringUtils.isNotBlank(manufacturer)) {
                    identfier.append(SEPARATOR).append(manufacturer);
                }
                String model = StringUtils.deleteWhitespace(device.getProperty("ro.product.model"));
                if (StringUtils.isNotBlank(model)) {
                    identfier.append(SEPARATOR).append(model);
                }
            }
            return identfier.toString();
        }
    }
}
