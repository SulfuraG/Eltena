package com.eltena.core.application.ui;

public enum MenuViewType {
    MAIN("メインメニュー"),
    STATUS("ステータス"),
    JOBS("ジョブ"),
    TITLES("称号");

    private final String title;

    MenuViewType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
