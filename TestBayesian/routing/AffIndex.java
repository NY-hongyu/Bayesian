package routing;

import java.io.Serializable;

/**
 * Created by NY on 2017/3/13.
 */
public class AffIndex implements Serializable {
    private static final long serialVersionUID = 1L;
    transient private String messageID;
    private String locID;
    private String timeID;
    private boolean transRes;

    AffIndex(String messageID,String locID,String timeID){
        this.messageID = messageID;
        this.locID = locID;
        this.timeID = timeID;
        this.transRes = false;
    }
    public boolean isLocEqual(String otherLocId){
        return locID.equals(otherLocId);
    }

    public boolean isTimeEqual(String otherTimeId){
        return timeID.equals(otherTimeId);
    }

    public boolean isTransResEqual(boolean otherRes){
        return transRes==otherRes;
    }

    public String getMessageID(){
        return messageID;
    }
    public String getLocID(){
        return this.locID;
    }
    public String getTimeID(){
        return this.timeID;
    }
    public boolean getTransRes(){
        return transRes;
    }
    public void setTransRes(boolean res){
        this.transRes = res;
    }
    public String toString(){
        String s1 = "[" + messageID +","+ locID +","+ timeID+"]";
        return s1;
    }
}