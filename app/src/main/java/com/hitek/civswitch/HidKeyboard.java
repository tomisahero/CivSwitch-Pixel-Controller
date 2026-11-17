package com.hitek.civswitch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class HidKeyboard {
    private Process writer;
    private OutputStream out;
    private final File dir;
    HidKeyboard(File dir) { this.dir=dir; }
    private static final byte[] DESC = new byte[] {
        0x05,0x01,0x09,0x06,(byte)0xa1,0x01,0x05,0x07,0x19,(byte)0xe0,0x29,(byte)0xe7,
        0x15,0x00,0x25,0x01,0x75,0x01,(byte)0x95,0x08,(byte)0x81,0x02,
        (byte)0x95,0x01,0x75,0x08,(byte)0x81,0x01,(byte)0x95,0x05,0x75,0x01,
        0x05,0x08,0x19,0x01,0x29,0x05,(byte)0x91,0x02,(byte)0x95,0x01,
        0x75,0x03,(byte)0x91,0x01,(byte)0x95,0x06,0x75,0x08,0x15,0x00,
        0x25,0x65,0x05,0x07,0x19,0x00,0x29,0x65,(byte)0x81,0x00,(byte)0xc0
    };
    private static String q(String s){ return "'"+s.replace("'", "'\\''")+"'"; }
    static String root(String command) throws Exception {
        Process p = new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] x=new byte[4096]; int n;
        try(InputStream in=p.getInputStream()){while((n=in.read(x))!=-1) b.write(x,0,n);}
        int code=p.waitFor(); String result=b.toString("UTF-8");
        if(code!=0) throw new IOException("Root command failed ("+code+"): "+result);
        return result;
    }
    private File script(String name,String text) throws Exception {
        File f=new File(dir,name);try(FileOutputStream o=new FileOutputStream(f)){o.write(text.getBytes(StandardCharsets.UTF_8));}
        return f;
    }
    void setup() throws Exception {
        String desc=Base64.getEncoder().encodeToString(DESC);
        String setup="#!/system/bin/sh\nset -eu\nG=/config/usb_gadget/g1\n"+
          "[ -d \"$G\" ] || { echo 'No Android USB gadget'; exit 1; }\n"+
          "[ -d \"$G/configs/b.1\" ] || { echo 'Missing configs/b.1'; exit 1; }\n"+
          "[ -e /dev/hidg0 ] && { echo 'hidg0 already exists: refusing to overwrite'; exit 1; }\n"+
          "OLD=$(cat \"$G/UDC\")\n"+
          "[ -n \"$OLD\" ] || { echo 'USB controller not bound'; exit 1; }\n"+
          "echo \"$OLD\" > "+q(new File(dir,"udc-backup.txt").getAbsolutePath())+"\n"+
          "echo '' > \"$G/UDC\"\n"+
          "mkdir \"$G/functions/hid.civkbd\"\n"+
          "echo 1 > \"$G/functions/hid.civkbd/protocol\"\n"+
          "echo 1 > \"$G/functions/hid.civkbd/subclass\"\n"+
          "echo 8 > \"$G/functions/hid.civkbd/report_length\"\n"+
          "echo "+q(desc)+" | base64 -d > \"$G/functions/hid.civkbd/report_desc\"\n"+
          "ln -s \"$G/functions/hid.civkbd\" \"$G/configs/b.1/hid.civkbd\"\n"+
          "echo \"$OLD\" > \"$G/UDC\"\n"+
          "[ -e /dev/hidg0 ] || { echo 'hidg0 missing after bind'; exit 1; }\n";
        File f=script("setup-hid.sh",setup);
        root("sh "+q(f.getAbsolutePath()));
    }
    void open() throws Exception {
        writer=new ProcessBuilder("su","-c","cat > /dev/hidg0").start();out=writer.getOutputStream();
        Thread.sleep(100);if(!writer.isAlive()) throw new IOException("HID writer exited");
    }
    synchronized void report(int modifier,int key) throws Exception {
        if(out==null) throw new IOException("Keyboard not initialized");
        out.write(new byte[]{(byte)modifier,0,(byte)key,0,0,0,0,0});out.flush();
    }
    synchronized void release() throws Exception {report(0,0);}
    void close(){try{if(out!=null){release();out.close();}}catch(Exception ignored){}out=null;if(writer!=null)writer.destroy();writer=null;}
    void restore() throws Exception {
        close();
        String restore="#!/system/bin/sh\nset -eu\nG=/config/usb_gadget/g1\n"+
          "OLD=$(cat "+q(new File(dir,"udc-backup.txt").getAbsolutePath())+")\n"+
          "echo '' > \"$G/UDC\"\n"+
          "rm -f \"$G/configs/b.1/hid.civkbd\"\n"+
          "rmdir \"$G/functions/hid.civkbd\"\n"+
          "echo \"$OLD\" > \"$G/UDC\"\n";
        root("sh "+q(script("restore-hid.sh",restore).getAbsolutePath()));
    }
    // US keyboard layout. Unsupported characters fail before sending, never silently disappear.
    static int[] key(char c){
        if(c>='a'&&c<='z')return new int[]{0,4+c-'a'};
        if(c>='A'&&c<='Z')return new int[]{2,4+c-'A'};
        if(c>='1'&&c<='9')return new int[]{0,30+c-'1'};
        if(c=='0')return new int[]{0,39};
        String plain=" \n-=[]\\;',./`";int[] codes={44,40,45,46,47,48,49,51,52,54,55,56,53};
        int i=plain.indexOf(c);if(i>=0)return new int[]{0,codes[i]};
        String shifted="!@#$%^&*()_+{}|:\"<>?~";int[] shiftedCodes={30,31,32,33,34,35,36,37,38,39,45,46,47,48,49,51,52,54,55,56,53};
        i=shifted.indexOf(c);if(i>=0)return new int[]{2,shiftedCodes[i]};
        throw new IllegalArgumentException("Unsupported US keyboard character U+"+Integer.toHexString(c));
    }
}
