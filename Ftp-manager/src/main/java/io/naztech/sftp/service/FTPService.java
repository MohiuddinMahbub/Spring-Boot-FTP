package io.naztech.sftp.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.naztech.sftp.constants.Constants;

/**
 * @author Md. Mahbub Hasan Mohiuddin
 * @since 2020-02-26
 **/

@Service
public class FTPService {
	private static Logger log = LoggerFactory.getLogger(FTPService.class);
	
	private FTPClient ftp;

	@Value("${ftp.host:ftp.dac.naztech.us.com}")
	private String server;

	@Value("${ftp.port:2121}")
	private int port;

	@Value("${ftp.user:naztech}")
	private String user;

	@Value("${ftp.pass:NT@123}")
	private String password;

	@Value("${abby.in.path}")
	private String abbyIn;

	@Value("${abby.out.path}")
	private String abbyOut;

	//@Value("${docutech.in.path}")
	//private String localIn;

	@Value("${docutech.out.path}")
	private String localOut;
	
	@Value("${docutech.scan.path}")
	private String scanPath;
	
	@Value("${abby.move.path}")
	private String moveTo;

	private void open() throws IOException {
		ftp = new FTPClient();

		ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));

		ftp.connect(server, port);
		int reply = ftp.getReplyCode();
		if (!FTPReply.isPositiveCompletion(reply)) {
			ftp.disconnect();
			throw new IOException("Exception in connecting to FTP Server");
		}

		ftp.login(user, password);
	}
	
	private void close() throws IOException {
		ftp.disconnect();
	}
	
