package com.hitek.civswitch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Object lock=new Object();
    private volatile boolean stop=false, paused=false, waiting=false, running=false, ready=false;
    private HidKeyboard keyboard;
    private TextView status,log,expectedText,sentText,nextText,progressText;
    private ProgressBar progress;
    private EditText keyMs, messageMs, batchSize;
    private Button init,run,confirm,pause,stopButton,restore;
    private final ArrayList<String> messages=new ArrayList<>();
    private int completed=0;
    private volatile boolean testing=false;
    private static final String TEST_TEXT="TEST123";
    private TextView preview(LinearLayout root,String label){
        TextView heading=new TextView(this);heading.setText(label);root.addView(heading);
        TextView body=new TextView(this);body.setTextIsSelectable(true);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);body.setTextSize(13);
        body.setPadding(10,8,10,18);root.addView(body);return body;
    }
    private void showProgress(int index,int count){
        ui.post(()->{
            if(index<0||index>=messages.size())return;
            String m=messages.get(index);int n=Math.min(count,m.length());
            expectedText.setText(m);sentText.setText(m.substring(0,n));
            nextText.setText(index+1<messages.size()?messages.get(index+1):"(Last message)");
            progress.setMax(m.length());progress.setProgress(n);
            progressText.setText("Message "+(index+1)+"/"+messages.size()+" · "+n+"/"+m.length()+" characters sent; "+(m.length()-n)+" remaining. Not verified on Switch.");
        });
    }
    private void line(String s){ui.post(()->{status.setText(s);log.append(s+"\n");if(log.length()>15000)log.setText(log.getText().subSequence(log.length()-12000,log.length()));});}
    private Button button(LinearLayout root,String title,Runnable action){Button b=new Button(this);b.setText(title);root.addView(b);b.setOnClickListener(v->action.run());return b;}
    private EditText number(LinearLayout root,String label,String def){TextView t=new TextView(this);t.setText(label);root.addView(t);EditText e=new EditText(this);e.setInputType(2);e.setSingleLine(true);e.setText(def);root.addView(e);return e;}
    private void worker(Runnable task){new Thread(task,"civ-worker").start();}
    @Override public void onCreate(Bundle state){super.onCreate(state);keyboard=new HidKeyboard(getFilesDir());
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,24,28,24);scroll.addView(root);setContentView(scroll);
        TextView title=new TextView(this);title.setText("CivSwitch · Pixel HID Controller");title.setTextSize(22);root.addView(title);
        TextView caution=new TextView(this);caution.setText("ROOT REQUIRED · Connect Pixel USB-C directly to Switch 2. USB configuration changes may interrupt ADB/MTP. No automatic game-side confirmation is possible.");root.addView(caution);
        keyMs=number(root,"Key press duration (ms)","80");messageMs=number(root,"Pause between messages (ms)","3000");batchSize=number(root,"Manual confirmation after N messages","5");
        init=button(root,"1 · Initialize USB keyboard",()->worker(()->{try{line("Setting up HID keyboard…");keyboard.setup();keyboard.open();ready=true;line("HID ready. Test connection in the Switch 2 text field before running.");}catch(Exception e){line("Setup error: "+e.getMessage()+" · Try Restore USB");}}));
        button(root,"TEST keyboard · type TEST123 (no Enter)",()->{
            if(!ready||running||testing){line("Initialize USB first; stop active transmission");return;}
            final int duration;
            try{duration=Integer.parseInt(keyMs.getText().toString());if(duration<10||duration>1000)throw new Exception();}
            catch(Exception e){line("Invalid key duration");return;}
            testing=true;
            worker(()->{try{
                line("Testing: typing TEST123 without Enter. Clear target field afterward.");
                for(char c:TEST_TEXT.toCharArray()){
                    int[] k=HidKeyboard.key(c);keyboard.report(k[0],k[1]);
                    try{Thread.sleep(duration);}finally{keyboard.release();}
                    Thread.sleep(Math.max(10,duration/2));
                }
                line("TEST123 sent. NOT confirmed by target. Clear field before payload.");
            }catch(Exception e){line("Test error: "+e.getMessage());}
            finally{try{keyboard.release();}catch(Exception ignored){}testing=false;}});
        });
        run=button(root,"2 · Start / restart from first message",()->{if(running||testing){line("Already running or testing");return;}if(!ready){line("Initialize USB keyboard first");return;}final int k,m,b;try{k=Integer.parseInt(keyMs.getText().toString());m=Integer.parseInt(messageMs.getText().toString());b=Integer.parseInt(batchSize.getText().toString());if(k<10||k>1000||m<0||m>60000||b<1||b>87)throw new Exception();}catch(Exception e){line("Invalid settings. Key 10–1000 ms; pause 0–60000 ms; batch 1–87.");return;}
            stop=false;paused=false;waiting=false;running=true;completed=0;
            worker(()->execute(k,m,b));});
        pause=button(root,"Pause / Resume",()->{synchronized(lock){paused=!paused;lock.notifyAll();}line(paused?"Paused after current key release":"Resumed");});
        confirm=button(root,"Confirm Switch 2 looks OK · Continue",()->{synchronized(lock){if(waiting){waiting=false;lock.notifyAll();line("Manual checkpoint acknowledged");}else line("No checkpoint awaiting confirmation");}});
        stopButton=button(root,"STOP · Release all keys",()->{stop=true;synchronized(lock){waiting=false;paused=false;lock.notifyAll();}worker(()->{try{keyboard.release();}catch(Exception ignored){}line("Stop requested");});});
        restore=button(root,"Restore original Android USB",()->{if(running){line("Stop the run and wait for it to finish before restoring USB");return;}worker(()->{try{keyboard.restore();ready=false;line("USB configuration restored");}catch(Exception e){line("Restore failed: "+e.getMessage());}});});
        status=new TextView(this);status.setText("Ready for initialization");status.setTextSize(16);root.addView(status);
        progressText=preview(root,"LIVE · USB progress");
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress);
        expectedText=preview(root,"EXPECTED · complete message");
        sentText=preview(root,"SENT · HID characters written (not Switch verified)");
        nextText=preview(root,"UP NEXT · next message");
        log=new TextView(this);log.setText("Event log\n");root.addView(log);
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open("payload.txt"),StandardCharsets.UTF_8))){String s;while((s=r.readLine())!=null)if(!s.isEmpty())messages.add(s);}
        catch(Exception e){line("Payload read failed: "+e.getMessage());}
        line("Loaded "+messages.size()+" payload messages. Manual checkpoints do not verify game acceptance.");
        if(!messages.isEmpty())showProgress(0,0);
    }
    private void gate() throws InterruptedException {synchronized(lock){while(!stop&&(paused||waiting))lock.wait(200);}if(stop)throw new InterruptedException("Stopped");}
    private void delay(int ms) throws InterruptedException {long end=android.os.SystemClock.uptimeMillis()+ms;while(android.os.SystemClock.uptimeMillis()<end){gate();Thread.sleep(Math.min(30,Math.max(1,end-android.os.SystemClock.uptimeMillis())));}}
    private void type(String message,int keyDuration,int messageIndex) throws Exception {
        for(int i=0;i<message.length();i++){
            gate();int[] key=HidKeyboard.key(message.charAt(i));keyboard.report(key[0],key[1]);
            try{delay(keyDuration);}finally{keyboard.release();}
            delay(Math.max(10,keyDuration/2));
            showProgress(messageIndex,i+1);
        }
        gate();keyboard.report(0,40);try{delay(keyDuration);}finally{keyboard.release();}
    }
    private void execute(int keyDuration,int messagePause,int batch){try{
        if(messages.size()!=87)throw new IOException("Expected 87 payload lines, got "+messages.size());
        // Fail fast on unsupported HID characters before sending any input.
        for(String s:messages)for(int i=0;i<s.length();i++)HidKeyboard.key(s.charAt(i));
        line("Starting 87-message run; keep phone awake and foregrounded");
        for(int i=0;i<messages.size();i++){
            gate();showProgress(i,0);line("Sending message "+(i+1)+" / "+messages.size());type(messages.get(i),keyDuration,i);
            completed=i+1;line("USB sent "+completed+"/87 (NOT game-confirmed)");
            if(completed<messages.size() && completed%batch==0){keyboard.release();synchronized(lock){waiting=true;}line("CHECKPOINT after "+completed+" messages. Inspect game, then tap Confirm.");gate();}
            if(completed<messages.size())delay(messagePause);
        }
        line("All 87 messages transmitted. This does not prove Civ VII accepted them.");
    }catch(InterruptedException e){line("Run stopped at "+completed+" completed messages");}
    catch(Exception e){line("Run error after "+completed+" messages: "+e.getMessage());}
    finally{try{keyboard.release();}catch(Exception ignored){}running=false;waiting=false;paused=false;}
    }
    @Override protected void onDestroy(){stop=true;synchronized(lock){waiting=false;paused=false;lock.notifyAll();}super.onDestroy();}
}
