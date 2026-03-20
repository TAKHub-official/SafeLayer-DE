package com.takhub.safelayerde.domain.model;

public class WarningAreaRef {

    private String areaId;
    private String areaName;
    private String warnCellId;

    public WarningAreaRef() {
    }

    public WarningAreaRef(String areaId, String areaName, String warnCellId) {
        this.areaId = areaId;
        this.areaName = areaName;
        this.warnCellId = warnCellId;
    }

    public String getAreaId() {
        return areaId;
    }

    public void setAreaId(String areaId) {
        this.areaId = areaId;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getWarnCellId() {
        return warnCellId;
    }

    public void setWarnCellId(String warnCellId) {
        this.warnCellId = warnCellId;
    }
}
