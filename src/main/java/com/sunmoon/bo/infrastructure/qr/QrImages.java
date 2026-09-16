package com.sunmoon.bo.infrastructure.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * QR 이미지를 요청마다 그립니다. 저장하지 않습니다 — 입력(주소)이 정해지면
 * 출력이 정해지는 순수 함수라 저장할 것이 없고, 이 계열에는 파일 저장소도
 * 없습니다.
 */
@Component
public class QrImages {

    /**
     * 오류 정정 H(30%). 테이블에 붙는 스티커는 긁히고, 물이 묻고, 손이
     * 반쯤 가립니다. 인쇄물은 다시 만들기 비싸니 여유를 크게 둡니다.
     */
    private static final ErrorCorrectionLevel CORRECTION = ErrorCorrectionLevel.H;

    /** 인쇄를 염두에 둔 크기. 화면에서는 줄여서 보면 됩니다. */
    private static final int SIZE_PX = 512;

    public byte[] pngOf(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX,
                    Map.of(EncodeHintType.ERROR_CORRECTION, CORRECTION,
                            EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("QR 이미지를 만들지 못했습니다: " + content, e);
        }
    }
}
