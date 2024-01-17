package app.netlify.nmhillusion.raccoon_scheduler.exception;

import app.netlify.nmhillusion.raccoon_scheduler.service.AdminService;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tech.nmhillusion.n2mix.exception.ApiResponseException;
import tech.nmhillusion.n2mix.exception.AppRuntimeException;
import tech.nmhillusion.n2mix.model.ApiErrorResponse;
import tech.nmhillusion.n2mix.util.ExceptionUtil;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
    @Autowired
    private AdminService adminService;

    @ExceptionHandler(value = ApiResponseException.class)
    protected ResponseEntity<ApiErrorResponse> handleException(ApiResponseException ex) {
        adminService.reportError(ex.getMessage(), ex);
        final AppRuntimeException appRuntimeException = ExceptionUtil.throwException(ex);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST, ex.getClass().getName(), appRuntimeException.getMessage()));
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    protected ResponseEntity<ApiErrorResponse> handleException(ConstraintViolationException ex) {
        adminService.reportError(ex.getMessage(), ex);
        final ApiErrorResponse apiErrorResponse = new ApiErrorResponse(HttpStatus.BAD_REQUEST, ex.getClass().getName(),
                ex.getMessage());
        return new ResponseEntity<>(apiErrorResponse, HttpStatus.valueOf(apiErrorResponse.getStatus()));
    }

//	@Override
//	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
//	                                                              HttpHeaders headers, HttpStatus status, WebRequest request) {
//		adminService.reportError(ex.getMessage(), ex, headers, status, request);
//		// Get all errors
//		final List<String> errors = ex.getBindingResult().getFieldErrors().stream().map(x -> x.getDefaultMessage())
//				.collect(Collectors.toList());
//
//		final ApiErrorResponse resErr = new ApiErrorResponse(HttpStatus.BAD_REQUEST, ex.getClass().getName(),
//				String.join("; ", errors));
//
//		return new ResponseEntity<>(resErr, HttpStatus.BAD_REQUEST);
//	}
//
//	@Override
//	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
//	                                                         HttpStatus status, WebRequest request) {
//		adminService.reportError(ex.getMessage(), ex, body, headers, status, request);
//		LogHelper.getLog(this).error("INTERNAL_EXCEPTION: " + new ChainMap<String, Object>()
//				.chainPut("message", ex.getMessage())
//				.chainPut("body", body)
//				.chainPut("headers", headers)
//				.chainPut("status", status)
//				.chainPut("webRequest", request)
//		);
//		final ApiErrorResponse apiErrorResponse = new ApiErrorResponse(status, ErrorType.INTERNAL_ERROR.getErrorName(), ex.getMessage());
//		return ResponseEntity
//				.status(HttpStatus.valueOf(apiErrorResponse.getStatus()))
//				.contentType(MediaType.APPLICATION_JSON)
//				.body(apiErrorResponse);
//	}
}

