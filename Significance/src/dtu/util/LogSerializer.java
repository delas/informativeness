package dtu.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * This class serializes message sent to the {@link #serialize(String)} method
 * by archiving each session.
 * 
 * @author Andrea Burattin
 */
public class LogSerializer {

	private static Path CURRENT_SESSION_FILE;
	
	static {
		CURRENT_SESSION_FILE = Paths.get(System.getProperty("user.dir") + "\\data\\results");
		
		try {
			Files.delete(CURRENT_SESSION_FILE);
			Files.createFile(CURRENT_SESSION_FILE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * This method is used to store messages into a file which is then archived
	 * in a compressed file
	 * 
	 * @param message the message to archive
	 */
	public static void serialize(String message) {
		try {
			Files.write(CURRENT_SESSION_FILE, now().concat(" - ").concat(message).concat("\n").getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void print() throws IOException {
		BufferedReader br = Files.newBufferedReader(CURRENT_SESSION_FILE);
		for (String line; (line = br.readLine()) != null;) {
			System.out.println(line);
		}
		br.close();
	}

	private static String now() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
		return sdf.format(cal.getTime());
	}
}