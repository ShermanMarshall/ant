package org.apache.tools.ant.types.selectors;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

public class PosixGroupSelectorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final String GROUP_GETTER = "getGid";

    private final File TEST_FILE = new File("/etc/passwd");

    private Class<?> jaasProviderClass;

    private PosixGroupSelector s;

    @Before
    public void setUp() {
        assumeTrue("Not POSIX", Os.isFamily("unix"));
        String osName = System.getProperty("os.name", "unknown").toLowerCase();
        String jaasProviderClassName = osName.contains("sunos")
           ? "com.sun.security.auth.module.SolarisSystem"
           : "com.sun.security.auth.module.UnixSystem";

        try {
            jaasProviderClass = Class.forName(jaasProviderClassName);
        } catch (Throwable e) {
            assumeNoException("Cannot obtain OS-specific JAAS information", e);
        }

        s = new PosixGroupSelector();
    }

    @Test
    public void posixGroupIsTrueForSelf() throws Exception {
        long gid = (long) jaasProviderClass.getMethod(GROUP_GETTER)
                .invoke(jaasProviderClass.newInstance());

        File file = folder.newFile("f.txt");
        Map<String, Object> fileAttributes = Files.readAttributes(file.toPath(),
                "unix:group,gid", LinkOption.NOFOLLOW_LINKS);
        long actualGid = (int) fileAttributes.get("gid");
        assertEquals("Different GIDs", gid, actualGid);

        GroupPrincipal actualGroup = (GroupPrincipal) fileAttributes.get("group");
        s.setGroup(actualGroup.getName());
        assertTrue(s.isSelected(null, null, file));
    }

    @Test
    public void posixGroupFollowSymlinks() throws Exception {
        long gid = (long) jaasProviderClass.getMethod(GROUP_GETTER)
                .invoke(jaasProviderClass.newInstance());

        File target = new File(folder.getRoot(), "link");
        Path symbolicLink = Files.createSymbolicLink(target.toPath(), TEST_FILE.toPath());
        Map<String, Object> linkAttributes = Files.readAttributes(target.toPath(),
                "unix:group,gid", LinkOption.NOFOLLOW_LINKS);
        long linkGid = (int) linkAttributes.get("gid");
        assertEquals("Different GIDs", gid, linkGid);

        GroupPrincipal targetGroup = Files.readAttributes(target.toPath(),
                PosixFileAttributes.class).group();
        GroupPrincipal linkGroup = (GroupPrincipal) linkAttributes.get("group");
        assertNotEquals("Same group name", linkGroup.getName(),
                targetGroup.getName());

        s.setGroup(linkGroup.getName());
        assertTrue(s.isSelected(null, null, symbolicLink.toFile()));
        s.setFollowSymlinks("yes");
        assertFalse(s.isSelected(null, null, symbolicLink.toFile()));
    }
}
