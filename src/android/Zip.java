package org.apache.cordova;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.unzip.UnzipUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.zip.ZipEntry;
//import java.util.zip.ZipInputStream;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.net.Uri;
import android.util.Log;

public class Zip extends CordovaPlugin {

	private static final String LOG_TAG = "Zip";

	@Override
	public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
		if ("unzip".equals(action)) {
			this.cordova.getThreadPool().execute(new Runnable() { public void run() {
				unzip(args, callbackContext);
			} });
			return true;
		} 
		else if ("unzip_dir".equals(action)) {
			this.cordova.getThreadPool().execute(new Runnable() { public void run() {
				unzip_dir(args, callbackContext);
			} });
			return true;
		}
		else if ("unzip_str".equals(action)) {
			this.cordova.getThreadPool().execute(new Runnable() { public void run() {
				unzip_str(args, callbackContext);
			} });
			return true;
		}
		else if ("unzip_str_zip4j".equals(action)) {
			this.cordova.getThreadPool().execute(new Runnable() { public void run() {
				unzip_str_zip4j(args, callbackContext);
			} });
			return true;
		}
		return false;
	}

	// Can't use DataInputStream because it has the wrong endian-ness.
	private static int readInt(InputStream is) throws IOException {
		int a = is.read(); int b = is.read(); int c = is.read(); int d = is.read(); 
		return a | b << 8 | c << 16 | d << 24;
	}

	private java.util.zip.ZipInputStream open_zip_inputStream(String zipFileName, final CallbackContext callbackContext, ProgressEvent progress) throws IOException {
		// Since Cordova 3.3.0 and release of File plugins, files are accessed via cdvfile://
		// Accept a path or a URI for the source zip.
		CordovaResourceApi resourceApi = webView.getResourceApi();

		Uri zipUri = getUriForArg(zipFileName);
		File tempFile = resourceApi.mapUriToFile(zipUri);
		if (tempFile == null || !tempFile.exists()) {
			String errorMessage = "Zip file does not exist: '"+zipFileName+"'";
			callbackContext.error(errorMessage);
			Log.e(LOG_TAG, errorMessage);
			return null;
		}

		OpenForReadResult zipFile = resourceApi.openForRead(zipUri);

		progress.setTotal(zipFile.length);

		InputStream inputStream = new BufferedInputStream(zipFile.inputStream);
		inputStream.mark(10);
		int magic = readInt(inputStream);

		if (magic != 875721283) { // CRX identifier
			inputStream.reset();
		} else {
			// CRX files contain a header. This header consists of:
			//  * 4 bytes of magic number
			//  * 4 bytes of CRX format version,
			//  * 4 bytes of public key length
			//  * 4 bytes of signature length
			//  * the public key
			//  * the signature
			// and then the ordinary zip data follows. We skip over the header before creating the ZipInputStream.
			readInt(inputStream); // version == 2.
			int pubkeyLength = readInt(inputStream);
			int signatureLength = readInt(inputStream);

			inputStream.skip(pubkeyLength + signatureLength);
			progress.setLoaded(16 + pubkeyLength + signatureLength);
		}

		// The inputstream is now pointing at the start of the actual zip file content.
		java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream);
		return zis;
	}

	private boolean open_output_dir(String outputDirectory, final CallbackContext callbackContext) throws IOException {
		CordovaResourceApi resourceApi = webView.getResourceApi();

		Uri outputUri = getUriForArg(outputDirectory);
		File outputDir = resourceApi.mapUriToFile(outputUri);
		outputDirectory = outputDir.getAbsolutePath();
		outputDirectory += outputDirectory.endsWith(File.separator) ? "" : File.separator;
		if (outputDir == null || (!outputDir.exists() && !outputDir.mkdirs())){
			String errorMessage = "Zip, Could not create output directory '"+outputDirectory+"'";
			callbackContext.error(errorMessage);
			Log.e(LOG_TAG, errorMessage);
			return false;
		}

		return true;
	}

	private void unzip(CordovaArgs args, CallbackContext callbackContext) {
		java.util.zip.ZipInputStream zis= null;
		try {
			String zipFileName = args.getString(0);
			String outputDirectory = args.getString(1);

			ProgressEvent progress = new ProgressEvent();
			zis= open_zip_inputStream(zipFileName, callbackContext, progress);

			if (zis==null) { return ; } //A: algo fallo, y logueo
			if (! open_output_dir(outputDirectory, callbackContext)) { return ; } //A: algo fallo y logueo

			ZipEntry ze;
			byte[] buffer = new byte[32 * 1024];
			boolean anyEntries = false;
			while ((ze = zis.getNextEntry()) != null) {
				anyEntries = true;
				String compressedName = ze.getName();

				if (ze.isDirectory()) {
					File dir = new File(outputDirectory + compressedName);
					dir.mkdirs();
				} else {
					File file = new File(outputDirectory + compressedName);
					file.getParentFile().mkdirs();
					if(file.exists() || file.createNewFile()){
						Log.w(LOG_TAG, "extracting: " + file.getPath());
						FileOutputStream fout = new FileOutputStream(file);
						int count;
						while ((count = zis.read(buffer)) != -1) {
							fout.write(buffer, 0, count);
						}
						fout.close();
					}
				}
				progress.addLoaded(ze.getCompressedSize());
				updateProgress(callbackContext, progress);
				zis.closeEntry();
			}

			progress.setLoaded(progress.getTotal()); //A: final progress = 100%
			updateProgress(callbackContext, progress);

			if (anyEntries) {
				callbackContext.success();
			}
			else {
				callbackContext.error("Zip, bad file '"+zipFileName+"'");
			}
		} catch (Exception e) {
			String errorMessage = "An error occurred while unzipping.";
			callbackContext.error(errorMessage);
			Log.e(LOG_TAG, errorMessage, e);
		} finally {
			if (zis != null) { try { zis.close(); } catch (IOException e) { } }
		}
	}

	private void unzip_str(CordovaArgs args, CallbackContext callbackContext) {
		InputStream inputStream = null;
		try {
			String zipFileName = args.getString(0);
			String filePathToExtract = args.getString(1);

			// Since Cordova 3.3.0 and release of File plugins, files are accessed via cdvfile://
			// Accept a path or a URI for the source zip.
			Uri zipUri = getUriForArg(zipFileName);

			CordovaResourceApi resourceApi = webView.getResourceApi();

			File tempFile = resourceApi.mapUriToFile(zipUri);
			if (tempFile == null || !tempFile.exists()) {
				String errorMessage = "Zip file does not exist";
				callbackContext.error(errorMessage);
				Log.e(LOG_TAG, errorMessage);
				return;
			}

			OpenForReadResult zipFile = resourceApi.openForRead(zipUri);
			ProgressEvent progress = new ProgressEvent();
			progress.setTotal(zipFile.length);

			inputStream = new BufferedInputStream(zipFile.inputStream);
			inputStream.mark(10);
			int magic = readInt(inputStream);

			if (magic != 875721283) { // CRX identifier
				inputStream.reset();
			} else {
				// CRX files contain a header. This header consists of:
				//  * 4 bytes of magic number
				//  * 4 bytes of CRX format version,
				//  * 4 bytes of public key length
				//  * 4 bytes of signature length
				//  * the public key
				//  * the signature
				// and then the ordinary zip data follows. We skip over the header before creating the ZipInputStream.
				readInt(inputStream); // version == 2.
				int pubkeyLength = readInt(inputStream);
				int signatureLength = readInt(inputStream);

				inputStream.skip(pubkeyLength + signatureLength);
				progress.setLoaded(16 + pubkeyLength + signatureLength);
			}

			// The inputstream is now pointing at the start of the actual zip file content.
			java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream);
			inputStream = zis;

			ZipEntry ze;
			byte[] buffer = new byte[32 * 1024];
			boolean found = false;

			ByteArrayOutputStream fout = new ByteArrayOutputStream();
			while (!found && (ze = zis.getNextEntry()) != null)
			{
				String compressedName = ze.getName();
				Log.w(LOG_TAG, "extracting: " + compressedName);
				if (compressedName.equals(filePathToExtract)) {
					int count;
					while ((count = zis.read(buffer)) != -1)
					{
						fout.write(buffer, 0, count);
					}
					fout.close();
					found= true;
				}
				zis.closeEntry();
			}

			if (found) {	
				callbackContext.success(fout.toByteArray());
			} else {
				callbackContext.error("Bad zip file");
			}
		} catch (Exception e) {
			String errorMessage = "An error occurred while unzipping.";
			callbackContext.error(errorMessage);
			Log.e(LOG_TAG, errorMessage, e);
		} finally {
			if (inputStream != null) {
				try { inputStream.close(); } catch (IOException e) { }
			}
		}
	}

	private void unzip_dir(CordovaArgs args, CallbackContext callbackContext) {
		InputStream inputStream = null;
		try {
			String zipFileName = args.getString(0);

			// Since Cordova 3.3.0 and release of File plugins, files are accessed via cdvfile://
			// Accept a path or a URI for the source zip.
			Uri zipUri = getUriForArg(zipFileName);

			CordovaResourceApi resourceApi = webView.getResourceApi();

			File tempFile = resourceApi.mapUriToFile(zipUri);
			if (tempFile == null || !tempFile.exists()) {
				String errorMessage = "Zip file does not exist";
				callbackContext.error(errorMessage);
				Log.e(LOG_TAG, errorMessage);
				return;
			}

			OpenForReadResult zipFile = resourceApi.openForRead(zipUri);

			inputStream = new BufferedInputStream(zipFile.inputStream);
			inputStream.mark(10);
			int magic = readInt(inputStream);

			if (magic != 875721283) { // CRX identifier
				inputStream.reset();
			} else {
				// CRX files contain a header. This header consists of:
				//  * 4 bytes of magic number
				//  * 4 bytes of CRX format version,
				//  * 4 bytes of public key length
				//  * 4 bytes of signature length
				//  * the public key
				//  * the signature
				// and then the ordinary zip data follows. We skip over the header before creating the ZipInputStream.
				readInt(inputStream); // version == 2.
				int pubkeyLength = readInt(inputStream);
				int signatureLength = readInt(inputStream);

				inputStream.skip(pubkeyLength + signatureLength);
			}

			// The inputstream is now pointing at the start of the actual zip file content.
			java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(inputStream);
			inputStream = zis;

			ZipEntry ze;
			JSONArray dirArray = new JSONArray();
	
			while ((ze = zis.getNextEntry()) != null)
			{
				String compressedName = ze.getName();
				Log.w(LOG_TAG, "extracting: " + compressedName);
				dirArray.put(compressedName);
				zis.closeEntry();
			}

			if (dirArray != null) {	
				callbackContext.success(dirArray);
			} else {
				callbackContext.error("Bad zip file");
			}
		} catch (Exception e) {
			String errorMessage = "An error occurred while unzipping.";
			callbackContext.error(errorMessage);
			Log.e(LOG_TAG, errorMessage, e);
		} finally {
			if (inputStream != null) { try { inputStream.close(); } catch (IOException e) { } }
		}
	}


	private final int BUFF_SIZE = 4096;
	private void unzip_str_zip4j(CordovaArgs args, CallbackContext callbackContext) {
		ZipInputStream is = null;
		ByteArrayOutputStream os = null;
		String logDetail= "";
		try {
			String zipFileName = args.getString(0);
			String zipPass = args.getString(1);
			String filePathToExtract = args.getString(2);
			String zipPass_SAFE = zipPass; //XXX:SEC
			logDetail= "zip '" + zipFileName +"' pass '"+zipPass_SAFE+"' pathToExtract '"+filePathToExtract+"'"; 
			Log.w(LOG_TAG, "Extracting "+logDetail);

			Uri zipUri = getUriForArg(zipFileName);
			CordovaResourceApi resourceApi = webView.getResourceApi();
			File asFile = resourceApi.mapUriToFile(zipUri);

			ZipFile zipFile = new ZipFile(asFile);
			if (zipFile.isEncrypted()) {
				Log.w(LOG_TAG, "IsEncripted "+logDetail);
				zipFile.setPassword(zipPass);
			}

			//Get the FileHeader of the File you want to extract from the
			//zip file. Input for the below method is the name of the file
			//For example: 123.txt or abc/123.txt if the file 123.txt
			//is inside the directory abc
			FileHeader fileHeader = zipFile.getFileHeader(filePathToExtract);
			if (fileHeader == null) {
				Log.e(LOG_TAG, "Compressed file not found in "+logDetail);
				callbackContext.error("Compressed file not found: '"+filePathToExtract+"'");
			}
			else {
				is = zipFile.getInputStream(fileHeader);
				os = new ByteArrayOutputStream();
				int readLen = -1;
				byte[] buff = new byte[BUFF_SIZE];
				while ((readLen = is.read(buff)) != -1) {
					os.write(buff, 0, readLen);
				}

				//Closing inputstream also checks for CRC of the the just extracted file.
				//If CRC check has to be skipped (for ex: to cancel the unzip operation, etc)
				//use method is.close(boolean skipCRCCheck) and set the flag,
				//skipCRCCheck to false
				//NOTE: It is recommended to close outputStream first because Zip4j throws 
				//an exception if CRC check fails
				is.close();
				os.close();
			}
		}
		catch (Exception ex) {
			Log.e(LOG_TAG, "EXCEPTION "+logDetail);
			callbackContext.error("EXCEPTION: "+ex.toString());
		}
		finally {
			if (is!=null) { try { is.close(); } catch (IOException ex2) {} }
		}

		if (os!=null) {	
			callbackContext.success(os.toByteArray());
		} else {
			Log.e(LOG_TAG, "Bad "+logDetail); 
			callbackContext.error("Bad zip file");
		}
	}

	private void updateProgress(CallbackContext callbackContext, ProgressEvent progress) throws JSONException {
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, progress.toJSONObject());
		pluginResult.setKeepCallback(true);
		callbackContext.sendPluginResult(pluginResult);
	}

	private Uri getUriForArg(String arg) {
		CordovaResourceApi resourceApi = webView.getResourceApi();
		Uri tmpTarget = Uri.parse(arg);
		return resourceApi.remapUri(
				tmpTarget.getScheme() != null ? tmpTarget : Uri.fromFile(new File(arg)));
	}

	private static class ProgressEvent {
		private long loaded;
		private long total;
		public long getLoaded() {
			return loaded;
		}
		public void setLoaded(long loaded) {
			this.loaded = loaded;
		}
		public void addLoaded(long add) {
			this.loaded += add;
		}
		public long getTotal() {
			return total;
		}
		public void setTotal(long total) {
			this.total = total;
		}
		public JSONObject toJSONObject() throws JSONException {
			return new JSONObject(
					"{loaded:" + loaded +
					",total:" + total + "}");
		}
	}
}
