import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.joda.time.LocalDateTime;
import twitter4j.*;
import us.raudi.pushraven.FcmResponse;
import us.raudi.pushraven.Notification;
import us.raudi.pushraven.Pushraven;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by pbric on 26/09/2017.
 */
public class Main {
    public static void main(String args[]) throws TwitterException{

        //Pushraven set up for user notification
        String key = "AAAAmZOYkzg:APA91bGkiYtkDnRug-PeTWk_FpohfPqKHZ9NtN36eGIaLqAyO0BhfPp3MGdIC6WY0KDi5I7NeJXW0QTYoB4eSG5-lQrj6IaW72P2jTbrGf8PoyzPn898BYy0o0TowPNI_HUNmi373WmD";
        Pushraven.setKey(key);

        //Twitter4J StatusListener allows for streaming of tweets in real-time.
        StatusListener listener = new StatusListener(){
            Charset utf8 = StandardCharsets.UTF_8;
            String date =  new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
            String logName = "TweetLog_" + date;
            int count = 0;
            int countLast5 = 0;
            final int M = 2, B = 5; //Constants used for calculating 't'
            float lta = 0, sta = 0;
            float t;
            boolean triggered = false;
            String location = "Melbourne";
            //Variables to be initialised in setup methods
            DatabaseReference dataRef;
            Queue<Integer> ltaQueue;
            Queue<Integer> staQueue;
            ScheduledExecutorService executorService;
            Runnable r;

            public void onStatus(Status status) {

                if(dataRef == null){
                    setUpDatabase();
                }

                if(ltaQueue == null && staQueue == null){
                    setUpQueues();
                }

                if(executorService == null){
                    setUpExecutor();
                    try{
                        executorService.scheduleAtFixedRate(r, 0L, 5L, TimeUnit.SECONDS);
                    } catch (Exception ex){
                        ex.printStackTrace();
                    }
                }

                if(status.getText().contains("RT ") || status.getText().contains("http") || status.getText().contains("@")) {
                    //Do nothing
                }
                else if(status.getText().toLowerCase().contains("earthquake") || status.getText().toLowerCase().contains("gempa") ||
                        status.getText().toLowerCase().contains("temblor") || status.getText().toLowerCase().contains("terremoto") ||
                        status.getText().toLowerCase().contains("sismo")){
                    //Save tweet to file
                    try{
                        List<String> line = Arrays.asList(status.getCreatedAt() + "; " + status.getUser().getName() + "; " + status.getUser().getLocation() + " : " + status.getText());
                        Files.write(Paths.get(logName), line, utf8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException ex){
                        ex.printStackTrace();
                    }
                    if(status.getUser().getLocation() != null){
                        location = status.getUser().getLocation();
                    }

                    count++; //Total number of tweets relating to earthquakes this session
                    countLast5++; //Number of tweets relating to earthquakes in last 5 seconds
                }

            }
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {}
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {}

            /**
             * This function sets up the database connection
             * and handles the database authentication
             */
            private void setUpDatabase(){
                try {
                    FileInputStream serviceAccount = new FileInputStream("C:\\Users\\pbric\\IdeaProjects\\mavenTest\\earthquakedetector-90350-firebase-adminsdk-7qoce-8f93c0c4ab.json");

                    Map<String, Object> auth = new HashMap<String, Object>();
                    auth.put("uid", "ed-server");

                    FirebaseOptions options = new FirebaseOptions.Builder()
                            .setCredential(FirebaseCredentials.fromCertificate(serviceAccount))
                            .setDatabaseUrl("https://earthquakedetector-90350.firebaseio.com/")
                            .setDatabaseAuthVariableOverride(auth)
                            .build();

                    FirebaseApp.initializeApp(options);

                    dataRef = FirebaseDatabase
                            .getInstance()
                            .getReference("Earthquakes");
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            /**
             * Queues are used to store the values required for the STA and LTA calculations
             */
            private void setUpQueues(){
                ltaQueue = new LinkedList<>();
                staQueue = new LinkedList<>();
                while(staQueue.size() < 12){
                    staQueue.add(0);
                }
                while(ltaQueue.size() < 720){
                    ltaQueue.add(0);
                }
            }

            /**
             * This is where the detection logic is.
             * This executable runs once every 5 seconds,
             * and if an event is detected it updates the database
             * and sends an alert to the app.
             */
            private void setUpExecutor(){
                executorService = Executors.newScheduledThreadPool(1);
                r = () -> {
                    try{
                        //Update staQueue and get new STA
                        int tweetsPerMinute = countLast5*12;
                        countLast5 = 0;
                        staQueue.add(tweetsPerMinute);
                        int removedStaInt = staQueue.remove();
                        sta = ((sta*12) - removedStaInt + tweetsPerMinute)/12;

                        //Update ltaQueue and get new LTA
                        ltaQueue.add(removedStaInt);
                        int removedLtaInt=ltaQueue.remove();
                        lta = ((lta*720) - removedLtaInt + removedStaInt)/720;


                        /*******************************************
                         * NOTE: This is where the new value of t is calculated every 5 seconds
                         * For testing purposes, comment out this line and use the line under
                         * to guarantee a trigger on the first iteration
                         */
                        t = sta/((M*lta) + B); //Calculate new t. COMMENT OUT FOR TESTING DETECTION & ALERT
                        //t = 1.01f; //USE THIS FOR TESTING DETECTION & ALERT



                        //Record t, sta and lta values
                        List<String> record = Arrays.asList(LocalDateTime.now()+ "; t: " + t + "; STA: " + sta + "; LTA: " + lta + "; Total Tweets: " + count);
                        Files.write(Paths.get("t_Records_m" + M + "b" + B + "_" + date), record, utf8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        System.out.println(record);

                        if(t > 1.0 && !triggered){
                            //Record event locally
                            List<String> line = Arrays.asList("Earthquake in " + location + "! " + LocalDateTime.now());
                            Files.write(Paths.get("EarthquakesDetected"), line, utf8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                            //Update online database with new event
                            Event earthquake = new Event(location, LocalDateTime.now().toString());
                            dataRef.push().setValue(earthquake);

                            //Send notification to users
                            Notification raven = new Notification();
                            raven.title("Earthquake Alert!")
                                    .text("New earthquake in: " + earthquake.location)
                                    .sound("default")
                                    .to("/topics/Earthquakes")
                                    .time_to_live(100);

                            FcmResponse response = Pushraven.push(raven);
                            System.out.println(response);

                            triggered = true;
                        }else if(t < 0.25 && triggered){
                            triggered = false;
                        }
                    }catch (Exception ex){
                        ex.printStackTrace();
                    }
                };
            }

            @Override
            public void onScrubGeo(long l, long l1) {

            }

            @Override
            public void onStallWarning(StallWarning stallWarning) {
                System.out.println(stallWarning);
            }

            public void onException(Exception ex) {
                ex.printStackTrace();
            }
        };


        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.addListener(listener);
        // sample() method internally creates a thread which manipulates TwitterStream and calls these adequate listener methods continuously.
        twitterStream.sample();

    }
}
