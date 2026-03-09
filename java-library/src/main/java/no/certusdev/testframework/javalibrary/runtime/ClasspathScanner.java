package no.certusdev.testframework.javalibrary.runtime;

import no.certusdev.testframework.javalibrary.annotations.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans the classpath for classes annotated with
 * {@link no.certusdev.testframework.javalibrary.annotations.TestSuite @TestSuite}.
 *
 * <p>Supports both exploded class directories (local development / Gradle build)
 * and JAR files (packaged Spring Boot app).
 *
 * <p>Usage — scan the entire application classpath automatically:
 * <pre>{@code
 * Set<Class<?>> suites = ClasspathScanner.findAllTestSuites();
 * }</pre>
 *
 * <p>Usage — scan a specific package only:
 * <pre>{@code
 * Set<Class<?>> suites = ClasspathScanner.findTestSuites("com.example.tests");
 * }</pre>
 *
 * @see no.certusdev.testframework.javalibrary.service.TestFrameworkService
 * @see TestSuiteRegistry
 */
public class ClasspathScanner {

    private static final Logger log = LoggerFactory.getLogger(ClasspathScanner.class);

    private ClasspathScanner() {}

    /**
     * Scans the <b>entire application classpath</b> for classes annotated with
     * {@code @TestSuite}, without needing a package name.
     *
     * <p>Works by iterating every URL entry of the context {@link ClassLoader}
     * (both directories and JARs). JDK internal modules and bootstrap entries
     * are skipped automatically.
     *
     * @return a set of matching classes; empty if none are found
     */
    public static Set<Class<?>> findAllTestSuites() {
        Set<Class<?>> found = new HashSet<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Set<URL> urls = collectClasspathUrls(classLoader);
        log.debug("Full classpath scan: checking {} URL entries", urls.size());

        for (URL url : urls) {
            String protocol = url.getProtocol();
            try {
                if ("file".equals(protocol)) {
                    File entry = new File(url.toURI());
                    if (entry.isDirectory()) {
                        // Exploded classes directory — scan recursively from root
                        Set<Class<?>> candidates = new HashSet<>();
                        findClassesInDirectory(entry, "", candidates, classLoader);
                        candidates.stream()
                                .filter(c -> c.isAnnotationPresent(TestSuite.class))
                                .peek(c -> log.info("Auto-discovered @TestSuite: {}", c.getName()))
                                .forEach(found::add);
                    } else if (entry.getName().endsWith(".jar")) {
                        scanJarFile(entry, found, classLoader);
                    }
                } else if ("jar".equals(protocol)) {
                    JarURLConnection jarConn = (JarURLConnection) url.openConnection();
                    scanJarEntries(jarConn.getJarFile(), found, classLoader);
                }
            } catch (IOException | RuntimeException | URISyntaxException e) {
                log.debug("Skipping classpath entry '{}': {}", url, e.getMessage());
            }
        }

        log.info("Full classpath scan found {} suite(s)", found.size());
        return found;
    }

