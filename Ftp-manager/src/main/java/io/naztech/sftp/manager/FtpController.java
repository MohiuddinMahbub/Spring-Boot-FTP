package io.naztech.sftp.manager;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.naztech.sftp.service.FTPService;

/**
 * @author Md. Mahbub Hasan Mohiuddin
 * @since 2020-02-26
 **/

@RestController
@EnableScheduling
public class FtpController {
	private static Logger log = LoggerFactory.getLogger(FtpController.class);

	@Autowired
	private FTPService fs;

	@Value("${docutech.scan.path}")
	private String scanPath;

	@Value("${docutech.move.path}")
	private String moveTo;

	@GetMapping("/download")
	public String download() {
		try {
			fs.download();
		} catch (IOException e) {
			log.error("Error downloading from remote server: {}", e.getMessage());
		}
		return "OK";
	}

	// @Scheduled(fixedRateString = "${fixed.rate.schedule}", initialDelay = 500)
	@Scheduled(fixedDelayString = "${fixed.rate.schedule}", initialDelay = 500)
	private void check() {

		File[] files = new File(scanPath).listFiles();
		for (File file : files) {
			if (file.isFile()) {
				log.info("New file found: {}", file.getName());
				try {
					fs.upload();
				} catch (IOException e) {
					log.error("Error uploading to remote server: {}", e.getMessage());
				} finally {
					if (file.renameTo(new File(moveTo + file.getName()))) {
						log.info("File moved successfully!");
					} else {
						log.info("File failed to move!");
					}
				}
			}
		}

		try {
			fs.download();
		} catch (IOException e) {
			log.error("Error downloading from remote server: {}", e.getMessage());
		}
	}

	/*
	 * @Scheduled(cron="${cron.expression}") private void testSchedule() {
	 * log.info("Hello"); }
	 */
}