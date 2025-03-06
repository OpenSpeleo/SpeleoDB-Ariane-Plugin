package com.arianesline.plugincontainer;

import com.arianesline.cavelib.api.CaveSurveyInterface;
import com.arianesline.cavelib.api.SurveyDataInterface;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.ArrayList;

public class CaveSurveyImpl implements CaveSurveyInterface {
    @Override
    public String getExtraData() {
        return "";
    }

    @Override
    public void setExtraData(String data) {

    }

    @Override
    public ArrayList<SurveyDataInterface> getSurveyDataInterface() {
        return null;
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String getGeoCoding() {
        return "";
    }

    @Override
    public String getCaveName() {
        return "";
    }

    @Override
    public void setCaveName(String name) {
        CaveSurveyInterface.super.setCaveName(name);
    }

    @Override
    public String getUnit() {
        return "";
    }

    @Override
    public void setUnit(String unit) {

    }

    @Override
    public Boolean getUseMagneticAzimuth() {
        return null;
    }

    @Override
    public void setUseMagneticAzimuth(Boolean mode) {
        CaveSurveyInterface.super.setUseMagneticAzimuth(mode);
    }

    @Override
    public int addStation(String sectionname, String explorer, LocalDate datetime, String nomstation, double direction, double length, double depthin, double depth, int fromid, int toid, String type, Color color, double longitude, double latitude, double up, double down, double left, double right, String comment, String profiletype, boolean islocked) {
        return 0;
    }
}
