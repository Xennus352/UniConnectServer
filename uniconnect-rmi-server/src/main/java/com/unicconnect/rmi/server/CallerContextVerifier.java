package com.unicconnect.rmi.server;

import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.contract.CallerContextCodec;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies signature + freshness and rejects nonce replay. Nonces live for
 * twice the context max-age, then the sweeper thread drops them.
 */
@Component
public class CallerContextVerifier {

    private final RmiServerProperties props;
    private final Map<UUID, Long> seenNonces = new ConcurrentHashMap<>();

    public CallerContextVerifier(RmiServerProperties props) {
        this.props = props;
        Thread sweeper = new Thread(() -> {
            while (true) {
                try { Thread.sleep(60_000); } catch (InterruptedException e) { return; }
                long cutoff = System.currentTimeMillis()
                        - 2 * CallerContextCodec.MAX_AGE_MILLIS;
                Iterator<Map.Entry<UUID, Long>> it = seenNonces.entrySet().iterator();
                while (it.hasNext()) {
                    if (it.next().getValue() < cutoff) it.remove();
                }
            }
        }, "rmi-nonce-sweeper");
        sweeper.setDaemon(true);
        sweeper.start();
    }

    /** @return the verified userId */
    public UUID verify(CallerContext ctx) {
        CallerContextCodec.verify(props.secretBytes(), ctx);
        if (seenNonces.putIfAbsent(ctx.nonce(), System.currentTimeMillis()) != null) {
            throw new com.unicconnect.rmi.contract.CallerContextRejectedException(
                    "Replayed caller context");
        }
        return ctx.userId();
    }
}
