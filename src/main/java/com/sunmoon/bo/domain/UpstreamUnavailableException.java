package com.sunmoon.bo.domain;

/**
 * An upstream BO reads from — Order, the Device Server — did not answer.
 *
 * <p>This exists so those screens can say so out loud. The failure mode
 * worth avoiding is the one where a dead upstream renders as an empty
 * table: "어제 매출이 0원" and "매출을 물어볼 수 없었다" are different
 * facts, and an operator acting on the first when the second is true is
 * the whole problem.
 */
public class UpstreamUnavailableException extends RuntimeException {

    private final String upstream;

    public UpstreamUnavailableException(String upstream, Throwable cause) {
        super(upstream + "에 연결하지 못했습니다", cause);
        this.upstream = upstream;
    }

    public String upstream() {
        return upstream;
    }
}
