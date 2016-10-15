package org.codehaus.openxma.mojo.multirelease.pojo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * Class representing a software version. 
 * 
 * It supports the version in major[.minor][.point][-suffix] format. 
 * It does String comparison on the suffix. 
 * 
 * @author vikas_sit
 *
 */
public class Version implements Comparable<Version> {

	private static int CANNOT_COMPARE = -2;

	private String[] versionParts = new String[4];

	private static final Pattern VERSION_NUMBER_PATTERN = Pattern
			.compile("(?:(\\w+)\\.)?(?:(\\w+)\\.)?(?:(\\w+))?(?:(-\\w+))?$");

	public Version(String major, String minor, String point, String suffix) {
		super();
		versionParts[0] = major;
		versionParts[1] = minor;
		versionParts[2] = point;
		versionParts[3] = suffix;
	}

	public static Version valueOf(String version) {
		if (StringUtils.isBlank(version)) {
			throw new IllegalArgumentException("Version Number not specified.");
		}

		Matcher matcher = VERSION_NUMBER_PATTERN.matcher(version);

		if (matcher.find()) {
			int groupCount = matcher.groupCount();

			String[] versionParts = new String[4];
			int j = 0;
			for (int i = 0; i < groupCount; i++) {
				String group = matcher.group(i + 1);

				if (StringUtils.isNotBlank(group)) {
					versionParts[j++] = group;
				}
			}

			return new Version(versionParts[0], versionParts[1],
					versionParts[2], versionParts[3]);
		}

		return null;
	}

	public String getMajor() {
		return versionParts[0] == null ? "" : versionParts[0];
	}

	public String getMinor() {
		return versionParts[1] == null ? "" : versionParts[1];
	}

	public String getPoint() {
		return versionParts[2] == null ? "" : versionParts[2];
	}

	public String getSuffix() {
		return versionParts[3] == null ? "" : versionParts[3].substring(1);
	}

	@Override
	public String toString() {
		return (versionParts[0] != null ? versionParts[0] : "")
				+ (versionParts[1] != null ? "." + versionParts[1] : "")
				+ (versionParts[2] != null ? "." + versionParts[2] : "")
				+ (versionParts[3] != null ? versionParts[3] : "");
	}

	public int compareTo(Version other) {
		int result = 1;
		if (other == null) {
			result = 1;
		}

		if (this == other) {
			result = 0;
		}

		result = compare(this.getMajor(), other.getMajor());

		if (result == 0) {
			result = compare(this.getMinor(), other.getMinor());
		}

		if (result == 0) {
			result = compare(this.getPoint(), other.getPoint());
		}
		
		if (result == 0) {
			result = compare(this.getSuffix(), other.getSuffix());
		}

		return result;
	}

	private int compare(String current, String other) {
		int result = 0;
		if (StringUtils.isBlank(current) && StringUtils.isBlank(other)) {
			result = 0;
		} else {
			result = compareNumeric(current, other);

			if (result == CANNOT_COMPARE) {
				result = comapareStrings(current, other);
			}
		}

		return result;
	}

	private int comapareStrings(String current, String other) {
		int result = 0;

		if (!StringUtils.isBlank(current) && !StringUtils.isBlank(other)) {
			result = current.compareTo(other);
		} else if (!StringUtils.isBlank(current)) {
			result = 1;
		} else {
			result = -1;
		}

		return result;
	}

	private int compareNumeric(String current, String other) {
		int result = CANNOT_COMPARE;
		Integer currentInteger = null;
		Integer otherInteger = null;
		try {
			currentInteger = Integer.valueOf(current);
		} catch (NumberFormatException ex) {

		}

		try {
			otherInteger = Integer.valueOf(other);
		} catch (NumberFormatException ex) {

		}

		if (currentInteger != null && otherInteger != null) {
			result = currentInteger.compareTo(otherInteger);
		} else if (currentInteger != null) {
			result = 1;
		} else if (otherInteger != null) {
			result = -1;
		} else {
			result = CANNOT_COMPARE;
		}

		return result;
	}

}
