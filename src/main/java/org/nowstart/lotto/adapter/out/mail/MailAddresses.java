package org.nowstart.lotto.adapter.out.mail;

/** 로그에 남기는 메일 주소를 가린다. */
final class MailAddresses {

    private static final int VISIBLE_LOCAL_CHARS = 2;

    private MailAddresses() {
    }

    static String mask(String address) {
        if (address == null || address.isBlank()) {
            return "<none>";
        }
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = address.substring(0, at);
        String visible = local.substring(0, Math.min(VISIBLE_LOCAL_CHARS, local.length()));
        return visible + "***" + address.substring(at);
    }
}
