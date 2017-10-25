/**
 * Created by pbric on 26/09/2017.
 */
public class Event {
    public String location;
    public String dateTime;

    public Event(String location, String dateTime){
        this.location = location;
        this.dateTime = dateTime;
    }

    //Empty constructor for Firebase
    public Event(){

    }

    public void setLocation(String location){
        this.location = location;
    }
    public void setDateTime(String dateTime){
        this.dateTime = dateTime;
    }

    public String getLocation(){
        return location;
    }
    public String getDateTime(){
        return dateTime;
    }
}

