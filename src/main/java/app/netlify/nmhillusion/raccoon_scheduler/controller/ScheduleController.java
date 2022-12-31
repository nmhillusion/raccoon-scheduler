package app.netlify.nmhillusion.raccoon_scheduler.controller;

import app.netlify.nmhillusion.raccoon_scheduler.service.AdminService;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * date: 2022-12-31
 * <p>
 * created-by: nmhillusion
 */

@RestController
@RequestMapping(value = "/api/schedule")
public class ScheduleController {
	@Autowired
	private AdminService adminService;

	@GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getStatus(@RequestParam @NotNull String username) {
		return ResponseEntity.ok()
				.body(adminService.currentStatus(username));
	}

}