//	private Collection<String> listFiles(String path) throws IOException {
//		FTPFile[] files = ftp.listFiles(path);
//
//		return Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toList());
//	}
//
//	private void putFileToPath(File file, String path) throws IOException {
//		ftp.storeFile(path, new FileInputStream(file));
//	}
//
//	private void downloadFile(String source, String destination) throws IOException {
//		FileOutputStream out = new FileOutputStream(destination);
//		ftp.retrieveFile(source, out);
//		out.close();
//	}
	
	/**
	 * Download a whole directory from a FTP server.
	 * 
	 * @param ftpClient  an instance of org.apache.commons.net.ftp.FTPClient class.
	 * @param parentDir  Path of the parent directory of the current directory being
	 *                   downloaded.
	 * @param currentDir Path of the current directory being downloaded.
	 * @param saveDir    path of directory where the whole remote directory will be
	 *                   downloaded and saved.
	 * @throws IOException if any network or IO error occurred.
	 */
	private void downloadDirectory(FTPClient ftpClient, String parentDir, String currentDir, String saveDir)
			throws IOException {
		String dirToList = parentDir;
		if (!currentDir.equals(Constants.EMPTY_STR)) {
			dirToList += Constants.FWD_SLASH + currentDir;
		}

		FTPFile[] subFiles = ftpClient.listFiles(dirToList);

		if (subFiles != null && subFiles.length > 0) {
			for (FTPFile aFile : subFiles) {
				String currentFileName = aFile.getName();
				if (currentFileName.equals(".") || currentFileName.equals("..")) {
					// skip parent directory and the directory itself
					continue;
				}
				String filePath = parentDir + Constants.FWD_SLASH + currentDir + Constants.FWD_SLASH + currentFileName;
				if (currentDir.equals(Constants.EMPTY_STR)) {
					filePath = parentDir + Constants.FWD_SLASH + currentFileName;
				}

				String newDirPath = saveDir + parentDir + File.separator + currentDir + File.separator
						+ currentFileName;
				if (currentDir.equals(Constants.EMPTY_STR)) {
					newDirPath = saveDir + parentDir + File.separator + currentFileName;
				}

				if (aFile.isDirectory()) {
					// create the directory in saveDir
					File newDir = new File(newDirPath);
					boolean created = newDir.mkdirs();
					if (created) {
						log.info("CREATED the directory: {}", newDirPath);
					} else {
						log.info("COULD NOT create the directory: {}", newDirPath);
					}

					// download the sub directory
					downloadDirectory(ftpClient, dirToList, currentFileName, saveDir);
				} else {
					// download the file
					boolean success = downloadSingleFile(ftpClient, filePath, newDirPath);
					if (success) {
						ftpClient.rename(filePath, moveTo + currentFileName);
						log.info("DOWNLOADED the file: {}", filePath);
					} else {
						log.info("COULD NOT download the file: {}", filePath);
					}
				}
			}
		}
	}

	/**
	 * Download a single file from the FTP server
	 * 
	 * @param ftpClient      an instance of org.apache.commons.net.ftp.FTPClient
	 *                       class.
	 * @param remoteFilePath path of the file on the server
	 * @param savePath       path of directory where the file will be stored
	 * @return true if the file was downloaded successfully, false otherwise
	 * @throws IOException if any network or IO error occurred.
	 */
	private boolean downloadSingleFile(FTPClient ftpClient, String remoteFilePath, String savePath)
			throws IOException {
		File downloadFile = new File(savePath);

		File parentDir = downloadFile.getParentFile();
		if (!parentDir.exists()) {
			parentDir.mkdir();
		}

		OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(downloadFile));
		try {
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			
			return ftpClient.retrieveFile(remoteFilePath, outputStream);			
			
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (outputStream != null) {
				outputStream.close();
			}
		}
	}

	/**
	 * Upload a whole directory (including its nested sub directories and files) to
	 * a FTP server.
	 *
	 * @param ftpClient       an instance of org.apache.commons.net.ftp.FTPClient
	 *                        class.
	 * @param remoteDirPath   Path of the destination directory on the server.
	 * @param localParentDir  Path of the local directory being uploaded.
	 * @param remoteParentDir Path of the parent directory of the current directory
	 *                        on the server (used by recursive calls).
	 * @throws IOException if any network or IO error occurred.
	 */
	private void uploadDirectory(FTPClient ftpClient, String remoteDirPath, String localParentDir,
			String remoteParentDir) throws IOException {

		log.info("LISTING directory: {}", localParentDir);

		File localDir = new File(localParentDir);
		File[] subFiles = localDir.listFiles();
		if (subFiles != null && subFiles.length > 0) {
			for (File item : subFiles) {
				String remoteFilePath = remoteDirPath + Constants.FWD_SLASH + remoteParentDir + Constants.FWD_SLASH
						+ item.getName();
				if (remoteParentDir.equals(Constants.EMPTY_STR)) {
					remoteFilePath = remoteDirPath + Constants.FWD_SLASH + item.getName();
				}

				if (item.isFile()) {
					// upload the file
					String localFilePath = item.getAbsolutePath();
					log.info("About to upload the file: {}", localFilePath);

					boolean uploaded = uploadSingleFile(ftpClient, localFilePath, remoteFilePath);
					if (uploaded) {
						log.info("UPLOADED a file to: {}", remoteFilePath);
					} else {
						log.info("COULD NOT upload the file: {}", localFilePath);
					}
				} else {
					// create directory on the server
					boolean created = ftpClient.makeDirectory(remoteFilePath);
					if (created) {
						log.info("CREATED the directory: {}", remoteFilePath);
					} else {
						log.info("COULD NOT create the directory: {}", remoteFilePath);
					}

					// upload the sub directory
					String parent = remoteParentDir + Constants.FWD_SLASH + item.getName();
					if (remoteParentDir.equals(Constants.EMPTY_STR)) {
						parent = item.getName();
					}

					localParentDir = item.getAbsolutePath();
					uploadDirectory(ftpClient, remoteDirPath, localParentDir, parent);
				}
			}
		}
	}

	/**
	 * Upload a single file to the FTP server.
	 *
	 * @param ftpClient      an instance of org.apache.commons.net.ftp.FTPClient
	 *                       class.
	 * @param localFilePath  Path of the file on local computer
	 * @param remoteFilePath Path of the file on remote the server
	 * @return true if the file was uploaded successfully, false otherwise
	 * @throws IOException if any network or IO error occurred.
	 */
	private boolean uploadSingleFile(FTPClient ftpClient, String localFilePath, String remoteFilePath)
			throws IOException {
		File localFile = new File(localFilePath);

		InputStream inputStream = new FileInputStream(localFile);
		try {
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			return ftpClient.storeFile(remoteFilePath, inputStream);
		} finally {
			inputStream.close();
		}
	}

	public void download() throws IOException {

		try {
			open();

			log.info("***FTP server connection opened***");
			
			downloadDirectory(ftp, abbyOut, Constants.EMPTY_STR, localOut);
			
			log.info("***Directory download completed***");
			
		} catch (IOException e) {
			e.printStackTrace();
			log.info("***Directory download failed***");
		}
		finally {
			close();
			
			log.info("***FTP server connection closed***");
		}
	}

	public void upload() throws IOException {

		try {
			open();

			log.info("***FTP server connection opened***");
			
			uploadDirectory(ftp, abbyIn, scanPath, Constants.EMPTY_STR);
			
			log.info("***Directory upload completed***");
		} catch (IOException e) {
			e.printStackTrace();
			log.info("***Directory upload failed***");
		}
		finally {
			close();
			
			log.info("***FTP server connection closed***");
		}
	}
}