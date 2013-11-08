package calabash.java.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class AndroidRunner {

    private final AndroidConfiguration configuration;
    private final File apk;
    private AndroidCalabashWrapper calabashWrapper;

    public AndroidRunner(String apkPath, AndroidConfiguration configuration) throws CalabashException {
        this.configuration = configuration;

        if (!apkPath.endsWith(".apk")) {
            throw new CalabashException("invalid path to apk file");
        }

        apk = new File(apkPath);
        if (!apk.exists()) {
            throw new CalabashException("invalid path to apk file");
        }
        setup();
    }

    public AndroidRunner(String apkPath) throws CalabashException {
        this(apkPath, null);
    }

    private void setup() throws CalabashException {
        File gemPath = extractGemsFromBundle();
        calabashWrapper = new AndroidCalabashWrapper(gemPath, apk,
                configuration);
        calabashWrapper.setup();
    }

    private File extractGemsFromBundle() throws CalabashException {
        File dir = getGemsExtractionDir();
        File extracted = new File(dir, "extracted");
        if (extracted.exists()) {
            // Already extracted
            return dir;
        }

        copyFileFromBundleTo("scripts", "gems.zip", dir);
        try {
            File gemszip = new File(dir, "gems.zip");
            Utils.unzip(gemszip, dir);
            gemszip.delete();
            extracted.createNewFile();
        } catch (Exception e) {
            throw new CalabashException("Failed to unzip gems", e);
        }

        return dir;
    }

    private File getGemsExtractionDir() throws CalabashException {
        try {
            File tempFile = File.createTempFile("foo", "bar");
            tempFile.delete();

            File gemsDir = new File(tempFile.getParentFile(),
                    "calabash-android-gems-" + getCurrentVersion());
            if (!gemsDir.exists()) {
                boolean created = gemsDir.mkdir();
                if (!created)
                    throw new CalabashException(
                            "Can't create gems extraction directory. "
                                    + gemsDir.getAbsolutePath());
            }

            if (!gemsDir.isDirectory())
                throw new CalabashException(String.format(
                        "Gems directory is invalid. %s is not a directory",
                        gemsDir.getAbsolutePath()));

            return gemsDir;
        } catch (IOException e) {
            throw new CalabashException(
                    "Can't create gems extraction directory.", e);
        }
    }

    private String getCurrentVersion() {
        try {
            Enumeration<URL> resources = getClass().getClassLoader()
                    .getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                try {
                    Manifest manifest = new Manifest(resources.nextElement()
                            .openStream());
                    Attributes mainAttributes = manifest.getMainAttributes();
                    if (mainAttributes != null
                            && "calabash-android-java".equals(mainAttributes
                            .getValue("Project-Name"))) {
                        String value = mainAttributes
                                .getValue("Implementation-Version");
                        if (value != null)
                            return value;
                    }
                } catch (Exception E) {
                    // ignore
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return "";
    }

    private void copyFileFromBundleTo(String sourceDir, String fileName,
                                      File outDir) throws CalabashException {
        final ClassLoader classLoader = Thread.currentThread()
                .getContextClassLoader();
        InputStream stream = classLoader.getResourceAsStream(sourceDir + "/"
                + fileName);
        if (stream == null)
            throw new CalabashException(
                    String.format(
                            "Can't copy %s from the bundle. Make sure you are using the correct JAR file",
                            fileName), null);

        try {
            File file = new File(outDir, fileName);
            file.createNewFile();
            FileOutputStream outFile = new FileOutputStream(file);
            byte[] buffer = new byte[10240];
            int len;
            while ((len = stream.read(buffer)) != -1) {
                outFile.write(buffer, 0, len);
            }
            outFile.close();
        } catch (IOException e) {
            throw new CalabashException(
                    String.format(
                            "Can't copy %s from the bundle to %s. Failed to create destination file",
                            fileName, outDir.getAbsolutePath()), e);
        }
    }
}
