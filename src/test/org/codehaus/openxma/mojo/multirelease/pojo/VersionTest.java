package org.codehaus.openxma.mojo.multirelease.pojo;

import org.junit.Test;
import static org.junit.Assert.*;

public class VersionTest {

	@Test
	public void testVersion() {
		Version version = Version.valueOf("a.b.c-B1");
		assertEquals("a", version.getMajor());
		assertEquals("b", version.getMinor());
		assertEquals("c", version.getPoint());
		assertEquals("B1", version.getSuffix());
		assertEquals("a.b.c-B1", version.toString());

		version = Version.valueOf("a.b.c");
		assertEquals("a", version.getMajor());
		assertEquals("b", version.getMinor());
		assertEquals("c", version.getPoint());
		assertEquals("", version.getSuffix());
		assertEquals("a.b.c", version.toString());

		version = Version.valueOf("a");
		assertEquals("a", version.getMajor());
		assertEquals("", version.getMinor());
		assertEquals("", version.getPoint());
		assertEquals("", version.getSuffix());
		assertEquals("a", version.toString());

		version = Version.valueOf("a.1.2");
		assertEquals("a", version.getMajor());
		assertEquals("1", version.getMinor());
		assertEquals("2", version.getPoint());
		assertEquals("", version.getSuffix());
		assertEquals("a.1.2", version.toString());
	}
	
	@Test
	public void testCompare() {
		assertTrue(Version.valueOf("a.b.c").compareTo(Version.valueOf("a.b.e")) < 0);
		
		assertTrue(Version.valueOf("1.3").compareTo(Version.valueOf("1.2")) > 0);
		
		assertTrue(Version.valueOf("3").compareTo(Version.valueOf("4")) < 0);
		
		assertTrue(Version.valueOf("1.3").compareTo(Version.valueOf("1.4")) < 0);
		
		assertTrue(Version.valueOf("1.3").compareTo(Version.valueOf("1.3.a")) < 0);
		
		assertTrue(Version.valueOf("1.a").compareTo(Version.valueOf("1.b")) < 0);
		
		assertTrue(Version.valueOf("1.2.3").compareTo(Version.valueOf("1.2.3")) == 0);
		
		assertTrue(Version.valueOf("1").compareTo(Version.valueOf("1")) == 0);
		
		assertTrue(Version.valueOf("a.b.c").compareTo(Version.valueOf("a.b.c")) == 0);
		
		assertTrue(Version.valueOf("a").compareTo(Version.valueOf("a")) == 0);
	}
}
