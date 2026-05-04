package com.arianesline.plugincontainer;

import java.io.File;
import java.util.List;

import com.arianesline.cavelib.api.AggregationInterface;
import com.arianesline.cavelib.api.AggregationMemberInterface;
import com.arianesline.cavelib.api.StationIDLinkInterface;

public class AggregationImpl implements AggregationInterface {

    /**
     * Default constructor for AggregationImpl.
     */
    public AggregationImpl() {
        // Default constructor
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getUnit() {
        return "";
    }

    @Override
    public void setUnit(String unit) {

    }

    @Override
    public String getRootFolder() {
        return "";
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public List<? extends AggregationMemberInterface> getAggregationMembers() {
        return null;
    }

    @Override
    public List<String> getClosuresDescriptionList() {
        return null;
    }

    @Override
    public void buildNew(File file) {

    }

    @Override
    public void load(File file) {

    }

    @Override
    public void save() {

    }

    @Override
    public void addClosure(int idFirstInteraction, int idSecondInteraction) {

    }

    @Override
    public void removeClosure(int i) {

    }

    @Override
    public void resetErrors() {

    }

    @Override
    public void addLink(String member, int stationID, int index) {

    }

    @Override
    public StationIDLinkInterface getStationSourceIDLink(int stationID) {
        return null;
    }

    @Override
    public String getStationSourceProject(int stationID) {
        return "";
    }

    @Override
    public int getStationSourceID(int stationID) {
        return 0;
    }

    @Override
    public int getStationAGGIDFromSource(int stationID, String fileName) {
        return 0;
    }
}
