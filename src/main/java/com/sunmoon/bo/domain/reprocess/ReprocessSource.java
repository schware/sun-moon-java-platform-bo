package com.sunmoon.bo.domain.reprocess;

import com.sunmoon.bo.domain.posops.PosOps;

/**
 * 재처리를 들고 있는 서비스 하나 — POS-SERVER(Device Server) · Order · APP-SERVER.
 *
 * <p>셋이 <b>같은 주소, 같은 모양</b>으로 답합니다(sun-moon-java-common 의 ReprocessViews). 그래서 BO 는 서비스마다
 * 다른 코드를 두지 않고 이 하나로 읽습니다. 실패를 들고 있는 것도, 다시 하는 법을 아는 것도 그 서비스입니다 —
 * BO 는 모아서 보여 주고, 누른 것을 그 서비스로 돌려보낼 뿐입니다.
 *
 * <p>닿지 못하면 {@link com.sunmoon.bo.domain.UpstreamUnavailableException} 을 던집니다.
 */
public interface ReprocessSource {

    /** 화면의 "서비스" 칸이자, "다시"를 어디로 보낼지 고르는 이름. */
    String service();

    PosOps.Reprocess list(String storeId);

    PosOps.RetryOutcome retryOne(String kind, String storeId, String id);

    PosOps.RetriedAll retryAll(String kind, String storeId);
}
