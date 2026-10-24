package io.github.heorhipuhachou.bmuz.cover;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InvalidCoverException extends ResponseStatusException {

    InvalidCoverException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
