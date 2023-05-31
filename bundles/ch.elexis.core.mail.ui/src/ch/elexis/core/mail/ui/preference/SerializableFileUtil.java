package ch.elexis.core.mail.ui.preference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SerializableFileUtil {

	public static byte[] serializeData(ArrayList<SerializableFile> serial) throws IOException {
		byte[] serializedData = null;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(baos)) {
			out.writeObject(serial);
			serializedData = baos.toByteArray();
		}
		return serializedData;
	}

	@SuppressWarnings("unchecked")
	public static List<SerializableFile> deserializeData(byte[] data) throws IOException, ClassNotFoundException {
		List<SerializableFile> fileList = new ArrayList<>();
		try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
				ObjectInputStream ois = new ObjectInputStream(bais)) {
			fileList = (List<SerializableFile>) ois.readObject();
		}
		return fileList;
	}
}
