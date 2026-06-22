package com.example.suicanfcreader.lib;

import android.util.SparseArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SuicaReader {

    public int termId;
    public int procId;
    public int year;
    public int month;
    public int day;
    public String kind;
    public int remain;
    public int seqNo;
    public int regionCode;
    public int inStation;
    public int inLine;
    public int outStation;
    public int outLine;
    public int busLine;
    public int busStop;

    public String device;
    public String action;

    public static final SparseArray<String> DEVICE_LIST = new SparseArray<>();
    public static final SparseArray<String> ACTION_LIST = new SparseArray<>();

    public SuicaReader() {}

    public static SuicaReader parse(byte[] res, int off) {
        SuicaReader self = new SuicaReader();
        self.init(res, off);
        return self;
    }

    private void init(byte[] res, int off) {
        this.termId = toUnsigned(res[off]);
        this.procId = toUnsigned(res[off + 1]);

        int dateBits = toInt(res, off, 4, 5);
        this.year = (dateBits >> 9) & 0x07f;
        this.month = (dateBits >> 5) & 0x00f;
        this.day = dateBits & 0x01f;

        if (isCharge(this.procId)) {
            this.kind = "チャージ";
        } else if (isShopping(this.procId)) {
            this.kind = "物販";
        } else if (isBus(this.procId)) {
            this.kind = "バス";
        } else {
            this.kind = toUnsigned(res[off + 6]) < 0x80 ? "JR" : "公営/私鉄";
        }

        this.inLine = toInt(res, off, 6);
        this.inStation = toInt(res, off, 7);
        this.outLine = toInt(res, off, 8);
        this.outStation = toInt(res, off, 9);
        this.busLine = toInt(res, off, 6, 7);
        this.busStop = toInt(res, off, 8, 9);
        this.remain = toInt(res, off, 11, 10);
        this.seqNo = toInt(res, off, 12, 13, 14);
        this.regionCode = toUnsigned(res[off + 15]);

        this.device = DEVICE_LIST.get(this.termId, "端末不明");
        this.action = ACTION_LIST.get(this.procId, this.kind);
    }

    private int toInt(byte[] res, int off, int... idx) {
        int num = 0;
        for (int j : idx) {
            num = num << 8;
            num += toUnsigned(res[off + j]);
        }
        return num;
    }

    private int toUnsigned(byte value) {
        return ((int) value) & 0x0ff;
    }

    public boolean isStationRecord() {
        if (isCharge(this.procId) || isShopping(this.procId) || isBus(this.procId)) return false;
        return (inLine != 0 || inStation != 0 || outLine != 0 || outStation != 0);
    }

    public boolean isChargeRecord() {
        return isCharge(this.procId);
    }

    public boolean isShoppingRecord() {
        return isShopping(this.procId);
    }

    public boolean isBusRecord() {
        return isBus(this.procId);
    }

    private boolean isCharge(int procId) {
        return procId == 2 || procId == 31 || procId == 72 || procId == 73;
    }

    private boolean isShopping(int procId) {
        return procId == 70 || procId == 74 || procId == 75 || procId == 198 || procId == 203;
    }

    private boolean isBus(int procId) {
        return procId == 13 || procId == 15 || procId == 31 || procId == 35;
    }

    public static byte[] readWithoutEncryption(byte[] idm, int size)
            throws IOException {
        return readWithoutEncryption(idm, 0, size);
    }

    public static byte[] readWithoutEncryption(byte[] idm, int startBlock, int size)
            throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(100);

        bout.write(0);
        bout.write(0x06);
        bout.write(idm);
        bout.write(1);
        bout.write(0x0f);
        bout.write(0x09);
        bout.write(size);
        for (int i = 0; i < size; i++) {
            bout.write(0x80);
            bout.write(startBlock + i);
        }

        byte[] msg = bout.toByteArray();
        msg[0] = (byte) msg.length;
        return msg;
    }

    static {
        DEVICE_LIST.put(3, "精算機");
        DEVICE_LIST.put(4, "携帯型端末");
        DEVICE_LIST.put(5, "車載端末");
        DEVICE_LIST.put(7, "券売機");
        DEVICE_LIST.put(8, "券売機");
        DEVICE_LIST.put(9, "入金機");
        DEVICE_LIST.put(18, "券売機");
        DEVICE_LIST.put(20, "券売機等");
        DEVICE_LIST.put(21, "券売機等");
        DEVICE_LIST.put(22, "改札機");
        DEVICE_LIST.put(23, "簡易改札機");
        DEVICE_LIST.put(24, "窓口端末");
        DEVICE_LIST.put(25, "窓口端末");
        DEVICE_LIST.put(26, "改札端末");
        DEVICE_LIST.put(27, "携帯電話");
        DEVICE_LIST.put(28, "乗継精算機");
        DEVICE_LIST.put(29, "連絡改札機");
        DEVICE_LIST.put(31, "簡易入金機");
        DEVICE_LIST.put(70, "VIEW ALTTE");
        DEVICE_LIST.put(72, "VIEW ALTTE");
        DEVICE_LIST.put(199, "物販端末");
        DEVICE_LIST.put(200, "自販機");

        ACTION_LIST.put(1, "運賃支払");
        ACTION_LIST.put(2, "チャージ");
        ACTION_LIST.put(3, "券購入");
        ACTION_LIST.put(4, "精算");
        ACTION_LIST.put(5, "入場精算");
        ACTION_LIST.put(6, "窓口処理");
        ACTION_LIST.put(7, "新規発行");
        ACTION_LIST.put(8, "控除");
        ACTION_LIST.put(13, "バス");
        ACTION_LIST.put(15, "バス");
        ACTION_LIST.put(17, "再発行");
        ACTION_LIST.put(19, "支払");
        ACTION_LIST.put(20, "入場オートチャージ");
        ACTION_LIST.put(21, "出場オートチャージ");
        ACTION_LIST.put(31, "バスチャージ");
        ACTION_LIST.put(35, "バス券購入");
        ACTION_LIST.put(70, "物販");
        ACTION_LIST.put(72, "特典チャージ");
        ACTION_LIST.put(73, "レジ入金");
        ACTION_LIST.put(74, "物販取消");
        ACTION_LIST.put(75, "入場物販");
        ACTION_LIST.put(132, "他社精算");
        ACTION_LIST.put(133, "他社入場精算");
        ACTION_LIST.put(198, "現金併用物販");
        ACTION_LIST.put(203, "入場現金併用物販");
    }
}
