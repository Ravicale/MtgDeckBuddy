package application;

public enum LogTags {
	//TODO: Add support to override the default settings with a config file.
	DECK_IMAGE("Deck Image", LogLevel.INFO),
	PREFETCH ("Prefetch", LogLevel.WARN),
	IMAGE_LOAD("Image Loading", LogLevel.INFO),
	DB_INIT("Database Init", LogLevel.WARN),
	DB_ACTION("Database Action", LogLevel.WARN),
	USER_INPUT("User Input", LogLevel.INFO),
	UI_UPDATES("UI Updates", LogLevel.WARN),
	UI_SYNC("UI Sync", LogLevel.WARN);

	@SuppressWarnings("unused")
	private enum LogLevel {
		OFF("off"),
		TRACE("trace"),
		DEBUG("debug"),
		INFO("info"),
		WARN("warn"),
		ERROR("error");

		public final String value;
		LogLevel(String valueName) {
			value = valueName;
		}
	}

	public final String tag;
	private final LogLevel level;

	LogTags(String tagName, LogLevel levelName) {
		tag = tagName;
		level = levelName;
	}

	private static final boolean WRITE_TO_FILE = true;
	public static void configureLogging() {
		if (WRITE_TO_FILE) {
			org.tinylog.configuration.Configuration.set("writer", "file");
			org.tinylog.configuration.Configuration.set("writer.file", "log.txt");
			org.tinylog.configuration.Configuration.set("writer.charset", "UTF-8");
		} else {
			org.tinylog.configuration.Configuration.set("writer", "console");
		}

		org.tinylog.configuration.Configuration.set("writingthread", "true");
		org.tinylog.configuration.Configuration.set("writer.buffered", "true");
		org.tinylog.configuration.Configuration.set("writer.format", "{thread} {date} - [\"{tag}\"|{level}] : {message|indent=4}");

		LogTags[] tags = values();
		StringBuilder enabledTags = new StringBuilder();
		boolean first = true;
		for (LogTags tag : tags) {
			if (tag.level != LogLevel.OFF) {
				if (!first) {
					enabledTags.append(",");
				}
				enabledTags.append(tag.tag);
				enabledTags.append("@");
				enabledTags.append(tag.level.value);
				first = false;
			}
		}

		//Literally cannot use the logger here.
		//noinspection UseOfSystemOutOrSystemErr
		System.out.println("Logging \"" + enabledTags + "\"");
		org.tinylog.configuration.Configuration.set("writer.tag", enabledTags.toString());
	}
}
