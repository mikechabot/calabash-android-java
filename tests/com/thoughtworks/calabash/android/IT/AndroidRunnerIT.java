package com.thoughtworks.calabash.android.IT;

import com.thoughtworks.calabash.android.*;
import org.apache.commons.io.FileUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.thoughtworks.calabash.android.IT.AllActionsIT.EMULATOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

//Just run all the test. Can't help with emulator state dependency.
public class AndroidRunnerIT {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private File tempDir;
    private File tempAndroidApkPath;
    private String packageName;
    private AndroidConfiguration configuration;

    @Before
    public void setUp() throws Exception {
        packageName = "com.example.AndroidTestApplication";
        tempDir = TestUtils.createTempDir("TestAndroidApps");
        tempAndroidApkPath = createTempDirWithProj("AndroidTestApplication.apk", tempDir);
        configuration = new AndroidConfiguration();
        configuration.setLogsDirectory(new File("logs"));
    }

    private File createTempDirWithProj(String androidApp, File dir) throws IOException {
        File androidAppPath = new File("tests/resources/" + androidApp);
        File tempAndroidPath = new File(dir, androidApp);
        FileUtils.copyFile(androidAppPath, tempAndroidPath);
        return tempAndroidPath;
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void shouldCreateTestServerApk() throws CalabashException, IOException {
        AndroidRunner androidRunner = new AndroidRunner(tempAndroidApkPath.getAbsolutePath(), this.configuration);
        androidRunner.setup();
        File testServersDir = new File(tempDir, "test_servers");

        assertTrue(testServersDir.exists());
        File[] testServerApk = testServersDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".apk");
            }
        });

        assertEquals(1, testServerApk.length);
    }

    @Ignore("Since the emulator is always running, we can't test this")
    @Test
    public void shouldThrowExceptionIfSerialOrDeviceNotProvided() throws CalabashException {
        expectedException.expect(CalabashException.class);
        expectedException.expectMessage("Could not get the device serial, set the serial or devicename in the AndroidConfiguration");

        AndroidRunner androidRunner = new AndroidRunner(tempAndroidApkPath.getAbsolutePath(), this.configuration);
        androidRunner.setup();
        androidRunner.start();
    }

    @Test
    public void shouldThrowExceptionIfSerialIsGivenWhenNotStarted() throws CalabashException {
        String serial = "emulator-x";
        expectedException.expect(CalabashException.class);
        expectedException.expectMessage(String.format("%s not found in the device list, installation failed", serial));

        configuration.setSerial(serial);
        AndroidRunner androidRunner = new AndroidRunner(tempAndroidApkPath.getAbsolutePath(), configuration);
        androidRunner.setup();
        androidRunner.start();
    }

    @Test
    public void shouldInstallAppOnDeviceWithName() throws CalabashException {
        configuration.setDeviceName("device");
        configuration.setShouldReinstallApp(true);
        AndroidRunner androidRunner = new AndroidRunner(tempAndroidApkPath.getAbsolutePath(), configuration);

        androidRunner.setup();
        AndroidApplication application = androidRunner.start();

        assertTrue(TestUtils.isAppInstalled(packageName, application.getInstalledOnSerial()));
        assertTrue(TestUtils.isMainActivity(application, "MyActivity"));
    }

    @Test
    public void shouldInstallApplicationIfSerialIsProvided() throws CalabashException {
        //note: emulator should be launched with serial 'EMULATOR
        String serial = EMULATOR;
        TestUtils.uninstall(packageName, serial);
        configuration.setSerial(serial);
        AndroidRunner androidRunner = new AndroidRunner(tempAndroidApkPath.getAbsolutePath(), configuration);

        androidRunner.setup();
        AndroidApplication application = androidRunner.start();

        assertTrue(TestUtils.isAppInstalled(packageName, serial));
        assertTrue(TestUtils.isMainActivity(application, "MyActivity"));
    }

    @Test
    public void shouldInstallApplicationAlreadyRunningDevice() throws CalabashException {
        //note: emulator with name 'device' should be launched with serial 'EMULATOR'
        String device = "device";
        String serial = EMULATOR;
        TestUtils.uninstall(packageName, serial);
        configuration.setDeviceName(device);
        AndroidRunner androidRunner = new AndroidRunner(tempAndroidApkPath.getAbsolutePath(), configuration);

        androidRunner.setup();
        AndroidApplication application = androidRunner.start();

        assertTrue(TestUtils.isAppInstalled(packageName, serial));
        assertTrue(TestUtils.isMainActivity(application, TestUtils.activityMap.get(TestUtils.ACTIVITY_MAIN)));
    }

    @Test
    public void shouldRetryTimesSpecified() throws Exception {
        final List<Integer> times = new ArrayList<Integer>();
        final String timeoutMessage = "custom timeout message";
        int retryFreqInSec = 5;
        int timeoutInSec = 20;
        expectedException.expect(OperationTimedoutException.class);
        expectedException.expectMessage(new BaseMatcher<String>() {

            public boolean matches(Object o) {
                return o.toString().contains(timeoutMessage);
            }

            public void describeTo(Description description) {

            }
        });

        final AndroidApplication application = TestUtils.installAppOnEmulator(EMULATOR, packageName, tempAndroidApkPath);
        application.waitFor(new ICondition() {
            @Override
            public boolean test() throws CalabashException {
                times.add(1);
                return application.query("button marked:'some foo element'").size() == 1;
            }
        }, new WaitOptions(timeoutInSec, retryFreqInSec, 0, timeoutMessage, false));

        assertEquals(timeoutInSec / retryFreqInSec, times.size());
    }

}