    /**
     * Scans {@code basePackage} and all sub-packages for classes annotated with
     * {@code @TestSuite}.
     *
     * @param basePackage the root package to scan, e.g. {@code "com.example.tests"}
     * @return a set of matching classes; empty if none are found
     */
    public static Set<Class<?>> findTestSuites(String basePackage) {
        Set<Class<?>> found = new HashSet<>();
        Set<Class<?>> candidates = findClasses(basePackage);
        for (Class<?> clazz : candidates) {
            if (clazz.isAnnotationPresent(TestSuite.class)) {
                log.info("Auto-discovered @TestSuite: {}", clazz.getName());
                found.add(clazz);
            }
        }
        log.info("Classpath scan of '{}' found {} suite(s)", basePackage, found.size());
        return found;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Collects all URL entries from {@code classLoader} and its parents,
     * stopping before the bootstrap/platform classloader.
     */
    private static Set<URL> collectClasspathUrls(ClassLoader classLoader) {
        Set<URL> urls = new HashSet<>();
        ClassLoader cl = classLoader;
        while (cl != null) {
            if (cl instanceof URLClassLoader urlCl) {
                for (URL url : urlCl.getURLs()) {
                    urls.add(url);
                }
            }
            cl = cl.getParent();
        }
        // Fallback for Spring Boot fat-jar / module-path launchers
        if (urls.isEmpty()) {
            try {
                Enumeration<URL> roots = classLoader.getResources("");
                while (roots.hasMoreElements()) {
                    urls.add(roots.nextElement());
                }
            } catch (IOException e) {
                log.warn("Could not enumerate classpath roots: {}", e.getMessage());
            }
        }
        return urls;
    }

    /**
     * Opens a JAR file from a {@code file://} URL and scans it for suites.
     */
    private static void scanJarFile(File jarFile, Set<Class<?>> found, ClassLoader loader) {
        try (JarFile jar = new JarFile(jarFile)) {
            scanJarEntries(jar, found, loader);
        } catch (IOException e) {
            log.debug("Skipping JAR '{}': {}", jarFile.getName(), e.getMessage());
        }
    }

    /**
     * Scans all entries of an open {@link JarFile} for {@code @TestSuite} classes.
     */
    private static void scanJarEntries(JarFile jar, Set<Class<?>> found, ClassLoader loader) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class") && !entry.isDirectory()) {
                String className = name.replace('/', '.').replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className, false, loader);
                    if (clazz.isAnnotationPresent(TestSuite.class)) {
                        log.info("Auto-discovered @TestSuite: {}", clazz.getName());
                        found.add(clazz);
                    }
                } catch (ClassNotFoundException | LinkageError e) {
                    log.debug("Skipping class '{}': {}", className, e.getMessage());
                }
            }
        }
    }

    /**
     * Recursively finds all classes under {@code packageName} using the context
     * class loader, handling both file-system directories and JAR entries.
     */
    private static Set<Class<?>> findClasses(String packageName) {
        Set<Class<?>> classes = new HashSet<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                    findClassesInDirectory(new File(filePath), packageName, classes, classLoader);
                } else if ("jar".equals(protocol)) {
                    JarURLConnection jarConn = (JarURLConnection) resource.openConnection();
                    findClassesInJar(jarConn.getJarFile(), path, classes, classLoader);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan package '{}': {}", packageName, e.getMessage());
        }

        return classes;
    }

    /**
     * Walks a directory tree, loading every {@code .class} file as a {@link Class}.
     * When {@code packageName} is empty (root scan), derives the package from the path.
     */
    private static void findClassesInDirectory(File directory, String packageName,
                                                Set<Class<?>> classes, ClassLoader loader) {
        if (!directory.exists() || !directory.isDirectory()) return;

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = packageName.isEmpty()
                        ? file.getName()
                        : packageName + "." + file.getName();
                findClassesInDirectory(file, subPackage, classes, loader);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName.isEmpty()
                        ? file.getName().replace(".class", "")
                        : packageName + "." + file.getName().replace(".class", "");
                loadSafely(className, loader, classes);
            }
        }
    }

    /**
     * Iterates JAR entries, loading every {@code .class} file under {@code basePath}.
     */
    private static void findClassesInJar(JarFile jar, String basePath,
                                          Set<Class<?>> classes, ClassLoader loader) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(basePath) && name.endsWith(".class") && !entry.isDirectory()) {
                String className = name.replace('/', '.').replace(".class", "");
                loadSafely(className, loader, classes);
            }
        }
    }

    /**
     * Loads a class by name, silently ignoring classes that cannot be loaded
     * (e.g. classes with missing dependencies at scan time).
     */
    private static void loadSafely(String className, ClassLoader loader, Set<Class<?>> classes) {
        try {
            classes.add(Class.forName(className, false, loader));
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("Skipping class '{}': {}", className, e.getMessage());
        }
    }
}
