package com.unicconnect.security;

import com.unicconnect.rmi.client.RmiClientProperties;
import com.unicconnect.util.SecurityUtil;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.contract.CallerContextCodec;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Builds a signed CallerContext from the authenticated HTTP principal. */
@Component
public class CallerContextFactory {

    private final SecurityUtil securityUtil;
    private final RmiClientProperties props;

    public CallerContextFactory(SecurityUtil securityUtil, RmiClientProperties props) {
        this.securityUtil = securityUtil;
        this.props = props;
    }

    public CallerContext forCurrentUser() {
        UUID userId = securityUtil.currentUserId();
        long now = System.currentTimeMillis();
        UUID nonce = UUID.randomUUID();
        return new CallerContext(userId, now, nonce,
                CallerContextCodec.sign(props.secretBytes(), userId, now, nonce));
    }
}
