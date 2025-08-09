package com.example.unithon.global.error.exception;

import static org.springframework.http.HttpStatus.*;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum GlobalExceptionMessage implements ExceptionMessage {

	INTERNAL_SERVER_ERROR_MESSAGE(INTERNAL_SERVER_ERROR, "알 수 없는 서버 에러가 발생했습니다."),
	NO_RESOURCE_MESSAGE(NOT_FOUND, "존재하지 않는 경로입니다."),
	ARGUMENT_NOT_VALID_MESSAGE(BAD_REQUEST, "응답 데이터의 유효성 검증이 실패했습니다."),
	ARGUMENT_TYPE_MISMATCH_MESSAGE(BAD_REQUEST, "경로 변수 또는 쿼리 파라미터의 타입이 잘못되었습니다."),
	MISSING_PARAMETER_MESSAGE(BAD_REQUEST, "필수 쿼리 파라미터가 누락되었습니다."),
	DATA_NOT_READABLE_MESSAGE(BAD_REQUEST, "읽을 수 없는 응답 데이터입니다."),
	UNSUPPORTED_MEDIA_TYPE_MESSAGE(UNSUPPORTED_MEDIA_TYPE, "지원되지 않는 content-type 입니다."),
	UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "서버에서 본문을 처리할 수 없습니다."),
	
	// STT/TTS 관련 에러
	STT_SERVICE_ERROR(INTERNAL_SERVER_ERROR, "음성 인식 서비스에 오류가 발생했습니다."),
	TTS_SERVICE_ERROR(INTERNAL_SERVER_ERROR, "음성 합성 서비스에 오류가 발생했습니다."),
	AUDIO_FILE_INVALID(BAD_REQUEST, "올바르지 않은 오디오 파일입니다."),
	TEXT_EMPTY(BAD_REQUEST, "변환할 텍스트가 비어있습니다."),
	;

	private final HttpStatus httpStatus;
	private final String message;
}
